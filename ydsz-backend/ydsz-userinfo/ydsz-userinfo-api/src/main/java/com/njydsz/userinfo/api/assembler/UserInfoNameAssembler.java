package com.njydsz.userinfo.api.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameAssemblerProperties;
import com.njydsz.common.feign.assembler.NameType;
import com.njydsz.userinfo.api.client.OrgQueryClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 ydsz-userinfo 服务的 {@link NameAssembler} 默认实现。
 *
 * <p><b>核心特性</b>：
 * <ul>
 *   <li><b>Feign 调用</b>：通过 {@link OrgQueryClient} 的 5 个 batch-names 端点
 *       （user / dept / role / post / company）批量解析 ID → 名称。</li>
 *   <li><b>本地缓存</b>：使用 {@link ConcurrentHashMap} + TTL（默认 5 分钟），
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
@RequiredArgsConstructor
public class UserInfoNameAssembler implements NameAssembler {

    private final OrgQueryClient orgQueryClient;
    private final NameAssemblerProperties properties;

    /** 缓存条目：name + 过期时间戳（毫秒） */
    private static final class CacheEntry {
        final String name;
        final long expireAt;

        CacheEntry(String name, long expireAt) {
            this.name = name;
            this.expireAt = expireAt;
        }

        boolean isExpired(long now) {
            return now >= expireAt;
        }
    }

    /** 缓存 key = type.name() + ":" + id；value = CacheEntry */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

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

        // 回填缓存
        long now = System.currentTimeMillis();
        long expireAt = now + properties.getCacheTtl().toMillis();
        for (Map.Entry<String, String> entry : result.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                cache.put(cacheKey(type, entry.getKey()), new CacheEntry(entry.getValue(), expireAt));
            }
        }
        return result;
    }

    @Override
    public String resolveName(NameType type, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = cacheKey(type, id);
        long now = System.currentTimeMillis();

        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired(now)) {
            return cached.name;
        }

        // 缓存未命中或已过期：单次 Feign 调用（仅查询单个 ID）
        Map<String, String> result = batchResolveNames(type, List.of(id));
        String name = result.get(id);
        if (name != null && !name.isBlank()) {
            return name;
        }
        // 调用失败或未命中：从缓存中删除可能过期的条目
        cache.remove(key);
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
}
