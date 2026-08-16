package com.njydsz.message.server.template.cache;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.server.template.TemplateEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 带 AST 缓存的模板引擎。
 *
 * <p>核心优化：将高频模板预编译为指令树（{@link TemplateAst}）， 避免每次渲染重复执行正则匹配。
 *
 * <p>性能对比（万级词库 + 复杂模板）：
 *
 * <ul>
 *   <li>无缓存：O(n × 正则回溯次数)，高 QPS 下 CPU 飙升
 *   <li>有缓存：O(指令数)，仅首次渲染编译，后续直接遍历指令列表
 * </ul>
 *
 * <p>缓存策略：
 *
 * <ul>
 *   <li>最大容量 1000 条（可通过配置调整）
 *   <li>无 TTL（模板由管理后台维护，变更时主动失效）
 *   <li>线程安全（ConcurrentHashMap + volatile 引用）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachedTemplateEngine implements TemplateEngine {

  /** 变量占位符正则：匹配 ${var} / ${a.b.c} / ${this} / ${@index} / ${var|filter:arg} */
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

  /** if-else 块正则 */
  private static final Pattern IF_ELSE_PATTERN =
      Pattern.compile(
          "\\{\\{#if\\s+([\\w.]+)\\}\\}(.*?)\\{\\{else\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);

  /** 纯 if 块正则（无 else） */
  private static final Pattern IF_PATTERN =
      Pattern.compile("\\{\\{#if\\s+([\\w.]+)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);

  /** each 块正则 */
  private static final Pattern EACH_PATTERN =
      Pattern.compile("\\{\\{#each\\s+([\\w.]+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);

  /** 默认缓存最大容量 */
  private static final int DEFAULT_MAX_CACHE_SIZE = 1000;

  /** 模板 AST 缓存：原始模板字符串 → 编译后的 AST */
  private final Map<String, TemplateAst> astCache = new ConcurrentHashMap<>(128);

  /** 命中次数统计（用于监控） */
  private final AtomicLong cacheHits = new AtomicLong(0);

  /** 未命中次数统计（用于监控） */
  private final AtomicLong cacheMisses = new AtomicLong(0);

  /** 最大缓存容量 */
  private final int maxCacheSize = DEFAULT_MAX_CACHE_SIZE;

  @Override
  public String render(String template, Map<String, Object> params) {
    return render(template, params, null);
  }

  @Override
  public String render(String template, Map<String, Object> params, Set<String> requiredKeys) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    if (params == null && (requiredKeys == null || requiredKeys.isEmpty())) {
      return template;
    }
    Map<String, Object> safeParams = params != null ? params : Map.of();
    if (requiredKeys != null && !requiredKeys.isEmpty()) {
      validateRequired(safeParams, requiredKeys);
    }
    // 获取或编译 AST
    TemplateAst ast = getOrCompile(template);
    // 使用 AST 渲染
    return renderAst(ast, safeParams);
  }

  /** 获取或编译模板 AST。 */
  private TemplateAst getOrCompile(String template) {
    TemplateAst cached = astCache.get(template);
    if (cached != null) {
      cacheHits.incrementAndGet();
      return cached;
    }
    cacheMisses.incrementAndGet();
    // 编译模板为 AST
    TemplateAst compiled = compile(template);
    // 控制缓存容量
    if (astCache.size() < maxCacheSize) {
      astCache.put(template, compiled);
    } else {
      log.warn(
          "[TemplateAst] 缓存已达上限({}),跳过缓存新模板: {}",
          maxCacheSize,
          template.length() > 50 ? template.substring(0, 50) + "..." : template);
    }
    return compiled;
  }

  /** 将模板编译为 AST。 */
  private TemplateAst compile(String template) {
    List<TemplateAst.AstInstruction> instructions = new ArrayList<>();
    int pos = 0;
    int len = template.length();
    while (pos < len) {
      // 尝试匹配 each 块
      Matcher eachMatcher = EACH_PATTERN.matcher(template);
      if (eachMatcher.find(pos) && eachMatcher.start() == pos) {
        String key = eachMatcher.group(1);
        String body = eachMatcher.group(2);
        instructions.add(TemplateAst.AstInstruction.eachBlock(key, compile(body)));
        pos = eachMatcher.end();
        continue;
      }
      // 尝试匹配 if-else 块
      Matcher ifElseMatcher = IF_ELSE_PATTERN.matcher(template);
      if (ifElseMatcher.find(pos) && ifElseMatcher.start() == pos) {
        String key = ifElseMatcher.group(1);
        String truePart = ifElseMatcher.group(2);
        String falsePart = ifElseMatcher.group(3);
        TemplateAst falseBranch = compile(falsePart);
        instructions.add(TemplateAst.AstInstruction.ifBlock(key, compile(truePart), falseBranch));
        pos = ifElseMatcher.end();
        continue;
      }
      // 尝试匹配纯 if 块
      Matcher ifMatcher = IF_PATTERN.matcher(template);
      if (ifMatcher.find(pos) && ifMatcher.start() == pos) {
        String key = ifMatcher.group(1);
        String truePart = ifMatcher.group(2);
        instructions.add(TemplateAst.AstInstruction.ifBlock(key, compile(truePart), null));
        pos = ifMatcher.end();
        continue;
      }
      // 尝试匹配变量
      Matcher varMatcher = VAR_PATTERN.matcher(template);
      if (varMatcher.find(pos) && varMatcher.start() == pos) {
        String expr = varMatcher.group(1);
        instructions.add(TemplateAst.AstInstruction.var(expr));
        pos = varMatcher.end();
        continue;
      }
      // 静态文本：读取到下一个特殊标记前
      int nextSpecial = findNextSpecial(template, pos);
      if (nextSpecial > pos) {
        instructions.add(TemplateAst.AstInstruction.text(template.substring(pos, nextSpecial)));
        pos = nextSpecial;
      } else {
        // 当前位置无匹配的标记，作为一个字符处理
        instructions.add(TemplateAst.AstInstruction.text(String.valueOf(template.charAt(pos))));
        pos++;
      }
    }
    return new TemplateAst(template, instructions);
  }

  /** 查找下一个特殊标记的位置（$、{ 等模板语法起始字符）。 */
  private int findNextSpecial(String template, int fromIndex) {
    int len = template.length();
    for (int i = fromIndex; i < len; i++) {
      char c = template.charAt(i);
      if (c == '$' && i + 1 < len && template.charAt(i + 1) == '{') {
        return i;
      }
      if (c == '{' && i + 1 < len && template.charAt(i + 1) == '{') {
        return i;
      }
    }
    return len;
  }

  /** 使用 AST 渲染模板。 */
  private String renderAst(TemplateAst ast, Map<String, Object> params) {
    StringBuilder result = new StringBuilder();
    for (TemplateAst.AstInstruction instruction : ast.getInstructions()) {
      switch (instruction.getType()) {
        case TEXT -> result.append(instruction.getText());
        case VAR -> result.append(renderVar(instruction.getExpression(), params));
        case IF -> {
          Object value = resolve(params, instruction.getConditionKey());
          TemplateAst branch =
              isTruthy(value) ? instruction.getTrueBranch() : instruction.getFalseBranch();
          if (branch != null) {
            result.append(renderAst(branch, params));
          }
        }
        case EACH -> {
          Object listValue = resolve(params, instruction.getIterationKey());
          if (listValue instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
              Map<String, Object> itemScope = new java.util.HashMap<>(params);
              itemScope.put("this", item);
              itemScope.put("@index", index);
              result.append(renderAst(instruction.getBody(), itemScope));
              index++;
            }
          }
        }
      }
    }
    return result.toString();
  }

  /** 渲染单个变量（含管道过滤器）。 */
  private String renderVar(String expression, Map<String, Object> params) {
    String[] parts = expression.split("\\|");
    String key = parts[0].trim();
    Object value = resolve(params, key);
    for (int i = 1; i < parts.length; i++) {
      value = applyFilter(value, parts[i].trim());
    }
    return value == null ? "" : String.valueOf(value);
  }

  /** 应用管道过滤器。 */
  private Object applyFilter(Object value, String filterExpr) {
    if (filterExpr == null || filterExpr.isEmpty()) {
      return value;
    }
    String[] fa = filterExpr.split(":", 2);
    String filterName = fa[0].trim().toLowerCase();
    String filterArg = fa.length > 1 ? fa[1] : "";
    return switch (filterName) {
      case "date" -> formatDate(value, filterArg);
      case "number" -> formatNumber(value, filterArg);
      case "default" ->
          value == null || (value instanceof String s && s.isBlank()) ? filterArg : value;
      case "upper" -> value == null ? null : String.valueOf(value).toUpperCase();
      case "lower" -> value == null ? null : String.valueOf(value).toLowerCase();
      case "truncate" -> truncate(value, filterArg);
      default -> value;
    };
  }

  /** 日期格式化过滤器。 */
  private String formatDate(Object value, String pattern) {
    if (value == null) return "";
    String fmt = pattern.isEmpty() ? "yyyy-MM-dd HH:mm:ss" : pattern;
    try {
      java.time.format.DateTimeFormatter formatter =
          java.time.format.DateTimeFormatter.ofPattern(fmt);
      if (value instanceof java.time.LocalDateTime ldt) return ldt.format(formatter);
      if (value instanceof java.time.LocalDate ld) return ld.format(formatter);
      if (value instanceof java.util.Date date)
        return date.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter);
      if (value instanceof String str) return java.time.LocalDateTime.parse(str).format(formatter);
      if (value instanceof Long ts)
        return new java.util.Date(ts)
            .toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter);
    } catch (Exception e) {
      return String.valueOf(value);
    }
    return String.valueOf(value);
  }

  /** 数字格式化过滤器。 */
  private String formatNumber(Object value, String pattern) {
    if (value == null) return "";
    String fmt = pattern.isEmpty() ? "#,##0.00" : pattern;
    try {
      java.text.DecimalFormat df = new java.text.DecimalFormat(fmt);
      df.setRoundingMode(java.math.RoundingMode.HALF_UP);
      if (value instanceof Number num) return df.format(num);
      if (value instanceof String str) return df.format(new java.math.BigDecimal(str));
    } catch (Exception e) {
      return String.valueOf(value);
    }
    return String.valueOf(value);
  }

  /** 字符串截断过滤器。 */
  private String truncate(Object value, String lengthStr) {
    if (value == null) return "";
    String str = String.valueOf(value);
    try {
      int maxLen = Integer.parseInt(lengthStr.trim());
      return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    } catch (NumberFormatException e) {
      return str;
    }
  }

  /** 解析占位符 key 对应的值，支持 a.b.c 嵌套 Map 取值。 */
  private Object resolve(Map<String, Object> params, String key) {
    if (!key.contains(".")) {
      return params.get(key);
    }
    String[] parts = key.split("\\.");
    Object cur = params;
    for (String p : parts) {
      if (cur instanceof Map<?, ?> map) {
        cur = map.get(p);
      } else {
        return null;
      }
    }
    return cur;
  }

  /** truthy 判定。 */
  private boolean isTruthy(Object value) {
    if (value == null) return false;
    if (value instanceof Boolean b) return b;
    if (value instanceof String s) return !s.isBlank();
    if (value instanceof Number n) return n.doubleValue() != 0d;
    if (value instanceof java.util.Collection<?> c) return !c.isEmpty();
    if (value instanceof Map<?, ?> mp) return !mp.isEmpty();
    return true;
  }

  /** 校验必填参数。 */
  private void validateRequired(Map<String, Object> params, Set<String> requiredKeys) {
    for (String key : requiredKeys) {
      Object value = resolve(params, key);
      if (value == null || (value instanceof String s && s.isBlank())) {
        throw SysException.builder()
            .resultCode(BaseResultCode.BAD_REQUEST)
            .message("模板必填参数缺失: " + key)
            .build();
      }
    }
  }

  /**
   * 主动失效指定模板缓存（模板更新时调用）。
   *
   * @param template 模板内容
   */
  public void evictCache(String template) {
    if (template != null) {
      astCache.remove(template);
      log.debug(
          "[TemplateAst] 缓存已失效: {}",
          template.length() > 30 ? template.substring(0, 30) + "..." : template);
    }
  }

  /** 清空所有缓存。 */
  public void clearCache() {
    astCache.clear();
    log.info("[TemplateAst] 缓存已全部清空");
  }

  /** 当前缓存大小。 */
  public int cacheSize() {
    return astCache.size();
  }

  /**
   * 缓存命中率统计（供监控使用）。
   *
   * @return 命中率（0.0 ~ 1.0），无数据时返回 -1
   */
  public double cacheHitRate() {
    long hits = cacheHits.get();
    long misses = cacheMisses.get();
    long total = hits + misses;
    return total > 0 ? (double) hits / total : -1.0;
  }
}
