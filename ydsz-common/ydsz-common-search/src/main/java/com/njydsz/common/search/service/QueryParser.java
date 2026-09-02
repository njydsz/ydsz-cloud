package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchRequest;

/**
 * 查询理解器
 *
 * <p>解析用户输入的高级搜索语法，提取结构化过滤条件：
 *
 * <ul>
 *   <li>{@code field:value} — 精确匹配字段值
 *   <li>{@code field:"multi word"} — 精确匹配含空格的值
 *   <li>{@code type:project} — 指定搜索类型
 *   <li>剩余文本作为关键词搜索
 * </ul>
 *
 * <p>示例：
 *
 * <pre>
 *   "stage:立项 type:project 系统建设" → keyword="系统建设", filters=[stage=立项], types=[project]
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class QueryParser {

  // 使用 Unicode 单词字符类别，支持中文/日文/韩文等字段名（如「状态:进行中」）
  private static final Pattern FIELD_VALUE_PATTERN =
      Pattern.compile("(\\p{L}+(?:[_]\\p{L}+)*):(?:(\"[^\"]+\")|(\\S+))");

  /**
   * 解析查询语句，提取结构化条件
   *
   * @param rawQuery 原始查询语句
   * @return 解析结果
   */
  public ParseResult parse(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return new ParseResult(rawQuery, new ArrayList<>(16), new ArrayList<>(16));
    }

    List<SearchFilter> filters = new ArrayList<>(16);
    List<String> types = new ArrayList<>(16);
    String remainingKeyword = rawQuery;

    Matcher matcher = FIELD_VALUE_PATTERN.matcher(rawQuery);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String field = matcher.group(1).toLowerCase();
      String value =
          matcher.group(2) != null
              ? matcher.group(2).substring(1, matcher.group(2).length() - 1)
              : matcher.group(3);

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
      matcher.appendReplacement(sb, "");
    }
    matcher.appendTail(sb);
    remainingKeyword = sb.toString().replaceAll("\\s+", " ").trim();

    return new ParseResult(remainingKeyword, filters, types);
  }

  /**
   * 将解析结果应用到搜索请求
   *
   * @param request 原始搜索请求
   * @return 应用了结构化条件的新请求
   */
  public SearchRequest applyTo(SearchRequest request) {
    if (request.getKeyword() == null || request.getKeyword().isBlank()) {
      return request;
    }

    ParseResult result = parse(request.getKeyword());
    if (result.filters().isEmpty() && result.types().isEmpty()) {
      return request;
    }

    SearchRequest.SearchRequestBuilder builder =
        SearchRequest.builder()
            .keyword(result.keyword().isBlank() ? request.getKeyword() : result.keyword())
            .page(request.getPage())
            .pageSize(request.getPageSize())
            .userId(request.getUserId())
            .tenantId(request.getTenantId())
            .roles(request.getRoles())
            .deptId(request.getDeptId())
            .admin(request.isAdmin())
            .highlight(request.isHighlight())
            .fuzzy(request.isFuzzy())
            .titleOnly(request.isTitleOnly());

    List<String> combinedTypes =
        new ArrayList<>(request.getTypes() != null ? request.getTypes() : new ArrayList<>(16));
    combinedTypes.addAll(result.types());
    builder.types(combinedTypes);

    builder.filters(result.filters());

    log.debug(
        "[QueryParser] 原始关键词={}, 解析后关键词={}, 过滤={}, 类型={}",
        request.getKeyword(),
        result.keyword(),
        result.filters(),
        result.types());

    return builder.build();
  }

  /**
   * 查询解析结果
   *
   * @param keyword 剩余关键词（去除 field:value 后）
   * @param filters 提取的过滤条件
   * @param types 提取的类型列表
   */
  public record ParseResult(String keyword, List<SearchFilter> filters, List<String> types) {}
}
