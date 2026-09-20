package com.njydsz.common.jdbc.monitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import net.sf.jsqlparser.JSQLParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JSqlParser AST 解析缓存（Spring Bean）
 *
 * <p>基于 LRU 淘汰策略的 SQL 归一化缓存，将 SQL 指纹 → 归一化后的 SQL
 * 字符串进行缓存，避免同一条 SQL 模板在多个拦截器中重复解析。每次 {@link
 * #parse(String)} 调用从缓存的归一化 SQL 直接解析，返回独立的 AST 实例，
 * 避免昂贵的 {@code toString() + re-parse} 深拷贝双通道开销。
 *
 * <p>缓存策略：
 *
 * <ul>
 *   <li>Key = SQL 指纹（{@link SqlFingerprint#fingerprint(String)} 归一化后的模板）
 *   <li>Value = 归一化后的 SQL 字符串（用于解析出独立 AST 实例）
 *   <li>最大容量可配置（默认 {@code 512} 条），LRU 淘汰
 *   <li>每次调用都从缓存的归一化 SQL 重新解析，确保返回独立的 AST 实例，线程安全
 *   <li>使用读写锁保证缓存复合操作线程安全
 *   <li>MeterRegistry 可选注入，提供 hit/miss 计数指标
 * </ul>
 *
 * <p>性能分析：
 *
 * <ul>
 *   <li><b>原实现</b>：缓存命中时仍需 {@code original.toString()} 序列化 +
 *       {@code CCJSqlParserUtil.parse()} 反序列化，双通道开销 ≈ 纯解析的 2 倍
 *   <li><b>优化后</b>：缓存命中时仅执行 {@code CCJSqlParserUtil.parse(normalizedSql)}，
 *       单通道开销 ≈ 纯解析的 1 倍，热路径性能提升约 50%
 *   <li><b>缓存未命中</b>：预解析一次获取归一化 SQL（{@code parsed.toString()}），
 *       后将其存入缓存，避免下次重复归一化
 * </ul>
 *
 * <p>配置方式：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     sql-ast-cache:
 *       max-size: 512
 * }</pre>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @Component
 * public class MyInterceptor {
 *     private final SqlAstCache sqlAstCache;
 *
 *     public MyInterceptor(SqlAstCache sqlAstCache) {
 *         this.sqlAstCache = sqlAstCache;
 *     }
 *
 *     public void process(String sql) {
 *         Statement ast = sqlAstCache.parse(sql);
 *         // 使用 ast 进行 AST 改写...
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意：</b>返回的 {@link Statement} 是独立的解析实例，可安全进行原地改写；每次调用
 * {@link #parse(String)} 都会构造新的 AST，请勿在同一拦截器实例中持有返回值跨请求复用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public final class SqlAstCache {

  private static final Logger log = LoggerFactory.getLogger(SqlAstCache.class);

  /** Micrometer tag name for cache result (hit/miss). */
  public static final String METRIC_NAME = "ydsz.jdbc.sql.ast.cache";
  public static final String TAG_RESULT = "result";
  public static final String TAG_HIT = "hit";
  public static final String TAG_MISS = "miss";

  /** 默认最大缓存条数 */
  public static final int DEFAULT_MAX_SIZE = 512;

  /** LRU 缓存映射（access-order）：SQL 指纹 → 归一化后的 SQL 字符串 */
  private final Map<String, String> cache;

  /** 读写锁，保护缓存复合操作 */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  /** 最大缓存容量 */
  private final int maxSize;

  /** 缓存命中计数 */
  private final LongAdder hitCount = new LongAdder();

  /** 缓存未命中计数 */
  private final LongAdder missCount = new LongAdder();

  /** MeterRegistry，可选注入 */
  private MeterRegistry meterRegistry;

  /**
   * 构造方法，初始化 LRU 缓存。
   *
   * @param maxSize 最大缓存条数（默认 512）
   */
  public SqlAstCache(
      @Value("${ydsz.jdbc.sql-ast-cache.max-size:" + DEFAULT_MAX_SIZE + "}") int maxSize) {
    if (maxSize <= 0) {
      this.maxSize = DEFAULT_MAX_SIZE;
    } else {
      this.maxSize = maxSize;
    }
    this.cache =
        new LinkedHashMap<String, String>(this.maxSize, 0.75f, true) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > SqlAstCache.this.maxSize;
          }
        };
    log.info("SqlAstCache 已初始化 (maxSize={})", this.maxSize);
  }

  /**
   * 设置 MeterRegistry（可选），开启 hit/miss 指标推送。
   *
   * <p>可选注入：只在需要监控指标的项目中注册（如 Gateway / Agent 引擎）。
   * 如果未注入 MeterRegistry，指标仅通过 {@link #getHitCount()} 获取。
   *
   * @param meterRegistry Spring Boot Actuator 的 MeterRegistry
   */
  public void setMeterRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    log.info("SqlAstCache 已接入 MeterRegistry");
  }

  /**
   * 解析 SQL 语句，带缓存。
   *
   * <p>如果缓存中存在该 SQL 指纹，则直接使用缓存的归一化 SQL 进行解析，返回独立的 AST 实例。
   * 缓存未命中时，先解析原始 SQL 获取 {@link Statement}，然后将其归一化字符串 {@code
   * parsed.toString()} 存入缓存，返回同一解析结果（避免二次解析浪费）。
   *
   * @param sql 原始 SQL 语句
   * @return 解析后的 AST（独立实例，可安全改写）
   * @throws JSQLParserException 解析失败时抛出
   */
  public Statement parse(String sql) throws JSQLParserException {
    if (sql == null || sql.isEmpty()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }

    String fingerprint = SqlFingerprint.fingerprint(sql);

    // 读操作：尝试从缓存获取归一化 SQL
    String normalizedSql = null;
    lock.readLock().lock();
    try {
      normalizedSql = cache.get(fingerprint);
    } finally {
      lock.readLock().unlock();
    }

    if (normalizedSql != null) {
      // 缓存命中：直接从归一化 SQL 解析，返回独立 AST 实例
      hitCount.increment();
      recordMetric(TAG_HIT);
      return CCJSqlParserUtil.parse(normalizedSql);
    }

    // 缓存未命中：先解析原始 SQL
    Statement parsed = CCJSqlParserUtil.parse(sql);
    missCount.increment();
    recordMetric(TAG_MISS);

    // 将归一化后的 SQL 字符串存入缓存（parsed.toString() 是 LRU 淘汰时最有价值的表示）
    String cachedSql = parsed.toString();

    lock.writeLock().lock();
    try {
      cache.put(fingerprint, cachedSql);
    } finally {
      lock.writeLock().unlock();
    }

    return parsed;
  }

  /**
   * 推送 hit/miss 指标到 MeterRegistry（弱依赖，未注入时静默跳过）。
   *
   * @param result "hit" 或 "miss"
   */
  private void recordMetric(String result) {
    if (meterRegistry != null) {
      Iterable<Tag> tags = Tags.of(TAG_RESULT, result);
      Counter.builder(METRIC_NAME).tags(tags).register(meterRegistry).increment();
    }
  }

  /** 清空缓存 */
  public void invalidateAll() {
    lock.writeLock().lock();
    try {
      cache.clear();
    } finally {
      lock.writeLock().unlock();
    }
    hitCount.reset();
    missCount.reset();
    log.info("SqlAstCache 已清空");
  }

  /**
   * 获取当前缓存条目数
   *
   * @return 缓存大小
   */
  public int size() {
    lock.readLock().lock();
    try {
      return cache.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 获取缓存最大容量
   *
   * @return 最大缓存条数
   */
  public int maxSize() {
    return maxSize;
  }

  /**
   * 获取累积缓存命中次数。
   *
   * @return 命中计数
   */
  public long getHitCount() {
    return hitCount.sum();
  }

  /**
   * 获取累积缓存未命中次数。
   *
   * @return 未命中计数
   */
  public long getMissCount() {
    return missCount.sum();
  }
}
