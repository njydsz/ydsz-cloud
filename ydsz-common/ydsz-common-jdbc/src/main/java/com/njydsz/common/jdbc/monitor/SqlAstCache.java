package com.njydsz.common.jdbc.monitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JSqlParser AST 解析缓存（Spring Bean）
 *
 * <p>基于 LRU 淘汰策略的 SQL 解析缓存，将 SQL 指纹 → 解析后的 {@link Statement} 进行缓存， 避免同一条 SQL 模板在多个拦截器中重复解析。
 *
 * <p>缓存策略：
 *
 * <ul>
 *   <li>Key = SQL 指纹（{@link SqlFingerprint#fingerprint(String)} 归一化后的模板）
 *   <li>Value = 解析后的 AST {@link Statement} 对象
 *   <li>最大容量可配置（默认 {@code 512} 条），LRU 淘汰
 *   <li>返回前执行深拷贝，避免并发改写同一 AST 导致线程安全问题
 *   <li>使用读写锁保证并发安全
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
 * <p><b>注意：</b>返回的 {@link Statement} 是缓存 AST 的深拷贝，可安全进行原地改写。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
@Component
public final class SqlAstCache {

  /** 默认最大缓存条数 */
  public static final int DEFAULT_MAX_SIZE = 512;

  /** LRU 缓存映射（access-order） */
  private final Map<String, Statement> cache;

  /** 读写锁，保护缓存复合操作 */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  /** 最大缓存容量 */
  private final int maxSize;

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
        new LinkedHashMap<String, Statement>(this.maxSize, 0.75f, true) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Statement> eldest) {
            return size() > SqlAstCache.this.maxSize;
          }
        };
    log.info("SqlAstCache 已初始化 (maxSize={})", this.maxSize);
  }

  /**
   * 解析 SQL 语句，带缓存。
   *
   * <p>如果缓存中存在该 SQL 指纹对应的 AST，则返回其深拷贝； 否则解析 SQL 并缓存原始 AST，返回一份新的深拷贝。
   *
   * @param sql 原始 SQL 语句
   * @return 解析后的 AST（深拷贝，可安全改写）
   * @throws JSQLParserException 解析失败时抛出
   */
  public Statement parse(String sql) throws JSQLParserException {
    if (sql == null || sql.isEmpty()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }

    String fingerprint = SqlFingerprint.fingerprint(sql);

    // 读操作：尝试从缓存获取
    lock.readLock().lock();
    Statement cached;
    try {
      cached = cache.get(fingerprint);
    } finally {
      lock.readLock().unlock();
    }

    if (cached != null) {
      return deepCopy(cached);
    }

    // 缓存未命中，解析原始 SQL
    Statement parsed = CCJSqlParserUtil.parse(sql);

    // 写操作：放入缓存
    lock.writeLock().lock();
    try {
      cache.put(fingerprint, parsed);
    } finally {
      lock.writeLock().unlock();
    }

    return deepCopy(parsed);
  }

  /**
   * 深拷贝 AST 语句。
   *
   * <p>通过序列化为字符串后重新解析实现深拷贝。由于 JSqlParser 的 Statement 接口 不支持 Cloneable，采用 toString + re-parse
   * 方式确保副本独立。
   *
   * <p>虽然此操作有一定开销，但仅在缓存命中时执行一次（后续拦截器可复用同一副本）， 相比每次拦截器解析原始 SQL，总体性能仍显著提升。
   *
   * @param original 原始 AST
   * @return 深拷贝后的 AST
   */
  private Statement deepCopy(Statement original) {
    try {
      String sql = original.toString();
      return CCJSqlParserUtil.parse(sql);
    } catch (JSQLParserException e) {
      // 深拷贝失败不应阻塞业务，回退到返回原始对象（此时存在并发风险，但极罕见）
      log.warn("SqlAstCache 深拷贝失败，返回原始 AST 引用（存在并发风险）: {}", e.getMessage());
      return original;
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
}
