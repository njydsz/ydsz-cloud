package com.njydsz.agent.infra.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import com.njydsz.common.thread.util.ExecutorUtils;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 摘要压缩对话记忆
 *
 * <p>包装 {@link ConversationMemory}，当消息数超过 {@link #summaryThreshold} 时， 自动将较早的消息通过 LLM 压缩为摘要，只保留最近
 * {@link #keepRecentCount} 条原始消息。
 *
 * <h3>对标竞品</h3>
 *
 * <ul>
 *   <li>LangChain ConversationSummaryBufferMemory
 *   <li>OpenAI Assistants Thread（自动截断 + 摘要）
 * </ul>
 *
 * <h3>执行流程</h3>
 *
 * <pre>
 * save() → 检查消息数 → 超过阈值?
 *   YES → 异步压缩：load 全部 → 取较早消息 → LLM 摘要 → 摘要先持久化 → 清除旧消息 → 保存摘要+最近消息
 *   NO  → 直接委托给 delegate
 * </pre>
 *
 * <p><b>可靠性（P0 修复）</b>：摘要优先持久化到 Redis（{@code ydsz:agent:summary:{convId}}）， 再执行
 * {@code clear + save}，即使压缩中途失败或进程重启，摘要仍可恢复上下文，不再丢失历史。
 *
 * <p><b>异步压缩（P1 优化）</b>：摘要 LLM 调用在虚拟线程中异步执行，不阻塞用户消息保存路径； 同一会话同时仅允许一个压缩任务在途，避免重复压缩。
 *
 * <p><b>线程安全</b>：delegate 记忆并发安全；摘要读取优先 Redis（原子操作），内存 Map 仅作兜底缓存。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SummaryConversationMemory implements ConversationMemory {

  /** 摘要前缀 */
  private static final String SUMMARY_PREFIX = "[对话摘要] ";

  /** 摘要 Redis key 前缀（完整 key = 前缀 + conversationId） */
  private static final String SUMMARY_KEY_PREFIX = "ydsz:agent:summary:";

  /** 摘要 Redis TTL（秒），与对话记忆 TTL 对齐（24 小时） */
  private static final long SUMMARY_TTL_SECONDS = 24 * 3600L;

  /** 委托的记忆实现 */
  private final ConversationMemory delegate;

  /** LLM 客户端（用于生成摘要） */
  private final LlmClient llmClient;

  /** 模型名称 */
  private final String model;

  /** 触发摘要的消息阈值 */
  private final int summaryThreshold;

  /** 保留最近消息数 */
  private final int keepRecentCount;

  /** Token 预算（估算值），超过时触发压缩 */
  private final int tokenBudget;

  /** Token 估算的字符系数（Char/Token） */
  private final double tokenCharRatio;

  /** Redis 摘要存储（可为 null，null 时降级为纯内存缓存） */
  private final RedisStringOps redisOps;

  /** 正在压缩中的会话集合（防止同一会话并发重复压缩） */
  private final ConcurrentMap<String, Boolean> compressing = new ConcurrentHashMap<>();

  /** 摘要内存兜底缓存（Redis 不可用时的降级路径） */
  private final ConcurrentMap<String, String> conversationSummaries = new ConcurrentHashMap<>();

  /** 异步压缩虚拟线程池（JDK 21 虚拟线程，规范豁免场景） */
  private final ExecutorService summaryExecutor =
      ExecutorUtils.newVirtualThreadExecutor("agent-memory-summary-");

  public SummaryConversationMemory(
      ConversationMemory delegate,
      LlmClient llmClient,
      String model,
      int summaryThreshold,
      int keepRecentCount) {
    this(delegate, llmClient, model, summaryThreshold, keepRecentCount, null);
  }

  /**
   * 构造摘要压缩对话记忆。
   *
   * @param delegate 委托的记忆实现，不允许为 {@code null}
   * @param llmClient LLM 客户端，用于生成摘要
   * @param model 摘要模型名称
   * @param summaryThreshold 触发压缩的消息条数阈值
   * @param keepRecentCount 压缩后保留的最近原始消息条数
   * @param redisOps Redis String 操作组件（用于摘要持久化；传 {@code null} 时降级为内存缓存）
   */
  public SummaryConversationMemory(
      ConversationMemory delegate,
      LlmClient llmClient,
      String model,
      int summaryThreshold,
      int keepRecentCount,
      RedisStringOps redisOps) {
    this(delegate, llmClient, model, summaryThreshold, keepRecentCount, redisOps, 4000, 2.5);
  }

  /**
   * 构造摘要压缩对话记忆（全参）。
   *
   * @param delegate 委托的记忆实现，不允许为 {@code null}
   * @param llmClient LLM 客户端，用于生成摘要
   * @param model 摘要模型名称
   * @param summaryThreshold 触发压缩的消息条数阈值
   * @param keepRecentCount 压缩后保留的最近原始消息条数
   * @param redisOps Redis String 操作组件（用于摘要持久化；传 {@code null} 时降级为内存缓存）
   * @param tokenBudget 触发压缩的 Token 预算
   * @param tokenCharRatio Token 估算的字符系数
   */
  public SummaryConversationMemory(
      ConversationMemory delegate,
      LlmClient llmClient,
      String model,
      int summaryThreshold,
      int keepRecentCount,
      RedisStringOps redisOps,
      int tokenBudget,
      double tokenCharRatio) {
    this.delegate = delegate;
    this.llmClient = llmClient;
    this.model = model;
    this.summaryThreshold = summaryThreshold > 0 ? summaryThreshold : 20;
    this.keepRecentCount = keepRecentCount > 0 ? keepRecentCount : 10;
    this.redisOps = redisOps;
    this.tokenBudget = tokenBudget > 0 ? tokenBudget : 4000;
    this.tokenCharRatio = tokenCharRatio > 0 ? tokenCharRatio : 2.5;
  }

  @Override
  public void save(String conversationId, ChatMessage message) {
    delegate.save(conversationId, message);

    long count = delegate.count(conversationId);
    int totalTokens = estimateConversationTokens(conversationId);
    // 消息数超阈值 或 估算 Token 超预算，均触发压缩（异步执行，不阻塞保存路径）
    if (count > summaryThreshold || totalTokens > tokenBudget) {
      triggerCompress(conversationId);
    }
  }

  @Override
  public List<ChatMessage> load(String conversationId, int maxMessages) {
    List<ChatMessage> messages = new ArrayList<>();

    String summary = loadSummary(conversationId);
    if (summary != null && !summary.isBlank()) {
      messages.add(ChatMessage.system(SUMMARY_PREFIX + summary));
    }

    messages.addAll(delegate.load(conversationId, maxMessages));
    return messages;
  }

  @Override
  public void clear(String conversationId) {
    delegate.clear(conversationId);
    conversationSummaries.remove(conversationId);
    deleteSummary(conversationId);
  }

  @Override
  public long count(String conversationId) {
    return delegate.count(conversationId);
  }

  /**
   * 按 Token 预算加载对话历史（Token 感知截断 + 摘要合并）。
   *
   * <p>先加载摘要（如有），再按 Token 预算从委托记忆中选择最新消息，保证总 Token 不超预算。
   */
  @Override
  public List<ChatMessage> loadWithTokenBudget(
      String conversationId, int tokenBudget, double tokenCharRatio) {
    List<ChatMessage> result = new ArrayList<>();
    // 摘要优先（占用部分预算）
    String summary = loadSummary(conversationId);
    if (summary != null && !summary.isBlank()) {
      ChatMessage summaryMsg = ChatMessage.system(SUMMARY_PREFIX + summary);
      result.add(summaryMsg);
      tokenBudget -= ConversationMemory.estimateTokens(summaryMsg, tokenCharRatio);
    }
    // 按剩余预算加载最近消息
    if (tokenBudget > 0) {
      List<ChatMessage> recentMessages = delegate.load(conversationId, Integer.MAX_VALUE);
      int currentTokens = 0;
      int includeUpTo = recentMessages.size();
      for (int i = recentMessages.size() - 1; i >= 0; i--) {
        int msgTokens = ConversationMemory.estimateTokens(recentMessages.get(i), tokenCharRatio);
        if (currentTokens + msgTokens > tokenBudget) {
          break;
        }
        currentTokens += msgTokens;
        includeUpTo = i;
      }
      result.addAll(recentMessages.subList(includeUpTo, recentMessages.size()));
    }
    return result;
  }

  /**
   * 估算对话历史总 Token 数（含摘要）。
   *
   * @param conversationId 对话 ID
   * @return 估算 Token 总数
   */
  private int estimateConversationTokens(String conversationId) {
    int total = 0;
    String summary = loadSummary(conversationId);
    if (summary != null) {
      total += (int) Math.ceil(summary.length() / tokenCharRatio);
    }
    List<ChatMessage> messages = delegate.load(conversationId, Integer.MAX_VALUE);
    for (ChatMessage msg : messages) {
      total += ConversationMemory.estimateTokens(msg, tokenCharRatio);
    }
    return total;
  }

  /**
   * 触发异步压缩（同一会话仅允许一个压缩任务在途）。
   *
   * @param conversationId 对话 ID
   */
  private void triggerCompress(String conversationId) {
    if (compressing.putIfAbsent(conversationId, Boolean.TRUE) != null) {
      // 该会话已有压缩任务在途，跳过本次触发避免重复压缩
      return;
    }
    summaryExecutor.execute(
        () -> {
          try {
            tryCompress(conversationId);
          } finally {
            compressing.remove(conversationId);
          }
        });
  }

  /** 压缩对话历史：将较早的消息摘要后替换 */
  private void tryCompress(String conversationId) {
    try {
      List<ChatMessage> allMessages = delegate.load(conversationId, Integer.MAX_VALUE);
      if (allMessages.size() <= summaryThreshold) {
        return;
      }

      int toSummarize = allMessages.size() - keepRecentCount;
      List<ChatMessage> oldMessages = allMessages.subList(0, toSummarize);
      List<ChatMessage> recentMessages = allMessages.subList(toSummarize, allMessages.size());

      String existingSummary = loadSummary(conversationId);
      String summary = summarizeMessages(conversationId, oldMessages, existingSummary);

      if (summary != null && !summary.isBlank()) {
        // 摘要先持久化（Redis + 内存），再替换消息；即使后续失败，摘要仍可恢复上下文
        saveSummary(conversationId, summary);
        delegate.clear(conversationId);
        for (ChatMessage msg : recentMessages) {
          delegate.save(conversationId, msg);
        }
        log.info(
            "[Memory-Summary] 对话压缩完成: convId={}, compressed={}, kept={}",
            conversationId,
            oldMessages.size(),
            recentMessages.size());
      }
    } catch (Exception e) {
      log.warn("[Memory-Summary] 对话压缩失败: convId={}, error={}", conversationId, e.getMessage());
    }
  }

  /** 调用 LLM 生成摘要 */
  private String summarizeMessages(
      String conversationId, List<ChatMessage> messages, String existingSummary) {
    StringBuilder sb = new StringBuilder();
    sb.append("请将以下对话历史压缩为简洁的摘要（不超过 500 字），保留关键信息、决策和上下文。\n");
    if (existingSummary != null && !existingSummary.isBlank()) {
      sb.append("\n已有摘要：\n").append(existingSummary).append("\n");
    }
    sb.append("\n需要追加压缩的对话：\n");
    for (ChatMessage msg : messages) {
      sb.append(msg.getRole()).append(": ").append(truncate(msg.getContent(), 500)).append("\n");
    }

    ChatRequest request =
        ChatRequest.builder()
            .model(model)
            .messages(
                List.of(
                    ChatMessage.system("你是对话摘要助手，负责将对话历史压缩为简洁摘要。"),
                    ChatMessage.user(sb.toString(), conversationId)))
            .temperature(0.3)
            .maxTokens(800)
            .build();

    ChatResponse response = llmClient.chat(request);
    return response.getContent();
  }

  /**
   * 读取摘要：优先从 Redis 读取（跨进程/重启持久），未命中回退内存兜底缓存。
   *
   * @param conversationId 对话 ID
   * @return 摘要内容；不存在时返回 {@code null}
   */
  private String loadSummary(String conversationId) {
    if (redisOps != null) {
      try {
        Object value = redisOps.get(buildSummaryKey(conversationId));
        if (value != null) {
          return String.valueOf(value);
        }
      } catch (Exception e) {
        log.warn("[Memory-Summary] 摘要读取失败，回退内存缓存: {}", e.getMessage());
      }
    }
    return conversationSummaries.get(conversationId);
  }

  /**
   * 持久化摘要：写入 Redis（带 TTL）并更新内存兜底缓存。
   *
   * @param conversationId 对话 ID
   * @param summary 摘要内容
   */
  private void saveSummary(String conversationId, String summary) {
    conversationSummaries.put(conversationId, summary);
    if (redisOps != null) {
      try {
        redisOps.set(buildSummaryKey(conversationId), summary, SUMMARY_TTL_SECONDS);
      } catch (Exception e) {
        log.warn("[Memory-Summary] 摘要持久化失败，仅保留内存缓存: {}", e.getMessage());
      }
    }
  }

  /**
   * 删除持久化摘要。
   *
   * @param conversationId 对话 ID
   */
  private void deleteSummary(String conversationId) {
    if (redisOps != null) {
      try {
        redisOps.del(buildSummaryKey(conversationId));
      } catch (Exception e) {
        log.warn("[Memory-Summary] 摘要删除失败: {}", e.getMessage());
      }
    }
  }

  /**
   * 构建摘要 Redis key。
   *
   * @param conversationId 对话 ID
   * @return 完整 Redis key
   */
  private String buildSummaryKey(String conversationId) {
    return SUMMARY_KEY_PREFIX + conversationId;
  }

  private String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
