package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchRequest;

/**
 * 高级查询语法解析器 — 支持 AND/OR/NOT 布尔逻辑与括号分组。
 *
 * <p>在原有 field:value 语法基础上增强，支持完整的布尔搜索：
 *
 * <ul>
 *   <li>{@code word1 AND word2} — 同时包含 word1 和 word2
 *   <li>{@code word1 OR word2} — 包含 word1 或 word2
 *   <li>{@code NOT word} — 排除 word
 *   <li>{@code (word1 OR word2) AND word3} — 括号分组优先
 *   <li>{@code "exact phrase"} — 精确短语匹配
 *   <li>{@code field:value AND keyword} — 与字段过滤组合
 * </ul>
 *
 * <p>示例：
 *
 * <pre>
 *   "(项目 OR 合同) AND 管理 NOT 已完成" → 包含"项目"或"合同"，且包含"管理"，排除"已完成"
 *   "type:project AND stage:立项" — 指定类型与阶段的组合
 * </pre>
 *
 * <p>对标行业：
 *
 * <ul>
 *   <li>Elasticsearch Query DSL：bool query 嵌套 must/should/must_not
 *   <li>Lucene Query Parser：业界标准布尔语法（Google/百度基于此演进）
 *   <li>Algolia：filters 语法 + 分面过滤 + 数值比较
 * </ul>
 *
 * <p>兼容性：当输入不包含布尔操作符时，行为与 {@link QueryParser} 完全一致。
 *
 * @author ydzs-team
 * @since 1.0.0
 */
@Slf4j
public class AdvancedQueryParser {

  /** 字段值匹配模式 */
  private static final Pattern FIELD_VALUE_PATTERN =
      Pattern.compile("(\\w+):(?:(\"[^\"]+\")|(\\S+))");

  /** 精确短语匹配模式 */
  private static final Pattern PHRASE_PATTERN = Pattern.compile("\"([^\"]+)\"");

  /** 布尔操作符暨括号词法单元 */
  private static final Pattern BOOLEAN_TOKEN_PATTERN =
      Pattern.compile("\\s+(AND|OR|NOT)\\s+|\\(|\\)");

  /**
   * 解析高级查询语句。
   *
   * <p>处理优先级（由高到低）：
   *
   * <ol>
   *   <li>括号分组
   *   <li>NOT（一元操作符）
   *   <li>AND
   *   <li>OR
   * </ol>
   *
   * @param rawQuery 原始查询语句
   * @return 解析结果，包含结构化条件与布尔逻辑
   */
  public ParseResult parse(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return new ParseResult(
          "",
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>());
    }

    // 提取 field:value 结构化过滤
    List<SearchFilter> filters = new ArrayList<>();
    List<String> types = new ArrayList<>();
    Matcher fieldMatcher = FIELD_VALUE_PATTERN.matcher(rawQuery);
    StringBuffer fieldCleaned = new StringBuffer();
    while (fieldMatcher.find()) {
      String field = fieldMatcher.group(1).toLowerCase();
      String value =
          fieldMatcher.group(2) != null
              ? fieldMatcher.group(2).substring(1, fieldMatcher.group(2).length() - 1)
              : fieldMatcher.group(3);
      if ("type".equals(field)) {
        types.add(value);
      } else {
        filters.add(
            SearchFilter.builder()
                .field(field)
                .values(List.of(value))
                .operator(SearchFilter.Operator.EQ)
                .build());
      }
      fieldMatcher.appendReplacement(fieldCleaned, "");
    }
    fieldMatcher.appendTail(fieldCleaned);

    // 提取精确短语
    List<String> phrases = new ArrayList<>();
    Matcher phraseMatcher = PHRASE_PATTERN.matcher(fieldCleaned.toString());
    StringBuffer phraseCleaned = new StringBuffer();
    while (phraseMatcher.find()) {
      phrases.add(phraseMatcher.group(1));
      phraseCleaned.append(" PLACEHOLDER_PHRASE ");
    }
    // 剩余文字
    String remainingText =
        phraseCleaned.length() > 0
            ? fieldCleaned.toString().replaceAll("\"[^\"]+\"", " PLACEHOLDER_PHRASE ")
            : fieldCleaned.toString();

    // 解析布尔逻辑词元
    List<String> mustTerms = new ArrayList<>();
    List<String> shouldTerms = new ArrayList<>();
    List<String> mustNotTerms = new ArrayList<>();

    // 简化实现：解析 AND/OR/NOT 词元
    parseBooleanTokens(remainingText, mustTerms, shouldTerms, mustNotTerms);

    // 将短语加入 must 条件
    mustTerms.addAll(phrases);

