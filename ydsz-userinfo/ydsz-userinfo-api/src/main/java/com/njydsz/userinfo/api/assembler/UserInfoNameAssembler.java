package com.njydsz.userinfo.api.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameAssemblerProperties;
import com.njydsz.common.feign.assembler.NameType;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.userinfo.api.client.OrgQueryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 ydsz-userinfo 服务的 {@link NameAssembler} 默认实现。
 *
 * <p><b>核心特性</b>：
 * <ul>
 *   <li><b>Feign 调用</b>：通过 {@link OrgQueryClient} 的 5 个 batch-names 端点
 *       （user / dept / role / post / company）批量解析 ID → 名称。</li>
 *   <li><b>多级缓存</b>：P2-1 新增 L1（Caffeine 本地缓存，5 分钟 TTL）+ L2（Redis 分布式缓存，10 分钟 TTL，可配置关闭），
 *       {@link #resolveName} 走缓存；{@link #batchResolveNames} 不走缓存（避免污染）。</li>
 *   <li><b>try-catch 降级</b>：所有 Feign 调用包裹 try-catch，失败时返回空 Map / null，
 *       不抛异常阻断业务主流程。</li>
 *   <li><b>兜底策略</b>：{@link #enrich} / {@link #enrichOne} 在 Feign 失败或 ID 未命中时，
 *       用 ID 字符串本身顶替 name 字段（避免前端空白），可通过
 *       {@code ydsz.feign.name-assembler.fallback-to-id=false} 关闭。</li>
 *   <li><b>N+1 防护</b>：{@link #enrich} 内部自动收集所有 ID 后一次批量调用。</li>
 * </ul>
 *
 * <p><b>线程安全</b>：所有状态字段使用并发容器，可被多线程共享。
 *
 * <p><b>注册方式</b>：通过 {@link UserInfoNameAssemblerAutoConfiguration} 注册，
 * 启用条件为 classpath 中存在 {@link OrgQueryClient} 且未注册其它 {@link NameAssembler}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class UserInfoNameAssembler implements NameAssembler {

    private static final String REDIS_KEY_PREFIX = "userinfo:name:";

    private final OrgQueryClient orgQueryClient;
    private final NameAssemblerProperties properties;

    /** L1: 本地 Caffeine 缓存 */
    private final Cache<String, String> l1Cache;

    /** L2: Redis 分布式缓存（可选，依赖 common-redis 存在且启用） */
    private final RedisService redisService;

    /**
     * 构造 UserInfoNameAssembler。
     *
     * @param orgQueryClient   Feign 客户端
     * @param properties       配置属性
     * @param redisProvider    Redis 服务提供者（可选，未配置时为 null）
     */
    public UserInfoNameAssembler(OrgQueryClient orgQueryClient,
                                 NameAssemblerProperties properties,
                                 ObjectProvider<RedisService> redisProvider) {
        this.orgQueryClient = orgQueryClient;
        this.properties = properties;
        this.l1Cache = CacheBuilder.<String, String>newBuilder()
                .maximumSize(properties.getCacheMaxSize())
                .expireAfterWrite(properties.getCacheTtl().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        this.redisService = properties.isRedisCacheEnabled()
                ? redisProvider.getIfAvailable() : null;
        if (properties.isRedisCacheEnabled() && this.redisService == null) {
            log.info("NameAssembler Redis cache enabled but RedisService not available; "
                    + "falling back to L1 cache only");
        }
    }

    @Override
    public Map<String, String> batchResolveNames(NameType type, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result;
        try {
            BaseResponse<Map<String, String>> response = doBatchCall(type, distinctIds);
            if (response == null || !response.isSuccess()) {
                log.warn("UserInfoNameAssembler batch call failed: type={}, size={}, resp={}",
                        type, distinctIds.size(), response == null ? "null" : response.getCode());
                return Collections.emptyMap();
            }
            result = response.getData();
            if (result == null) {
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.warn("UserInfoNameAssembler batch call exception: type={}, size={}, msg={}",
                    type, distinctIds.size(), e.getMessage());
            return Collections.emptyMap();
        }

        // 回填 L1 + L2 缓存
        for (Map.Entry<String, String> entry : result.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                String l1Key = cacheKey(type, entry.getKey());
                l1Cache.put(l1Key, entry.getValue());
                putL2Cache(type, entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public String resolveName(NameType type, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        // L1: 本地缓存（最快）
        String key = cacheKey(type, id);
        String cached = l1Cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // L2: Redis 分布式缓存
        if (redisService != null) {
            String l2Key = redisCacheKey(type, id);
            try {
                String l2Value = redisService.get(l2Key, String.class);
                if (l2Value != null) {
                    // 回填 L1 缓存（L1 TTL < L2 TTL，保证兜底新鲜度）
                    l1Cache.put(key, l2Value);
                    return l2Value;
                }
            } catch (Exception e) {
                log.warn("NameAssembler L2 cache read failed: type={}, id={}", type, id);
            }
        }

        // Feign 兜底（L1+L2 均无命中）
        Map<String, String> result = batchResolveNames(type, List.of(id));
        String name = result.get(id);
        if (name != null && !name.isBlank()) {
            return name;
        }
        l1Cache.remove(key);
        return null;
    }

    @Override
    public <T> void enrich(Collection<T> objects,
                           Function<T, String> idGetter,
                           BiConsumer<T, String> nameSetter,
                           NameType type) {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        // 收集所有非空 ID（保留对象引用以便回写）
        List<T> validObjects = new ArrayList<>(objects.size());
        List<String> ids = new ArrayList<>(objects.size());
        for (T obj : objects) {
            if (obj == null) {
                continue;
            }
            String id = idGetter.apply(obj);
            if (id != null && !id.isBlank()) {
                validObjects.add(obj);
                ids.add(id);
            }
        }
        if (validObjects.isEmpty()) {
            return;
        }

        // 批量解析
        Map<String, String> nameMap = batchResolveNames(type, ids);

        // 回写：未命中时用 ID 顶替（兜底）
        boolean fallbackToId = properties.isFallbackToId();
        for (T obj : validObjects) {
            String id = idGetter.apply(obj);
            String name = nameMap.get(id);
            if (name == null || name.isBlank()) {
                if (fallbackToId) {
                    nameSetter.accept(obj, id);
                }
            } else {
                nameSetter.accept(obj, name);
            }
        }
    }

    @Override
    public <T> void enrichOne(T obj,
                              Function<T, String> idGetter,
                              BiConsumer<T, String> nameSetter,
                              NameType type) {
        if (obj == null) {
            return;
        }
        String id = idGetter.apply(obj);
        if (id == null || id.isBlank()) {
            return;
        }
        String name = resolveName(type, id);
        if (name != null && !name.isBlank()) {
            nameSetter.accept(obj, name);
        } else if (properties.isFallbackToId()) {
            // 兜底：用 ID 顶替 name
            nameSetter.accept(obj, id);
        }
    }

    /**
     * P2-1: 失效指定 ID 的多级缓存
     *
     * <p>在业务数据变更（用户改名、部门删除等）时调用，保证缓存一致性。
     *
     * @param type 实体类型
     * @param id   实体 ID
     */
    public void evict(NameType type, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        // L1
        l1Cache.remove(cacheKey(type, id));
        // L2
        if (redisService != null) {
            try {
                redisService.del(redisCacheKey(type, id));
            } catch (Exception e) {
                log.warn("NameAssembler L2 cache eviction failed: type={}, id={}", type, id);
            }
        }
    }

    /**
     * P2-1: 失效指定类型的全部 L1 缓存（轻量操作，仅清本地 JVM 缓存）
     *
     * <p>建议在名称批量变更后调用。L2 Redis 缓存可由 TTL 自动过期。
     *
     * @param type 实体类型
     */
    public void evictL1ByType(NameType type) {
        if (type == null) {
            return;
        }
        String prefix = type.name() + ":";
        l1Cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * 路由到对应类型的 OrgQueryClient batch 方法。
     */
    private BaseResponse<Map<String, String>> doBatchCall(NameType type, List<String> ids) {
        switch (type) {
            case USER:
                return orgQueryClient.batchUserNames(ids);
            case DEPT:
                return orgQueryClient.batchDeptNames(ids);
            case ROLE:
                return orgQueryClient.batchRoleNames(ids);
            case POST:
                return orgQueryClient.batchPostNames(ids);
            case COMPANY:
                return orgQueryClient.batchCompanyNames(ids);
            default:
                log.warn("UserInfoNameAssembler unsupported type: {}", type);
                return BaseResponse.success(Collections.emptyMap());
        }
    }

    private static String cacheKey(NameType type, String id) {
        return type.name() + ":" + id;
    }

    private static String redisCacheKey(NameType type, String id) {
        return REDIS_KEY_PREFIX + type.name() + ":" + id;
    }

    private void putL2Cache(NameType type, String id, String value) {
        if (redisService == null) {
            return;
        }
        try {
            redisService.set(redisCacheKey(type, id), value, properties.getRedisCacheTtl());
        } catch (Exception e) {
            log.warn("NameAssembler L2 cache write failed: type={}, id={}", type, id);
        }
    }
}
