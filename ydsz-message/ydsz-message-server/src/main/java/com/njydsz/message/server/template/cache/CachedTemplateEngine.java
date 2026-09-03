package com.njydsz.message.server.template.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.message.server.template.TemplateEngine;
import com.njydsz.message.server.template.util.TemplateFilterUtil;

/**
 * 带 AST 缓存的模板引擎（基于 ydsz-common-cache 实现）。
 *
 * <p>核心优化：将高频模板预编译为指令树（{@link TemplateAst}），避免每次渲染重复执行正则匹配。
 *
 * <p>性能对比（万级词库 + 复杂模板）：
 *
 * <ul>
 *   <li>无缓存：O(n × 正则回溯次数)，高 QPS 下 CPU 飙升
 *   <li>有缓存：O(指令数)，仅首次渲染编译，后续直接遍历指令列表
 * </ul>
 *
 * <p>缓存策略（Window-TinyLFU）：
 *
 * <ul>
 *   <li>最大容量可通过 {@code ydsz.message.template.cache-max-size} 配置（默认 1000）
 *   <li>写入后 30 分钟过期（模板由管理后台维护，长时间未使用的模板自动淘汰）
 *   <li>缓存命中率、容量等指标自动暴露到 Micrometer {@code ydsz.message.template.cache.*}
 * </ul>
 *
 * <p><b>符合《云顶编码规范》第 27.2.1 节</b>：继承 {@link SentryMetricsAdapter} 桥接指标注册，
 * 不直接操作 {@link MeterRegistry}。监控指标通过 {@code MetricsCollector} SPI 统一上报。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class CachedTemplateEngine extends SentryMetricsAdapter implements TemplateEngine {
  /** if/else 正则 false 分支组 */
  private static final int IF_ELSE_GROUP_FALSE = 3;

  /** 模板日志截断长度 */
  private static final int TEMPLATE_LOG_MAX_LENGTH = 30;

  /** 缓存最小容量 */
  private static final int MIN_CACHE_SIZE = 64;


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

  /** 默认缓存过期时间（分钟） */
  private static final long DEFAULT_EXPIRE_AFTER_WRITE_MINUTES = 30L;

  /** 缓存名称（用于健康检查和监控） */
  private static final String CACHE_NAME = "message:template-ast";

  /** 模板 AST 缓存：原始模板字符串 → 编译后的 AST */
  private final Cache<String, TemplateAst> astCache;

  /** 命中次数统计（监控用） */
  private final AtomicLong cacheHits = new AtomicLong(0);

  /** 未命中次数统计（监控用） */
  private final AtomicLong cacheMisses = new AtomicLong(0);

  /**
   * 构造带监控的缓存实例。
   *
   * <p>使用 ydsz-common-cache 统一管理缓存生命周期，支持配置外部化和健康检查。
   *
   * @param maxCacheSize 最大缓存容量
   * @param expireAfterWriteMinutes 写入后过期时间（分钟）
   */
  public CachedTemplateEngine(
      int maxCacheSize,
      long expireAfterWriteMinutes) {
    super("ydsz_message_template_");
    this.astCache =
        YdszCache.<String, TemplateAst>newBuilder()
            .name(CACHE_NAME)
            .maximumSize(Math.max(MIN_CACHE_SIZE, maxCacheSize))
            .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
            .recordStats()
            .removalListener(this::onCacheRemoval)
            .build();
    log.info(
        "[TemplateAst] 缓存已初始化: maxSize={} expireAfterWrite={}min",
        maxCacheSize,
        expireAfterWriteMinutes);
  }

  /** 缓存移除回调（在 SIZE 驱逐时输出警告） */
  private void onCacheRemoval(String key, TemplateAst value, RemovalCause cause) {
    if (cause == RemovalCause.SIZE) {
      log.warn(
          "[TemplateAst] 缓存容量驱逐: cause={} cacheSize={}",
          cause,
          astCache.estimatedSize());
    }
  }

  /**
   * 注册 Micrometer 监控指标。
   *
   * <p>通过 {@link SentryMetricsAdapter#gauge(String, Supplier, String...)} 注册动态 Gauge，
   * 符合《云顶编码规范》第 27.2.1 节「禁止直接操作 MeterRegistry」的强制要求。
   * 使用 {@code ydsz.message.template.cache.*} 指标前缀。
   */
  @PostConstruct
  public void registerMetrics() {
    // 注册缓存容量 Gauge（动态 Supplier）
    gauge("cache.size", () -> (double) astCache.estimatedSize(),
        "engine", "template.ast");
    // 注册缓存命中率 Gauge（动态 Supplier）
    gauge("cache.hit.rate", () -> astCache.getHitRate(),
        "engine", "template.ast");
    // 注册缓存驱逐数 Gauge（动态 Supplier）
    gauge("cache.eviction.count", () -> (double) astCache.getStats().getEvictionCount(),
        "engine", "template.ast");
    log.info("[TemplateAst] 缓存监控指标已注册（通过 SentryMetricsAdapter 桥接）");
  }

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

  /**
   * 获取或编译模板 AST。
   *
   * @param template 待编译的原始模板字符串
   * @return 编译后的模板 AST；若缓存命中则直接返回缓存对象
   */
  private TemplateAst getOrCompile(String template) {
    return astCache.get(
        template,
        key -> {
          cacheMisses.incrementAndGet();
          return compile(key);
        });
  }

  /**
   * 将模板编译为 AST。
   *
   * @param template 待编译的原始模板字符串
   * @return 编译后的模板 AST 指令列表包装对象
   */
  private TemplateAst compile(String template) {
    List<TemplateAst.AstInstruction> instructions = new ArrayList<>(16);
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
        String falsePart = ifElseMatcher.group(IF_ELSE_GROUP_FALSE);
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

  /**
   * 查找下一个特殊标记的位置（$、{ 等模板语法起始字符）。
   *
   * @param template 原始模板字符串
   * @param fromIndex 从此位置开始向后搜索
   * @return 下一个特殊标记的起始位置；若不存在则返回模板长度
   */
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

  /**
   * 使用 AST 渲染模板。
   *
   * @param ast 编译后的模板 AST
   * @param params 渲染参数 key→value 映射
   * @return 渲染后的字符串
   */
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
              Map<String, Object> itemScope = new LinkedHashMap<>(params);
              itemScope.put("this", item);
              itemScope.put("@index", index);
              result.append(renderAst(instruction.getBody(), itemScope));
              index++;
            }
          }
        }
                default -> {
            // 未知指令忽略
          }
        }
    }
    return result.toString();
  }

  /**
   * 渲染单个变量（含管道过滤器）。
   *
   * @param expression 含可选管道过滤器的变量表达式（如 "user.name|truncate:20"）
   * @param params 渲染参数 key→value 映射
   * @return 渲染后的字符串（null 值返回空字符串）
   */
  private String renderVar(String expression, Map<String, Object> params) {
    String[] parts = expression.split("\\|");
    String key = parts[0].trim();
    Object value = resolve(params, key);
    for (int i = 1; i < parts.length; i++) {
      value = applyFilter(value, parts[i].trim());
    }
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * 应用管道过滤器（委托至 {@link TemplateFilterUtil}）。
   *
   * @param value 过滤前的原始值
   * @param filterExpr 过滤器表达式（如 "truncate:20" 或 "date:yyyy-MM-dd"）
   * @return 过滤后的值
   */
  private Object applyFilter(Object value, String filterExpr) {
    return TemplateFilterUtil.applyFilter(value, filterExpr);
  }

  /**
   * 解析占位符 key 对应的值（委托至 {@link TemplateFilterUtil}）。
   *
   * @param params 渲染参数 key→value 映射
   * @param key 待解析的占位符 key（支持嵌套路径如 "user.name"）
   * @return 解析到的值；若 key 不存在则返回 null
   */
  private Object resolve(Map<String, Object> params, String key) {
    return TemplateFilterUtil.resolve(params, key);
  }

  /**
   * truthy 判定（委托至 {@link TemplateFilterUtil}）。
   *
   * @param value 待判定的对象
   * @return 若 value 为非 null、非 false、非空集合/字符串则返回 true
   */
  private boolean isTruthy(Object value) {
    return TemplateFilterUtil.isTruthy(value);
  }

  /**
   * 校验必填参数。
   *
   * @param params 待校验的渲染参数
   * @param requiredKeys 必须非空的 key 集合
   */
  private void validateRequired(Map<String, Object> params, Set<String> requiredKeys) {
    for (String key : requiredKeys) {
      Object value = resolve(params, key);
      if (value == null || (value instanceof String s && s.isBlank())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
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
      astCache.invalidate(template);
      log.debug(
          "[TemplateAst] 缓存已失效: {}",
          template.length() > TEMPLATE_LOG_MAX_LENGTH
              ? template.substring(0, TEMPLATE_LOG_MAX_LENGTH) + "..."
              : template);
    }
  }

  /** 清空所有缓存。 */
  public void clearCache() {
    astCache.invalidateAll();
    cacheHits.set(0);
    cacheMisses.set(0);
    log.info("[TemplateAst] 缓存已全部清空");
  }

  /**
   * 当前缓存大小。
   *
   * @return 当前缓存中的 AST 条目数
   */
  public long cacheSize() {
    return astCache.estimatedSize();
  }

  /**
   * 当前缓存大小（供 OpsController 使用）。
   *
   * @return 缓存条目数
   */
  public long getCacheSize() {
    return cacheSize();
  }

  /**
   * 根据模板编码清除缓存（支持热更新时失效）。
   *
   * <p>遍历缓存 key，模糊匹配包含指定 templateCode 的所有条目并清除。
   *
   * @param templateCode 模板编码
   */
  public void evictByTemplateCode(String templateCode) {
    if (templateCode == null || templateCode.isBlank()) {
      return;
    }
    long evicted =
        astCache.keySet().stream()
            .filter(key -> key.contains(templateCode))
            .peek(astCache::invalidate)
            .count();
    log.info(
        "[CachedTemplateEngine] evictByTemplateCode: templateCode={}, evicted={}",
        templateCode,
        evicted);
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

  /**
   * 缓存详细统计信息。
   *
   * @return 包含缓存大小、命中率、驱逐数的格式化字符串
   */
  public String cacheStats() {
    return String.format(
        "size=%d, hitRate=%.2f%%, evictions=%d",
        astCache.estimatedSize(),
        astCache.getHitRate() * 100,
        astCache.getStats().getEvictionCount());
  }

  /**
   * 获取缓存统计信息（结构化对象，供运维接口使用）。
   *
   * <p>返回 {@link com.njydsz.common.cache.stats.CacheStats}，
   * 包含 hitCount、missCount、hitRate、evictionCount 等详细指标。
   *
   * @return CacheStats 对象
   */
  public CacheStats getCacheStats() {
    return astCache.getStats();
  }
}