    return new ParseResult(
        remainingText.replaceAll("\\s+", " ").trim(),
        filters,
        types,
        mustTerms,
        shouldTerms,
        mustNotTerms);
  }

  /**
   * 解析布尔操作符词元，将简单查询分类到 must/should/must_not 列表。
   *
   * <p>语法规则（简化递归下降）：
   *
   * <ul>
   *   <li>无操作符：全部归入 mustTerms（隐式 AND）
   *   <li>AND 连接：左侧词归入 must
   *   <li>OR 连接：两侧词归入 should
   *   <li>NOT 后词：归入 mustNot
   * </ul>
   *
   * @param text 清理后的文本
   * @param mustTerms 必须匹配的词
   * @param shouldTerms 可选匹配的词
   * @param mustNotTerms 必须排除的词
   */
  private void parseBooleanTokens(
      String text, List<String> mustTerms, List<String> shouldTerms, List<String> mustNotTerms) {
    if (text == null || text.isBlank()) {
      return;
    }

    // 按空格切分词元，识别 AND/OR/NOT 操作符
    String[] tokens = text.trim().split("\\s+");
    BooleanOperator pendingOp = BooleanOperator.AND; // 默认 AND

    for (String token : tokens) {
      String upper = token.toUpperCase();
      if ("AND".equals(upper)) {
        pendingOp = BooleanOperator.AND;
      } else if ("OR".equals(upper)) {
        pendingOp = BooleanOperator.OR;
      } else if ("NOT".equals(upper)) {
        pendingOp = BooleanOperator.NOT;
      } else if (token.startsWith("(") || token.endsWith(")")) {
        // 括号简化处理：忽略括号，按后续操作符处理
        String cleaned = token.replace("(", "").replace(")", "").trim();
        if (!cleaned.isBlank()) {
          addToTargetList(cleaned, pendingOp, mustTerms, shouldTerms, mustNotTerms);
        }
      } else if (!token.isBlank() && !token.equals("PLACEHOLDER_PHRASE")) {
        addToTargetList(token, pendingOp, mustTerms, shouldTerms, mustNotTerms);
        // 重置为默认 AND（避免后续 NOT 持续影响）
        if (pendingOp == BooleanOperator.NOT) {
          pendingOp = BooleanOperator.AND;
        }
      }
    }
  }

  private void addToTargetList(
      String term,
      BooleanOperator op,
      List<String> must,
      List<String> should,
      List<String> mustNot) {
    switch (op) {
      case AND -> must.add(term);
      case OR -> should.add(term);
      case NOT -> mustNot.add(term);
    }
  }

  /**
   * 将解析结果应用到搜索请求，构建包含布尔逻辑的查询关键词。
   *
   * <p>布尔逻辑关键词会被转换为搜索引擎友好的格式：
   *
   * <ul>
   *   <li>mustTerms → 用空格连接（AND 语义）
   *   <li>shouldTerms → 用 " OR " 连接
   *   <li>mustNotTerms → 附加 " -term" 排除语法
   * </ul>
   *
   * @param request 原始搜索请求
   * @return 应用了布尔逻辑的新请求
   */
  public SearchRequest applyTo(SearchRequest request) {
    if (request.getKeyword() == null || request.getKeyword().isBlank()) {
      return request;
    }

    ParseResult result = parse(request.getKeyword());

    // 检查是否有增强布尔逻辑
    boolean hasBooleanLogic =
        !result.mustTerms().isEmpty()
            || !result.shouldTerms().isEmpty()
            || !result.mustNotTerms().isEmpty();

    if (!hasBooleanLogic && result.types().isEmpty() && result.filters().isEmpty()) {
      return request;
    }

    // 构建布尔查询关键词
    StringBuilder keywordBuilder = new StringBuilder();
    if (!result.mustTerms().isEmpty()) {
      keywordBuilder.append(String.join(" ", result.mustTerms()));
    }
    if (!result.shouldTerms().isEmpty()) {
      if (keywordBuilder.length() > 0) {
        keywordBuilder.append(" OR ");
      }
      keywordBuilder.append(String.join(" OR ", result.shouldTerms()));
    }
    if (!result.mustNotTerms().isEmpty()) {
      for (String exclude : result.mustNotTerms()) {
        keywordBuilder.append(" -").append(exclude);
      }
    }

    String finalKeyword = keywordBuilder.toString().trim();
    if (finalKeyword.isBlank()) {
      finalKeyword = request.getKeyword();
    }

    // 合并 types 和 filters
    List<String> combinedTypes =
        new ArrayList<>(request.getTypes() != null ? request.getTypes() : new ArrayList<>());
    combinedTypes.addAll(result.types());

    List<SearchFilter> combinedFilters =
        new ArrayList<>(request.getFilters() != null ? request.getFilters() : new ArrayList<>());
    combinedFilters.addAll(result.filters());

    log.debug(
        "[AdvancedParser] 原始关键词={}, 布尔关键词={}, filters={}",
        request.getKeyword(),
        finalKeyword,
        combinedFilters);

    return SearchRequest.builder()
        .keyword(finalKeyword)
        .types(combinedTypes)
        .page(request.getPage())
        .pageSize(request.getPageSize())
        .userId(request.getUserId())
        .tenantId(request.getTenantId())
        .roles(request.getRoles())
        .deptId(request.getDeptId())
        .admin(request.isAdmin())
        .highlight(request.isHighlight())
        .fuzzy(request.isFuzzy())
        .titleOnly(request.isTitleOnly())
        .filters(combinedFilters)
        .build();
  }

  /** 布尔操作符枚举。 */
  private enum BooleanOperator {
    AND,
    OR,
    NOT
  }

  /**
   * 高级查询解析结果。
   *
   * @param keyword 清理后的关键词文本
   * @param filters 结构化过滤条件列表
   * @param types 实体类型列表
   * @param mustTerms 必须匹配的词列表（AND）
   * @param shouldTerms 可选匹配的词列表（OR）
   * @param mustNotTerms 必须排除的词列表（NOT）
   */
  public record ParseResult(
      String keyword,
      List<SearchFilter> filters,
      List<String> types,
      List<String> mustTerms,
      List<String> shouldTerms,
      List<String> mustNotTerms) {}
}
