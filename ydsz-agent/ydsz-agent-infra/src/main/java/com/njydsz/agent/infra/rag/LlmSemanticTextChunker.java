package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.TextChunker;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.IdGenerator;

/**
 * LLM 语义感知分块器
 *
 * <p>将长文本按固定字符数预分段后，调用 LLM 分析语义边界，确定最佳分块断点。 适用于对语义一致性要求极高的场景（如法律文档、技术手册、医学文献），分块结果在语义内聚性上显著优于固定分块。
 *
 * <h3>工作原理</h3>
 *
 * <ol>
 *   <li>按 {@code maxChunkChars} 将文本预切为逻辑段落</li>
 *   <li>将段落文本送给 LLM，由其标注语义断裂点的段落序号</li>
 *   <li>根据返回的断裂点将段落合并为最终 chunk</li>
 * </ol>
 *
 * <p><b>线程安全</b>：无状态，{@link LlmClient} 调用为同步等待，可并发使用。
 *
 * <p><b>成本注意</b>：每次分块均触发一次 LLM 调用，长文档分块成本较高。建议仅在 RAG 索引关键文档时使用，或结合 {@code simple} 策略作为降级。
 *
 * @author ydzs-team
 * @since 26.09.17
 */
@Slf4j
public class LlmSemanticTextChunker implements TextChunker {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 每次预分段的最大字符数 */
  private static final int DEFAULT_MAX_CHUNK_CHARS = 1500;

  /** LLM 分析时每次送入的段落数上限（受 token 预算限制） */
  private static final int SECTIONS_PER_PROMPT = 10;

  /** 每 Token 对应的字符数估算（中文） */
  private static final double CHARS_PER_TOKEN = 2.0;

  private final LlmClient llmClient;
  private final String model;
  private final int maxChunkChars;

  /**
   * 构造 LLM 语义分块器。
   *
   * @param llmClient LLM 客户端
   * @param model 模型名称
   * @param maxChunkChars 单次预分段的最大字符数
   */
  public LlmSemanticTextChunker(LlmClient llmClient, String model, int maxChunkChars) {
    this.llmClient = llmClient;
    this.model = model;
    this.maxChunkChars = maxChunkChars > 0 ? maxChunkChars : DEFAULT_MAX_CHUNK_CHARS;
  }

  @Override
  public List<TextChunk> chunk(String text, String documentId) {
    return chunk(text, documentId, null, null);
  }

