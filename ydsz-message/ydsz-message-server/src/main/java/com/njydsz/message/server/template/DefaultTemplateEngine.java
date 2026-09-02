package com.njydsz.message.server.template;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.server.template.util.TemplateFilterUtil;

/**
 * 默认模板引擎实现（P0-3 增强）。
 *
 * <p>支持三层渲染能力，向后兼容 {@code ${var}} 简单变量语法：
 *
 * <ol>
 *   <li><b>变量替换</b>：{@code ${var}} / {@code ${a.b.c}} 嵌套 Map 取值；未命中替换为空串。 在 {@code {{#each}}}
 *       块内额外支持 {@code ${this}}、{@code ${this.prop}}、{@code ${@index}}。
 *   <li><b>条件渲染</b>：{@code {{#if var}}A{{else}}B{{/if}}}； truthy 判定规则：null→false / Boolean→自身 /
 *       String→非空白 / Number→非 0 / Collection→非空 / Map→非空 / 其他→true。else 分支可省略。
 *   <li><b>循环渲染</b>：{@code {{#each list}}...{{/each}}}； 仅对 {@link Iterable} 元素迭代（Map 不视为可迭代），每次迭代将
 *       {@code this} 与 {@code @index} 注入到子作用域；非可迭代值渲染空串。支持嵌套 each 与 if。
 * </ol>
 *
 * <p>渲染顺序：{@code {{#each}}} → {@code {{#if}}} → {@code ${var}}}（由内向外，确保块内条件与变量先解析）。
 *
 * <p>多渠道差异化由 {@code TemplateService.loadByCodeAndChannel} 在模板加载层实现， 引擎仅按传入的模板内容渲染。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class DefaultTemplateEngine implements TemplateEngine {
  /** if/else 正则 false 分支组 */
  private static final int IF_ELSE_GROUP_FALSE = 3;


  /** 变量占位符正则：匹配 ${var} / ${a.b.c} / ${this} / ${@index} / ${var|filter:arg} */
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

  /** if-else 块正则：{{#if var}}truePart{{else}}falsePart{{/if}} */
  private static final Pattern IF_ELSE_PATTERN =
      Pattern.compile(
          "\\{\\{#if\\s+([\\w.]+)\\}\\}(.*?)\\{\\{else\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);

  /** 纯 if 块正则（无 else）：{{#if var}}truePart{{/if}} */
  private static final Pattern IF_PATTERN =
      Pattern.compile("\\{\\{#if\\s+([\\w.]+)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);

  /** each 块正则：{{#each list}}body{{/each}} */
  private static final Pattern EACH_PATTERN =
      Pattern.compile("\\{\\{#each\\s+([\\w.]+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);

  /**
   * 渲染模板（无必填参数校验）
   *
   * @param template 模板内容
   * @param params 参数映射（可为 null）
   * @return 渲染后的字符串；模板为空时返回空串
   */
  @Override
  public String render(String template, Map<String, Object> params) {
    return render(template, params, null);
  }

  /**
   * 渲染模板（支持必填参数校验）
   *
   * <p>渲染顺序：each 块 → if 块 → 变量替换（由内向外）。
   *
   * @param template 模板内容
   * @param params 参数映射
   * @param requiredKeys 必填参数 key 集合（可为 null 或空）
   * @return 渲染后的字符串
   * @throws SysException 必填参数缺失或为空时抛出
   */
  @Override
  public String render(String template, Map<String, Object> params, Set<String> requiredKeys) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    // 保留原行为：params 为 null 且无必填校验时，返回原模板（不做任何替换）
    if (params == null && (requiredKeys == null || requiredKeys.isEmpty())) {
      return template;
    }
    Map<String, Object> safeParams = params != null ? params : Map.of();
    if (requiredKeys != null && !requiredKeys.isEmpty()) {
      validateRequired(safeParams, requiredKeys);
    }
    String result = template;
    result = processEach(result, safeParams);
    result = processIf(result, safeParams);
    result = processVars(result, safeParams);
    return result;
  }

  /**
   * 校验必填参数：缺失 / null / 空白字符串均视为缺失，抛 {@link SysException}。
   *
   * @param params 参数映射
   * @param requiredKeys 必填 key 集合
   */
  private void validateRequired(Map<String, Object> params, Set<String> requiredKeys) {
    for (String key : requiredKeys) {
      Object value = resolve(params, key);
      if (value == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("模板必填参数缺失: " + key)
            .build();
      }
      if (value instanceof String s && s.isBlank()) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("模板必填参数为空: " + key)
            .build();
      }
    }
  }

  /**
   * 处理 {@code {{#each list}}...{{/each}}} 块，递归支持嵌套。
   *
   * @param template 模板内容
   * @param params 参数映射
   * @return 处理后的内容
   */
  private String processEach(String template, Map<String, Object> params) {
    Matcher m = EACH_PATTERN.matcher(template);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(1);
      String body = m.group(2);
      Object value = resolve(params, key);
      String replacement = renderEachBody(body, value, params);
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 渲染 each 块体：对 {@link Iterable} 元素逐项渲染，注入 {@code this} / {@code @index}。
   *
   * @param body 块体模板
   * @param listValue 列表值
   * @param parentParams 父级参数（用于继承非循环变量）
   * @return 拼接后的渲染结果
   */
  private String renderEachBody(String body, Object listValue, Map<String, Object> parentParams) {
    if (!(listValue instanceof Iterable<?> iterable)) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int index = 0;
    for (Object item : iterable) {
      Map<String, Object> itemScope = new HashMap<>(parentParams);
      itemScope.put("this", item);
      itemScope.put("@index", index);
      String rendered = body;
      // 递归处理嵌套 each（内层先于外层变量替换）
      rendered = processEach(rendered, itemScope);
      rendered = processIf(rendered, itemScope);
      rendered = processVars(rendered, itemScope);
      out.append(rendered);
      index++;
    }
    return out.toString();
  }

  /**
   * 处理 {@code {{#if var}}...{{else}}...{{/if}}} 与 {@code {{#if var}}...{{/if}}} 块。 先处理 if-else，再处理纯
   * if，避免误匹配。
   *
   * @param template 模板内容
   * @param params 参数映射
   * @return 处理后的内容
   */
  private String processIf(String template, Map<String, Object> params) {
    // ① 先处理含 else 的 if 块
    Matcher elseMatcher = IF_ELSE_PATTERN.matcher(template);
    StringBuffer sb = new StringBuffer();
    while (elseMatcher.find()) {
      String key = elseMatcher.group(1);
      String truePart = elseMatcher.group(2);
      String falsePart = elseMatcher.group(IF_ELSE_GROUP_FALSE);
      Object value = resolve(params, key);
      String replacement = isTruthy(value) ? truePart : falsePart;
      elseMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    elseMatcher.appendTail(sb);
    // ② 再处理纯 if 块（无 else）
    Matcher ifMatcher = IF_PATTERN.matcher(sb.toString());
    StringBuffer sb2 = new StringBuffer();
    while (ifMatcher.find()) {
      String key = ifMatcher.group(1);
      String truePart = ifMatcher.group(2);
      Object value = resolve(params, key);
      String replacement = isTruthy(value) ? truePart : "";
      ifMatcher.appendReplacement(sb2, Matcher.quoteReplacement(replacement));
    }
    ifMatcher.appendTail(sb2);
    return sb2.toString();
  }

  /**
   * 处理 {@code ${var}} 变量替换，支持管道过滤器。
   *
   * <p>P1-7: 支持的过滤器：
   *
   * <ul>
   *   <li>{@code ${var|date:yyyy-MM-dd HH:mm:ss}} — 日期格式化
   *   <li>{@code ${var|number:#,##0.00}} — 数字格式化
   *   <li>{@code ${var|default:N/A}} — 默认值
   *   <li>{@code ${var|upper}} — 转大写
   *   <li>{@code ${var|lower}} — 转小写
   *   <li>{@code ${var|truncate:50}} — 截断到指定长度
   * </ul>
   *
   * 多个过滤器可链式使用：{@code ${var|default:N/A|upper}}
   *
   * @param template 模板内容
   * @param params 参数映射
   * @return 处理后的内容
   */
  private String processVars(String template, Map<String, Object> params) {
    Matcher m = VAR_PATTERN.matcher(template);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String expr = m.group(1);
      // 解析管道表达式
      String[] parts = expr.split("\\|");
      String key = parts[0].trim();
      Object value = resolve(params, key);
      // 应用过滤器链
      for (int i = 1; i < parts.length; i++) {
        value = applyFilter(value, parts[i].trim());
      }
      String replacement = value == null ? "" : String.valueOf(value);
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * P1-7: 应用单个过滤器。
   *
   * @param value 输入值
   * @param filterExpr 过滤器表达式（如 {@code date:yyyy-MM-dd} / {@code upper}）
   * @return 过滤后的值
   */
  private Object applyFilter(Object value, String filterExpr) {
    return TemplateFilterUtil.applyFilter(value, filterExpr);
  }

  private boolean isTruthy(Object value) {
    return TemplateFilterUtil.isTruthy(value);
  }

  private Object resolve(Map<String, Object> params, String key) {
    return TemplateFilterUtil.resolve(params, key);
  }

  /**
   * GAP-5: 渲染 Markdown 内容为通道原生格式。
   *
   * <p>IM 平台支持原生 Markdown，直接返回；其他通道降级为纯文本 （简单去除 Markdown 语法标记，保留可读性）。
   *
   * @param markdownContent Markdown 原文
   * @param channel 目标通道
   * @return 通道适配后的内容
   */
  public String renderMarkdown(String markdownContent, String channel) {
    if (markdownContent == null || markdownContent.isBlank()) {
      return "";
    }
    if ("DINGTALK".equalsIgnoreCase(channel)
        || "FEISHU".equalsIgnoreCase(channel)
        || "WECOM".equalsIgnoreCase(channel)) {
      // IM 通道支持原生 Markdown，直接返回
      return markdownContent;
    }
    // 其他通道降级为纯文本
    return markdownContent
        .replaceAll("#+\\s*", "")
        .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
        .replaceAll("\\*(.+?)\\*", "$1")
        .replaceAll("\\[(.+?)]\\(.+?\\)", "$1")
        .replaceAll("`(.+?)`", "$1")
        .replaceAll("^>\\s*", "")
        .replaceAll("^-\\s*", "• ");
  }
}
