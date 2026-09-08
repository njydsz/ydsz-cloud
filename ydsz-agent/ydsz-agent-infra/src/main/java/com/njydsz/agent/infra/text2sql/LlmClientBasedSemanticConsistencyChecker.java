package com.njydsz.agent.infra.text2sql;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.text2sql.SemanticConsistencyChecker;

/**
 * 基于 LLM 的语义一致性校验实现。
 *
 * <p>将用户问题与 LLM 生成的 SQL 一并发给 LLM，要求模型判断两者是否匹配，
 * 返回 0.0-1.0 的一致性分数及推理说明。
 *
 * <p>降级策略：LLM 调用失败时返回 1.0（默认通过），避免因校验环节阻塞查询链路。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class LlmClientBasedSemanticConsistencyChecker implements SemanticConsistencyChecker {

  /** 校验请求最大输出 Token 数 */
  private static final int CHECK_MAX_TOKENS = 256;

  private final LlmClient llmClient;
  private final String defaultModel;

  /**
   * 构造基于 LLM 的语义一致性校验器。
   *
   * @param llmClient LLM 客户端
   * @param defaultModel 模型名称
   */
  public LlmClientBasedSemanticConsistencyChecker(
      LlmClient llmClient,
      @Value("${ydsz.agent.llm.default-model:gpt-4o-mini}") String defaultModel) {
    this.llmClient = llmClient;
    this.defaultModel = defaultModel;
  }

  /**
   * 使用 LLM 判断生成的 SQL 是否与用户意图一致。
   *
   * <p>LLM 调用失败时返回 1.0（默认通过），避免校验环节阻塞查询链路。
   *
   * @param userQuery 用户原始问题
   * @param generatedSql LLM 生成的 SQL
   * @return 一致性校验结果（score 0.0-1.0 + 推理说明）
   */
  @Override
  public ConsistencyCheckResult check(String userQuery, String generatedSql) {
    String prompt =
        """
        你是 SQL 语义校验助手。请判断以下 SQL 查询是否准确反映了用户的意图。

        用户问题：%s
        SQL 查询：%s

        请按以下 JSON 格式回答（不要包含其他内容）：
        {"score": <0.0到1.0之间的小数，1.0表示完全匹配，0.0表示完全不匹配>, "reasoning": "<简要说明判断理由>"}
        """
            .formatted(userQuery, generatedSql);

    ChatRequest request =
        ChatRequest.builder()
            .model(defaultModel)
            .messages(List.of(ChatMessage.user(prompt, null)))
            .temperature(0)
            .maxTokens(CHECK_MAX_TOKENS)
            .build();

    try {
      ChatResponse response = llmClient.chat(request);
      String content = response.getContent();
      if (content == null || content.isBlank()) {
        log.warn("[SemanticConsistency] LLM 返回空响应，默认一致性为 1.0");
        return new ConsistencyCheckResult(1.0, "LLM 返回空响应，默认通过");
      }
      // 解析 JSON 响应
      return parseCheckResult(content, userQuery, generatedSql);
    } catch (Exception e) {
      log.warn("[SemanticConsistency] LLM 调用失败，默认一致性为 1.0: {}", e.getMessage());
      return new ConsistencyCheckResult(1.0, "LLM 调用失败，默认通过: " + e.getMessage());
    }
  }

  /**
   * 解析 LLM 返回的 JSON 一致性结果。
   *
   * @param content LLM 响应内容
   * @param userQuery 用户问题（用于日志）
   * @param generatedSql SQL（用于日志）
   * @return 解析后的结果
   */
  private ConsistencyCheckResult parseCheckResult(
      String content, String userQuery, String generatedSql) {
    try {
      // 简单 JSON 解析（避免引入额外依赖）
      String trimmed = content.trim();
      // 移除可能的 markdown 代码块包裹
      if (trimmed.startsWith("```")) {
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline > 0 && lastFence > firstNewline) {
          trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
        }
      }
      // 提取 score
      int scoreStart = trimmed.indexOf("\"score\"");
      if (scoreStart < 0) {
        return new ConsistencyCheckResult(1.0, "无法解析 LLM 响应，默认通过");
      }
      int colonIndex = trimmed.indexOf(':', scoreStart);
      int commaIndex = trimmed.indexOf(',', colonIndex);
      int braceIndex = trimmed.indexOf('}', colonIndex);
      int endIndex = commaIndex > 0 ? commaIndex : braceIndex;
      if (colonIndex < 0 || endIndex < 0) {
        return new ConsistencyCheckResult(1.0, "无法解析 LLM 响应格式，默认通过");
      }
      double score = Double.parseDouble(trimmed.substring(colonIndex + 1, endIndex).trim());
      // 限制分数范围
      score = Math.max(0.0, Math.min(1.0, score));

      // 提取 reasoning
      String reasoning = "LLM 校验通过";
      int reasonStart = trimmed.indexOf("\"reasoning\"");
      if (reasonStart > 0) {
        int reasonColon = trimmed.indexOf(':', reasonStart);
        if (reasonColon > 0) {
          int reasonEnd = trimmed.indexOf('}', reasonColon);
          if (reasonEnd > reasonColon) {
            reasoning =
                trimmed.substring(reasonColon + 1, reasonEnd).trim().replaceAll("^\"|\"$", "");
          }
        }
      }

      log.info(
          "[SemanticConsistency] 校验完成: score={}, query='{}'",
          score,
          userQuery.length() > 30 ? userQuery.substring(0, 30) + "..." : userQuery);
      return new ConsistencyCheckResult(score, reasoning);
    } catch (NumberFormatException e) {
      log.warn("[SemanticConsistency] 解析 LLM 响应失败: {}", e.getMessage());
      return new ConsistencyCheckResult(1.0, "解析失败，默认通过");
    }
  }
}
