package com.njydsz.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.nextwiki.domain.query.SearchQuery;
import com.njydsz.nextwiki.domain.query.SearchQuery.FieldQuery;

/**
 * 高级搜索语法解析器。
 *
 * <p>将用户输入的搜索字符串解析为结构化的 {@link SearchQuery}，支持以下语法：
 *
 * <ul>
 *   <li><b>全文词</b>：普通单词，在所有可搜索字段中模糊匹配
 *   <li><b>字段限定</b>：{@code name:报告}、{@code tag:重要}、{@code suffix:pdf}、{@code path:/docs}
 *   <li><b>短语精确匹配</b>：{@code "季度财务报告"}（引号包围）
 *   <li><b>包含/排除</b>：{@code +必须包含}、{@code -必须排除}
 *   <li><b>布尔运算符</b>：{@code AND}、{@code OR}、{@code NOT}（大小写不敏感）
 *   <li><b>通配符</b>：{@code report*}（前缀匹配）
 * </ul>
 *
 * <p><b>解析规则：</b>
 *
 * <ol>
 *   <li>先提取引号短语（优先级最高）
 *   <li>再提取字段限定表达式（field:value）
 *   <li>再处理 +/- 前缀的包含/排除词
 *   <li>剩余部分按空格分词，处理 AND/OR/NOT 布尔运算符
 * </ol>
 *
 * <p><b>示例：</b>
 *
 * <pre>
 *   输入: name:报告 tag:重要 "季度财务" -草稿
 *   解析: fieldQueries=[name:报告, tag:important], phrases=[季度财务], mustExcludeTerms=[草稿]
 *
 *   输入: report* AND (pdf OR docx)
 *   解析: fullTextTerms=[report*], 布尔逻辑由调用方处理
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SearchQueryParser {

  /** 字段限定语法正则：field:value（value 不含空格） */
  private static final Pattern FIELD_PATTERN =
      Pattern.compile("(\\w+):([^\\s\"]+)");

  /** 引号短语正则："..." */
  private static final Pattern PHRASE_PATTERN =
      Pattern.compile("\"([^\"]+)\"");

  /** 支持的字段限定前缀 */
  private static final List<String> SUPPORTED_FIELDS =
      Arrays.asList(
          FieldQuery.FIELD_NAME,
          FieldQuery.FIELD_CONTENT,
          FieldQuery.FIELD_TAG,
          FieldQuery.FIELD_PATH,
          FieldQuery.FIELD_SUFFIX);

  /**
   * 解析用户搜索输入为结构化查询。
   *
   * @param rawInput 用户原始搜索输入
   * @return 解析后的 {@link SearchQuery}（永不返回 null）
   */
  public SearchQuery parse(String rawInput) {
    return parse(rawInput, null, "all", 1, 20);
  }

  /**
   * 解析用户搜索输入为结构化查询（带分页参数）。
   *
   * @param rawInput 用户原始搜索输入
   * @param createdBy 创建人过滤（可为 null）
   * @param scope 搜索作用域（可为 null，默认 "all"）
   * @param page 页码
   * @param pageSize 每页大小
   * @return 解析后的 {@link SearchQuery}
   */
  public SearchQuery parse(
      String rawInput, String createdBy, String scope, int page, int pageSize) {

    // 使用 @Singular 生成的单项添加方法逐个收集解析结果
    SearchQuery.SearchQueryBuilder builder = SearchQuery.builder()
        .scope(scope != null ? scope : "all")
        .page(page)
        .pageSize(pageSize)
        .createdBy(createdBy);

    if (rawInput == null || rawInput.isBlank()) {
      return builder.build();
    }

    String remaining = rawInput.trim();

    // 1. 提取引号短语
    remaining = extractPhrases(remaining, builder);

    // 2. 提取字段限定表达式
    remaining = extractFieldQueries(remaining, builder);

    // 3. 处理 +/- 前缀词和布尔运算符
    processBooleanTerms(remaining, builder);

    SearchQuery query = builder.build();
    log.debug("[SearchQueryParser] 解析完成: rawInput={}, fields={}, phrases={}, include={}, exclude={}",
        rawInput,
        query.getFieldQueries().size(),
        query.getPhrases().size(),
        query.getMustIncludeTerms().size(),
        query.getMustExcludeTerms().size());

    return query;
  }

  /**
   * 提取引号包围的短语。
   *
   * @param input 输入字符串
   * @param builder 构建器
   * @return 移除短语后的剩余字符串
   */
  private String extractPhrases(String input, SearchQuery.SearchQueryBuilder builder) {
    Matcher matcher = PHRASE_PATTERN.matcher(input);
    StringBuilder remaining = new StringBuilder(input);

    while (matcher.find()) {
      String phrase = matcher.group(1).trim();
      if (!phrase.isEmpty()) {
        builder.phrase(phrase);
      }
      // 从剩余字符串中移除已匹配的短语
      remaining.replace(matcher.start(), matcher.end(), "");
      matcher = PHRASE_PATTERN.matcher(remaining);
    }

    return remaining.toString();
  }

  /**
   * 提取字段限定表达式（field:value）。
   *
   * @param input 输入字符串
   * @param builder 构建器
   * @return 移除字段限定后的剩余字符串
   */
  private String extractFieldQueries(String input, SearchQuery.SearchQueryBuilder builder) {
    Matcher matcher = FIELD_PATTERN.matcher(input);
    StringBuilder remaining = new StringBuilder(input);

    while (matcher.find()) {
      String field = matcher.group(1).toLowerCase();
      String value = matcher.group(2).trim();

      if (SUPPORTED_FIELDS.contains(field) && !value.isEmpty()) {
        builder.fieldQuery(FieldQuery.builder()
            .field(field)
            .value(value)
            .build());
        // 从剩余字符串中移除已匹配的字段限定
        remaining.replace(matcher.start(), matcher.end(), "");
        matcher = FIELD_PATTERN.matcher(remaining);
      }
    }

    return remaining.toString();
  }

  /**
   * 处理布尔运算符和 +/- 前缀词。
   *
   * @param input 输入字符串
   * @param builder 构建器
   */
  private void processBooleanTerms(String input, SearchQuery.SearchQueryBuilder builder) {
    // 按空格分词，处理 +/- 前缀和 AND/OR/NOT
    String[] tokens = input.trim().split("\\s+");

    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i].trim();
      if (token.isEmpty()) {
        continue;
      }

      String upperToken = token.toUpperCase();

      // 跳过布尔运算符关键字（AND/OR/NOT 作为连接词，不直接作为搜索词）
      if ("AND".equals(upperToken) || "OR".equals(upperToken)) {
        continue;
      }

      // NOT 关键字：下一个词为排除词
      if ("NOT".equals(upperToken)) {
        if (i + 1 < tokens.length) {
          String nextToken = tokens[i + 1].trim();
          if (!nextToken.isEmpty()) {
            builder.mustExcludeTerm(nextToken);
            i++; // 跳过下一个词
          }
        }
        continue;
      }

      // + 前缀：必须包含
      if (token.startsWith("+") && token.length() > 1) {
        builder.mustIncludeTerm(token.substring(1));
        continue;
      }

      // - 前缀：必须排除
      if (token.startsWith("-") && token.length() > 1) {
        builder.mustExcludeTerm(token.substring(1));
        continue;
      }

      // 普通全文词
      if (!token.isEmpty()) {
        builder.fullTextTerm(token);
      }
    }
  }
}
