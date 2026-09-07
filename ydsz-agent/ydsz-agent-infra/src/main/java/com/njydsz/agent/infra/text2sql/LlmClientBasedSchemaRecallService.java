package com.njydsz.agent.infra.text2sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.text2sql.SchemaRecallService;
import com.njydsz.agent.domain.text2sql.TableSchema;

/**
 * 基于 LLM 关键词匹配 + 启发式排序的 Schema 召回实现。
 *
 * <p>工作流程：
 *
 * <ol>
 *   <li>提取用户问题中的关键词集合
 *   <li>对每张表计算匹配分数（表名命中 + 列名命中 + 描述命中，加权求和）
 *   <li>若配置了 LLM 调用，使用 LLM 做二次精排
 *   <li>返回 Top-N 表 Schema 子集
 * </ol>
 *
 * <p>降级策略：当 LLM 调用失败时，回退到纯启发式匹配结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class LlmClientBasedSchemaRecallService implements SchemaRecallService {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 表名匹配权重 */
  private static final double TABLE_NAME_WEIGHT = 3.0;

  /** 列名匹配权重 */
  private static final double COLUMN_NAME_WEIGHT = 2.0;

  /** 描述匹配权重 */
  private static final double DESCRIPTION_WEIGHT = 1.0;

  /** 中文连续字符模式（用于从中文问题中提取关键词） */
  private static final Pattern CHINESE_WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");

  /** 英文单词模式 */
  private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z_]{2,}");

  /** LLM 最大输出 Token 数 */
  private static final int RECALL_MAX_TOKENS = 256;

  private final LlmClient llmClient;
  private final String defaultModel;

  public LlmClientBasedSchemaRecallService(
      LlmClient llmClient,
      @Value("${ydsz.agent.llm.default-model:gpt-4o-mini}") String defaultModel) {
    this.llmClient = llmClient;
    this.defaultModel = defaultModel;
  }

  @Override
  public List<TableSchema> recallRelevantSchemas(
      String query, List<TableSchema> availableTables, int maxRecall) {
    if (availableTables == null || availableTables.isEmpty()) {
      return List.of();
    }
    if (maxRecall <= 0) {
      return List.of();
    }
    // 提取问题关键词
    Set<String> keywords = extractKeywords(query);
    log.debug("[SchemaRecall] 问题关键词: {}", keywords);
    // 启发式打分
    List<TableSchemaScore> scored = new ArrayList<>(availableTables.size());
    for (TableSchema table : availableTables) {
      double score = scoreTable(table, keywords);
      if (score > 0) {
        scored.add(new TableSchemaScore(table, score));
      }
    }
    // 按分数降序排列
    scored.sort((a, b) -> Double.compare(b.score, a.score));
    // 提取 Top-N
    int limit = Math.min(maxRecall, scored.size());
    List<TableSchema> result = new ArrayList<>(limit);
    for (int i = 0; i < limit; i++) {
      result.add(scored.get(i).table);
    }
    log.info(
        "[SchemaRecall] 召回完成: query='{}', candidates={}, recalled={}",
        query.length() > 50 ? query.substring(0, 50) + "..." : query,
        availableTables.size(),
        result.size());
    return result;
  }

  /**
   * 对单张表计算匹配分数。
   *
   * @param table 表 Schema
   * @param keywords 关键词集合
   * @return 匹配分数（0 表示无匹配）
   */
  private double scoreTable(TableSchema table, Set<String> keywords) {
    if (keywords.isEmpty()) {
      return 0;
    }
    double score = 0;
    String tableNameLower = table.tableName().toLowerCase();
    // 表名命中
    for (String kw : keywords) {
      if (tableNameLower.contains(kw.toLowerCase())) {
        score += TABLE_NAME_WEIGHT;
        break;
      }
    }
    // 列名命中
    if (table.columns() != null) {
      for (TableSchema.ColumnDefinition col : table.columns()) {
        String colNameLower = col.name().toLowerCase();
        for (String kw : keywords) {
          if (colNameLower.contains(kw.toLowerCase())) {
            score += COLUMN_NAME_WEIGHT;
            break;
          }
        }
      }
    }
    // 描述命中
    if (table.description() != null && !table.description().isEmpty()) {
      String descLower = table.description().toLowerCase();
      for (String kw : keywords) {
        if (descLower.contains(kw.toLowerCase())) {
          score += DESCRIPTION_WEIGHT;
        }
      }
    }
    return score;
  }

  /**
   * 从用户问题中提取关键词集合（中英文混合）。
   *
   * @param query 用户问题
   * @return 关键词集合
   */
  private Set<String> extractKeywords(String query) {
    Set<String> keywords = new LinkedHashSet<>(COLLECTION_CAPACITY);
    if (query == null || query.isBlank()) {
      return keywords;
    }
    // 中文词
    Matcher chineseMatcher = CHINESE_WORD_PATTERN.matcher(query);
    while (chineseMatcher.find()) {
      keywords.add(chineseMatcher.group());
    }
    // 英文词
    Matcher englishMatcher = ENGLISH_WORD_PATTERN.matcher(query);
    while (englishMatcher.find()) {
      keywords.add(englishMatcher.group());
    }
    return keywords;
  }

  /**
   * 调用 LLM 做二次精排（当前版本降级为纯启发式，预留扩展接口）。
   *
   * <p>当 LLM 调用失败时，回退到纯启发式匹配结果。
   *
   * @param query 用户问题
   * @param candidates 候选表 Schema 列表
   * @param maxRecall 最大召回数量
   * @return 精排后的表 Schema 列表
   */
  private List<TableSchema> llmRerank(
      String query, List<TableSchema> candidates, int maxRecall) {
    // 构建 Prompt
    StringBuilder prompt = new StringBuilder(512);
    prompt.append(
        """
        你是数据库 Schema 分析助手。根据用户问题，从以下候选表中选出最相关的表。

        候选表：
        """);
    for (TableSchema table : candidates) {
      prompt.append("- ").append(table.tableName());
      if (table.description() != null && !table.description().isEmpty()) {
        prompt.append("（").append(table.description()).append("）");
      }
      prompt.append("\n");
    }
    prompt.append("\n用户问题：").append(query);
    prompt.append("\n\n请只返回最相关的表名列表（每行一个），最多 ").append(maxRecall).append(" 个。");

    ChatRequest request =
        ChatRequest.builder()
            .model(defaultModel)
            .messages(List.of(ChatMessage.user(prompt.toString(), null)))
            .temperature(0)
            .maxTokens(RECALL_MAX_TOKENS)
            .build();

    try {
      ChatResponse response = llmClient.chat(request);
      String content = response.getContent();
      if (content == null || content.isBlank()) {
        return candidates;
      }
      // 解析 LLM 返回的表名列表
      Set<String> selectedNames = new LinkedHashSet<>(COLLECTION_CAPACITY);
      for (String line : content.split("\n")) {
        String trimmed = line.trim().replaceAll("^[-*\\d.]+\\s*", "");
        if (!trimmed.isEmpty()) {
          selectedNames.add(trimmed.toLowerCase());
          if (selectedNames.size() >= maxRecall) {
            break;
          }
        }
      }
      // 按 LLM 返回的顺序排列候选表
      List<TableSchema> reranked = new ArrayList<>(Math.min(maxRecall, candidates.size()));
      for (TableSchema table : candidates) {
        if (selectedNames.contains(table.tableName().toLowerCase())) {
          reranked.add(table);
          if (reranked.size() >= maxRecall) {
            break;
          }
        }
      }
      // LLM 返回的结果可能不足，补充启发式结果
      if (reranked.size() < maxRecall) {
        for (TableSchema table : candidates) {
          if (!reranked.contains(table)) {
            reranked.add(table);
            if (reranked.size() >= maxRecall) {
              break;
            }
          }
        }
      }
      log.info("[SchemaRecall] LLM 精排完成: selected={}", reranked.size());
      return reranked;
    } catch (Exception e) {
      log.warn("[SchemaRecall] LLM 精排失败，回退到启发式结果: {}", e.getMessage());
      return candidates;
    }
  }

  /**
   * Schema 打分内部记录。
   *
   * @param table 表 Schema
   * @param score 匹配分数
   */
  private record TableSchemaScore(TableSchema table, double score) {}
}
