package com.njydsz.pmis.literule.server.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.njydsz.pmis.common.util.json.JsonUtils;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.server.config.LiteRuleProperties;
import com.njydsz.pmis.literule.server.spi.RuleConfigProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 多级缓存规则配置提供者（P1-1）
 *
 * <p>装饰器模式实现的 ydsz-pmis-common-cache (L1) + Redis (L2) 两级缓存，
 * 对标银行风控/Drools 优化实践，减少 DB 压力。
 *
 * <p>缓存层级：
 * <ul>
 *   <li>L1（本地内存）：TTL 60s，最大 1000 条，命中后直接返回</li>
 *   <li>L2（Redis 分布式）：TTL 300s，L1 未命中时查询，命中后回填 L1</li>
 *   <li>DB：L1/L2 均未命中时查询数据库，命中后回填 L1 和 L2</li>
 * </ul>
 *
 * <p>缓存失效策略：
 * <ul>
 *   <li>写操作（save/toggleEnabled/delete）：本地 L1 清除 + Redis 版本号递增</li>
 *   <li>监听 {@link RuleConfigRefreshEvent}：本地 L1 清除</li>
 *   <li>Redis 版本号变更检测：每次 L1 命中前检查版本号，变化则清除 L1（1 秒限流）</li>
 * </ul>
 *
 * <p>降级策略：
 * <ul>
 *   <li>Redis 不可用时降级为仅 L1 缓存（记录 WARN 日志）</li>
 *   <li>Caffeine 始终可用（本地内存）</li>
 *   <li>构造器参数 {@code redissonClient} 为 null 或 {@code l2Enabled=false} 时禁用 L2</li>
 * </ul>
 *
 * <p>并发安全：ydsz-pmis-common-cache 的 {@code cache.get(key, mapper)} 保证同一 key 仅一个线程执行加载，
 * 其余线程阻塞等待结果，天然防止缓存击穿。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
public class CachingRuleConfigProvider implements RuleConfigProvider {

    /** Redis 版本号 key */
    private static final String VERSION_KEY = "literule:rules:version";
    /** L1/L2 缓存 key - 全部启用规则 */
    private static final String KEY_ENABLED = "literule:rules:enabled";
    /** L1/L2 缓存 key - 全部规则 */
    private static final String KEY_ALL = "literule:rules:all";
    /** L1/L2 缓存 key 前缀 - 按规则编码 */
    private static final String KEY_CODE_PREFIX = "literule:rules:code:";
    /** L1/L2 缓存 key 前缀 - 按租户 */
    private static final String KEY_TENANT_ENABLED_PREFIX = "literule:rules:tenant:";
    /** L2 NULL 标记（Redis 中表示 null 结果） */
    private static final String L2_NULL_MARKER = "__NULL__";
    /** L1 NULL 标记（本地缓存中表示 null 结果，不缓存 null） */
    private static final RuleDefinition L1_NULL_MARKER = RuleDefinition.builder()
            .code("__L1_NULL_MARKER__").build();

    /** 版本号检查间隔（纳秒，1 秒）- 限流避免每次 L1 命中都打 Redis */
    private static final long VERSION_CHECK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final RuleConfigProvider delegate;
    /** L2 客户端；null 表示禁用 L2（仅 L1） */
    private final RedissonClient redissonClient;
    private final LiteRuleProperties.CacheConfig cacheConfig;

    /** L1 列表缓存（loadEnabledRules / loadAllRules / loadEnabledRulesByTenant） */
    private final Cache<String, List<RuleDefinition>> listCache;
    /** L1 单条缓存（findByCode，使用 NULL_MARKER 处理 null） */
    private final Cache<String, RuleDefinition> singleCache;

    /** 上次版本号检查时间（纳秒） */
    private volatile long lastVersionCheckNanos = 0L;
    /** 上次看到的版本号（-1 表示尚未初始化） */
    private volatile long lastSeenVersion = -1L;

    /**
     * Spring 注入构造器，使用系统时钟。
     *
     * @param delegate        被装饰的 RuleConfigProvider（DB/配置中心实现）
     * @param redissonClient  Redisson 客户端（null 时禁用 L2）
     * @param properties      配置属性
     */
    public CachingRuleConfigProvider(RuleConfigProvider delegate,
                                      RedissonClient redissonClient,
                                      LiteRuleProperties properties) {
        this(delegate, redissonClient, properties.getCache(), null);
    }