  @Override
  public List<TextChunk> chunk(String text, String documentId, String documentTitle, String source) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    // 短文本不分块
    if (text.length() <= maxChunkChars) {
      return List.of(
          new TextChunk(
              IdGenerator.nextIdStr(),
              text,
              documentId,
              documentTitle,
              source,
              0,
              estimateTokens(text),
              Map.of("chunkType", "llm-semantic"),
              null));
    }
    try {
      return doSemanticChunk(text, documentId, documentTitle, source);
    } catch (LlmException e) {
      log.warn("[LLM-Chunker] LLM 分块失败，降级为固定分块: docId={}, err={}", documentId, e.getMessage());
      return fallbackFixedChunk(text, documentId, documentTitle, source);
    }
  }

  private List<TextChunk> doSemanticChunk(
      String text, String documentId, String documentTitle, String source) {
    // 1. 预分段
    List<String> sections = preSplit(text);
    List<TextChunk> chunks = new ArrayList<>(COLLECTION_CAPACITY);

    // 2. 分批送 LLM 确定断裂点
    int chunkIndex = 0;
    List<String> currentChunkSections = new ArrayList<>(SECTIONS_PER_PROMPT);
    for (int i = 0; i < sections.size(); i++) {
      currentChunkSections.add(sections.get(i));
      // 当累积段落足够多或到达末尾时，决定是否切分
      if (currentChunkSections.size() >= SECTIONS_PER_PROMPT || i == sections.size() - 1) {
        if (i < sections.size() - 1) {
          // 分析当前批次的语义断裂点
          int breakAt = analyzeBreakPoint(currentChunkSections);
          if (breakAt > 0 && breakAt < currentChunkSections.size()) {
            // 断裂点之前的部分构成一个 chunk
            String chunkContent = String.join("\n\n", currentChunkSections.subList(0, breakAt));
            chunks.add(createChunk(chunkContent, documentId, documentTitle, source, chunkIndex++));
            // 断裂点之后的部分继续累积
            currentChunkSections = new ArrayList<>(currentChunkSections.subList(breakAt, currentChunkSections.size()));
          }
        } else {
          // 末尾：所有剩余段落构成最后一个 chunk
          String chunkContent = String.join("\n\n", currentChunkSections);
          chunks.add(createChunk(chunkContent, documentId, documentTitle, source, chunkIndex++));
        }
      }
    }
    log.debug("[LLM-Chunker] 语义分块完成: docId={}, sections={}, chunks={}", documentId, sections.size(), chunks.size());
    return chunks;
  }

  /**
   * 按固定字符数预分段（按段落边界对齐）。
   *
   * @param text 完整文本
   * @return 段落列表
   */
  private List<String> preSplit(String text) {
    List<String> sections = new ArrayList<>(COLLECTION_CAPACITY);
    String[] paragraphs = text.split("\\n\\n+");
    StringBuilder buffer = new StringBuilder();
    for (String para : paragraphs) {
      if (para.isBlank()) {
        continue;
      }
      if (buffer.length() + para.length() > maxChunkChars && buffer.length() > 0) {
        sections.add(buffer.toString().trim());
        buffer = new StringBuilder();
      }
      buffer.append(para);
      if (!para.endsWith("\n")) {
        buffer.append("\n\n");
      }
    }
    if (buffer.length() > 0) {
      sections.add(buffer.toString().trim());
    }
    return sections;
  }

  /**
   * 调用 LLM 分析断裂点位置。
   *
   * @param sections 段落列表
   * @return 断裂点段落索引（0 表示不切分）
   */
  private int analyzeBreakPoint(List<String> sections) {
    if (sections.size() <= 2) {
      return 0; // 段落过少不切分
    }
    StringBuilder prompt = new StringBuilder();
    prompt.append("以下是一篇文档的若干段落，请分析段落之间的语义断裂点。\n");
    prompt.append("返回 JSON 格式：{\"breakAt\": N}，其中 N 为第一个新 chunk 应从第几个段落开始计数（从 0 开始）。\n");
    prompt.append("如果所有段落语义连贯，无需切分，则返回 {\"breakAt\": 0}。\n");
    prompt.append("只返回 JSON，不要其他文字。\n\n");
    for (int i = 0; i < sections.size(); i++) {
      prompt.append(String.format("--- 段落 %d ---\n%s\n\n", i, sections.get(i)));
    }
    try {
      ChatRequest request = ChatRequest.builder()
          .model(model)
          .messages(List.of(ChatMessage.user(prompt.toString(), "chunker-analysis")))
          .temperature(0.1)
          .maxTokens(100)
          .build();
      String response = llmClient.chat(request).getMessage().getContent();
      if (response == null) {
        return 0;
      }
      // 尝试从响应中提取 JSON
      String jsonStr = extractJson(response);
      if (jsonStr == null) {
        return 0;
      }
      Map<String, Object> result = YdszJson.parseMap(jsonStr);
      if (result == null) {
        return 0;
      }
      Object breakAtObj = result.get("breakAt");
      if (breakAtObj instanceof Number breakAtNum) {
        return breakAtNum.intValue();
      }
    } catch (Exception e) {
      log.debug("[LLM-Chunker] LLM 分析断裂点失败: {}", e.getMessage());
    }
    return 0;
  }

  /**
   * 从 LLM 响应中提取 JSON 块。
   *
   * @param response LLM 原始响应
   * @return JSON 字符串；未找到返回 null
   */
  private String extractJson(String response) {
    int start = response.indexOf('{');
    int end = response.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return response.substring(start, end + 1);
    }
    return null;
  }

  /**
   * LLM 分块失败时的固定分块降级。
   *
   * @param text 原始文本
   * @param documentId 文档 ID
   * @param documentTitle 文档标题
   * @param source 来源
   * @return 固定分块结果
   */
  private List<TextChunk> fallbackFixedChunk(
      String text, String documentId, String documentTitle, String source) {
    List<TextChunk> chunks = new ArrayList<>(COLLECTION_CAPACITY);
    for (int i = 0; i < text.length(); i += maxChunkChars) {
      int end = Math.min(i + maxChunkChars, text.length());
      String content = text.substring(i, end);
      chunks.add(createChunk(content, documentId, documentTitle, source, chunks.size()));
    }
    return chunks;
  }

  private TextChunk createChunk(
      String content, String documentId, String documentTitle, String source, int chunkIndex) {
    return new TextChunk(
        IdGenerator.nextIdStr(),
        content,
        documentId,
        documentTitle,
        source,
        chunkIndex,
        estimateTokens(content),
        Map.of("chunkType", "llm-semantic"),
        null);
  }

  private int estimateTokens(String text) {
    return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
  }
}
