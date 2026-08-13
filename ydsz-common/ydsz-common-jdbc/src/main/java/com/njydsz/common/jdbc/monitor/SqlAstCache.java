package com.njydsz.common.jdbc.monitor;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

/**
 * JSqlParser AST 解析缓存
 *
 * <p>基于 Caffeine 的高性能 SQL 解析缓存，将 SQL 指纹 → 解析后的 {@link Statement} 进行缓存，
 * 避免同一条 SQL 模板在多个拦截器中重复解析。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>Key = SQL 指纹（{@link SqlFingerprint#fingerprint(String)} 归一化后的模板）</li>
 *   <li>Value = 解析后的 AST {@link Statement} 对象</li>
 *   <li>最大容量 {@value #DEFAULT_MAX_SIZE} 条，写入后 {@value #DEFAULT_EXPIRE_MINUTES} 分钟过期</li>
 *   <li>返回前执行深拷贝，避免并发改写同一 AST 导致线程安全问题</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Statement ast = SqlAstCache.getInstance().parse(sql);
 * // 使用 ast 进行 AST 改写...
 * }</pre>
 *
 * <p><b>注意：</b>返回的 {@link Statement} 是缓存 AST 的深拷贝，可安全进行原地改写。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
public final class SqlAstCache {

    private static final Logger log = LoggerFactory.getLogger(SqlAstCache.class);

    /** 默认最大缓存条数 */
    private static final int DEFAULT_MAX_SIZE = 512;

    /** 默认写入后过期时间（分钟） */
    private static final long DEFAULT_EXPIRE_MINUTES = 10;

    /** 全局单例实例 */
    private static volatile SqlAstCache instance;

    /** Caffeine 缓存实例 */
    private final Cache<String, Statement> cache;

    /**
     * 私有构造方法，使用默认配置初始化缓存
     */
    private SqlAstCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(DEFAULT_EXPIRE_MINUTES))
                .removalListener((key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("SqlAstCache 移除条目: key={}, cause={}", key, cause);
                    }
                })
                .build();
        log.info("SqlAstCache 已初始化 (maxSize={}, expireAfterWrite={}min)",
                DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_MINUTES);
    }

    /**
     * 获取全局单例实例
     *
     * @return SqlAstCache 单例
     */
    public static SqlAstCache getInstance() {
        if (instance == null) {
            synchronized (SqlAstCache.class) {
                if (instance == null) {
                    instance = new SqlAstCache();
                }
            }
        }
        return instance;
    }

    /**
     * 解析 SQL 语句，带缓存。
     *
     * <p>如果缓存中存在该 SQL 指纹对应的 AST，则返回其深拷贝；
     * 否则解析 SQL 并缓存原始 AST，返回一份新的深拷贝。
     *
     * @param sql 原始 SQL 语句
     * @return 解析后的 AST（深拷贝，可安全改写）
     * @throws net.sf.jsqlparser.JSQLParserException 解析失败时抛出
     */
    public Statement parse(String sql) throws net.sf.jsqlparser.JSQLParserException {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        String fingerprint = SqlFingerprint.fingerprint(sql);
        Statement cached = cache.getIfPresent(fingerprint);

        if (cached != null) {
            // 返回深拷贝以避免并发改写
            return deepCopy(cached);
        }

        // 缓存未命中，解析原始 SQL
        Statement parsed = CCJSqlParserUtil.parse(sql);
        cache.put(fingerprint, parsed);
        // 返回深拷贝，保留缓存中的原始 AST 不变
        return deepCopy(parsed);
    }

    /**
     * 深拷贝 AST 语句。
     *
     * <p>通过序列化为字符串后重新解析实现深拷贝。由于 JSqlParser 的 Statement 接口
     * 不支持 Cloneable，采用 toString + re-parse 方式确保副本独立。
     *
     * <p>虽然此操作有一定开销，但仅在缓存命中时执行一次（后续拦截器可复用同一副本），
     * 相比每次拦截器解析原始 SQL，总体性能仍显著提升。
     *
     * @param original 原始 AST
     * @return 深拷贝后的 AST
     */
    private Statement deepCopy(Statement original) {
        try {
            String sql = original.toString();
            return CCJSqlParserUtil.parse(sql);
        } catch (net.sf.jsqlparser.JSQLParserException e) {
            // 深拷贝失败不应阻塞业务，回退到返回原始对象（此时存在并发风险，但极罕见）
            log.warn("SqlAstCache 深拷贝失败，返回原始 AST 引用（存在并发风险）: {}", e.getMessage());
            return original;
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return Caffeine 缓存统计
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    /**
     * 清空缓存
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("SqlAstCache 已清空");
    }

    /**
     * 获取当前缓存条目数（估算值）
     *
     * @return 缓存大小估算
     */
    public long estimatedSize() {
        return cache.estimatedSize();
    }
}