    /**
     * 测试用构造器。
     *
     * @param delegate        被装饰的 RuleConfigProvider
     * @param redissonClient  Redisson 客户端（null 时禁用 L2）
     * @param cacheConfig     缓存配置
     * @param unused          预留参数（原 Caffeine Ticker，已废弃）
     */
    CachingRuleConfigProvider(RuleConfigProvider delegate,
                              RedissonClient redissonClient,
                              LiteRuleProperties.CacheConfig cacheConfig,
                              Object unused) {
        this.delegate = delegate;
        // L2 启用条件：RedissonClient 非空 且 配置启用 L2
        this.redissonClient = (redissonClient != null && cacheConfig.isL2Enabled()) ? redissonClient : null;
        this.cacheConfig = cacheConfig;
        this.listCache = YdszCache.<String, List<RuleDefinition>>newBuilder()
                .type(CacheType.TTL)
                .expireAfterWrite(cacheConfig.getL1TtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(cacheConfig.getL1MaxSize())
                .build();
        this.singleCache = YdszCache.<String, RuleDefinition>newBuilder()
                .type(CacheType.TTL)
                .expireAfterWrite(cacheConfig.getL1TtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(cacheConfig.getL1MaxSize())
                .build();
        log.info("[LiteRule-Cache] 多级缓存已初始化 (L1 ttl={}s maxSize={}, L2 enabled={})",
                cacheConfig.getL1TtlSeconds(), cacheConfig.getL1MaxSize(), this.redissonClient != null);
    }

    @Override
    public List<RuleDefinition> loadEnabledRules() {
        checkVersionAndInvalidate();
        return listCache.get(KEY_ENABLED, k -> loadListFromL2OrDb(KEY_ENABLED, delegate::loadEnabledRules));
    }

    @Override
    public List<RuleDefinition> loadAllRules() {
        checkVersionAndInvalidate();
        return listCache.get(KEY_ALL, k -> loadListFromL2OrDb(KEY_ALL, delegate::loadAllRules));
    }

    @Override
    public RuleDefinition findByCode(String ruleCode) {
        checkVersionAndInvalidate();
        String cacheKey = KEY_CODE_PREFIX + ruleCode;
        RuleDefinition cached = singleCache.get(cacheKey,
                k -> loadSingleFromL2OrDb(cacheKey, () -> delegate.findByCode(ruleCode)));
        return cached == L1_NULL_MARKER ? null : cached;
    }

    @Override
    public List<RuleDefinition> loadEnabledRulesByTenant(String tenantId) {
        checkVersionAndInvalidate();
        String cacheKey = KEY_TENANT_ENABLED_PREFIX + tenantId + ":enabled";
        return listCache.get(cacheKey, k -> loadListFromL2OrDb(cacheKey,
                () -> delegate.loadEnabledRulesByTenant(tenantId)));
    }

    @Override
    public RuleDefinition save(RuleDefinition definition, String operator) {
        try {
            return delegate.save(definition, operator);
        } finally {
            invalidateAll("save:" + definition.getCode());
        }
    }

    @Override
    public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
        try {
            delegate.toggleEnabled(ruleCode, enabled, operator);
        } finally {
            invalidateAll("toggle:" + ruleCode);
        }
    }

    // ==================== 事件监听 ====================

    /**
     * 监听规则配置刷新事件，清除 L1 缓存
     *
     * <p>使用 {@code @TransactionalEventListener(AFTER_COMMIT)} 确保仅在校验/持久化事务
     * 成功提交后才清缓存，回滚时不清（避免回滚后缓存被清空导致额外 DB 回源）。
     * {@code fallbackExecution=true} 确保非事务上下文（如 Redis 跨节点广播回调线程）
     * 中发布的事件仍能正常触发。
     *
     * <p>由 {@link com.njydsz.pmis.literule.server.config.RuleHotReloader} 同源事件触发，
     * 也可由分布式广播器（{@code RedisRuleConfigBroadcaster}）转发跨节点事件触发。
     *
     * @param event 刷新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigRefresh(RuleConfigRefreshEvent event) {
        log.info("[LiteRule-Cache] 收到刷新事件，清除 L1 缓存: type={}, ruleCode={}",
                event.getChangeType(), event.getRuleCode());
        invalidateL1();
    }

    // ==================== 内部方法 ====================

    /**
     * 检查 Redis 版本号是否变化，变化则清除 L1
     *
     * <p>使用 1 秒间隔的限流，避免每次 L1 命中都打 Redis。
     * Redis 不可用时跳过检查（降级为仅 L1 TTL 失效）。
     */
    private void checkVersionAndInvalidate() {
        if (redissonClient == null) return;
        long now = System.nanoTime();
        if (now - lastVersionCheckNanos < VERSION_CHECK_INTERVAL_NANOS) {
            return;
        }
        try {
            RAtomicLong versionAtomic = redissonClient.getAtomicLong(VERSION_KEY);
            long currentVersion = versionAtomic.get();
            long lastSeen = lastSeenVersion;
            if (lastSeen >= 0 && lastSeen != currentVersion) {
                log.info("[LiteRule-Cache] 检测到版本号变化: {} -> {}, 清除 L1", lastSeen, currentVersion);
                invalidateL1();
            }
            lastSeenVersion = currentVersion;
            lastVersionCheckNanos = now;
        } catch (Exception e) {
            log.warn("[LiteRule-Cache] 版本号检查失败，跳过 L1 失效: {}", e.getMessage());
        }
    }

    /**
     * 从 L2 或 DB 加载列表
     *
     * <p>调用此方法时 L1 已未命中。加载结果会回填 L2（L1 由缓存框架自动回填）。
     */
    private List<RuleDefinition> loadListFromL2OrDb(String l2Key, Supplier<List<RuleDefinition>> loader) {
        // 1. 尝试 L2
        if (redissonClient != null) {
            try {
                RBucket<String> bucket = redissonClient.getBucket(l2Key);
                String json = bucket.get();
                if (json != null) {
                    List<RuleDefinition> l2Value = JSON.parseObject(json, new YdszJsonType<List<RuleDefinition>>() {});
                    if (l2Value != null) {
                        log.debug("[LiteRule-Cache] L2 命中: {}", l2Key);
                        return l2Value;
                    }
                }
            } catch (Exception e) {
                log.warn("[LiteRule-Cache] L2 读取失败，降级为 DB: {}", e.getMessage());
            }
        }

        // 2. 加载 DB
        List<RuleDefinition> dbValue = loader.get();
        if (dbValue == null) {
            dbValue = Collections.emptyList();
        }

        // 3. 回填 L2
        fillL2(l2Key, dbValue);

        return dbValue;
    }

    /**
     * 从 L2 或 DB 加载单条
     *
     * <p>调用此方法时 L1 已未命中。加载结果会回填 L2（L1 由缓存框架自动回填）。
     * null 结果使用 {@link #L1_NULL_MARKER}（L1）和 {@link #L2_NULL_MARKER}（L2）标记，
     * 避免不缓存 null 导致的缓存穿透。
     */
    private RuleDefinition loadSingleFromL2OrDb(String l2Key, Supplier<RuleDefinition> loader) {
        // 1. 尝试 L2
        if (redissonClient != null) {
            try {
                RBucket<String> bucket = redissonClient.getBucket(l2Key);
                String json = bucket.get();
                if (json != null) {
                    if (L2_NULL_MARKER.equals(json)) {
                        log.debug("[LiteRule-Cache] L2 命中 NULL 标记: {}", l2Key);
                        return L1_NULL_MARKER;
                    }
                    RuleDefinition l2Value = JsonUtils.fromJson(json, RuleDefinition.class);
                    if (l2Value != null) {
                        log.debug("[LiteRule-Cache] L2 命中: {}", l2Key);
                        return l2Value;
                    }
                }
            } catch (Exception e) {
                log.warn("[LiteRule-Cache] L2 读取失败，降级为 DB: {}", e.getMessage());
            }
        }

        // 2. 加载 DB
        RuleDefinition dbValue = loader.get();

        // 3. 回填 L2
        if (dbValue == null) {
            fillL2(l2Key, L2_NULL_MARKER);
            return L1_NULL_MARKER;
        } else {
            fillL2(l2Key, dbValue);
            return dbValue;
        }
    }

    /**
     * 回填 L2 缓存
     *
     * @param key   L2 key
     * @param value 值；String 直接写入（用于 NULL 标记），其他对象 JSON 序列化
     */
    private void fillL2(String key, Object value) {
        if (redissonClient == null) return;
        try {
            String json;
            if (value instanceof String) {
                json = (String) value;
            } else {
                json = JsonUtils.toJson(value);
            }
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(json, Duration.ofSeconds(cacheConfig.getL2TtlSeconds()));
        } catch (Exception e) {
            log.warn("[LiteRule-Cache] L2 写入失败: {}", e.getMessage());
        }
    }

    /**
     * 清除 L1 缓存（本地）
     */
    private void invalidateL1() {
        listCache.invalidateAll();
        singleCache.invalidateAll();
    }

    /**
     * 清除全部缓存（L1 + L2 版本号递增）
     *
     * <p>写操作后调用：
     * <ol>
     *   <li>清除本节点 L1（立即生效）</li>
     *   <li>递增 Redis 版本号（其他节点在下次版本检查时清除其 L1）</li>
     * </ol>
     *
     * @param reason 失效原因（用于日志）
     */
    private void invalidateAll(String reason) {
        invalidateL1();
        if (redissonClient != null) {
            try {
                RAtomicLong versionAtomic = redissonClient.getAtomicLong(VERSION_KEY);
                long newVersion = versionAtomic.incrementAndGet();
                lastSeenVersion = newVersion;
                log.debug("[LiteRule-Cache] L2 版本号递增: {} -> reason={}", newVersion, reason);
            } catch (Exception e) {
                log.warn("[LiteRule-Cache] L2 版本号递增失败: {}", e.getMessage());
            }
        }
    }
}
