package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 系统配置 Service 实现。
 *
 * <p>集成 Redis 缓存、Micrometer 指标、缓存穿透防护和配置变更事件。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    /** 单个配置值缓存键前缀：system:config:value:{configKey} */
    private static final String CACHE_KEY_PREFIX = "system:config:value:";
    /** 配置组缓存键前缀：system:config:group:{configGroup} */
    private static final String CACHE_GROUP_PREFIX = "system:config:group:";
    /** 公开配置列表缓存键：system:config:public */
    private static final String CACHE_PUBLIC_KEY = "system:config:public";
    /** 空值哨兵，用于防缓存穿透 */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 空值哨兵 TTL（1 分钟） */
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(1);

    /** 系统配置仓储 */
    private final ConfigRepository configRepository;
    /** Redis 缓存服务 */
    private final RedisService redisService;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;
    /** 系统配置属性 */
    private final SystemProperties properties;
    /** Outbox 服务（可选依赖，用于发布配置变更事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;
    private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

    // ============================== CRUD ==============================

    @Override
    public PageResult<ConfigVO> page(ConfigPageQuery query) {
        QueryWrapper<Config> wrapper = buildQueryWrapper(query);
        Page<Config> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
        IPage<Config> result = configRepository.getConfigMapper().selectPage(mpPage, wrapper);
        List<ConfigVO> vos = result.getRecords().stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PageResult.of(vos, result.getTotal(), query.getEffectivePageNum(), query.getEffectivePageSize());
    }

    @Override
    public ConfigVO getById(String id) {
        Config entity = configRepository.getConfigMapper().selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ConfigDTO dto) {
        Config entity = toEntity(dto);
        validateValueType(entity.getValueType());
        checkDuplicateKey(entity);
        configRepository.getConfigMapper().insert(entity);
        evictCache(entity.getConfigKey(), entity.getConfigGroup());
        indexUpsert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ConfigDTO dto) {
        Config entity = toEntity(dto);
        validateValueType(entity.getValueType());
        boolean updated = configRepository.getConfigMapper().updateById(entity) > 0;
        if (updated) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
            indexUpsert(entity);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Config entity = configRepository.getConfigMapper().selectById(id);
        boolean removed = configRepository.getConfigMapper().deleteById(id) > 0;
        if (removed && entity != null) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
            indexDelete(id);
        }
        return removed;
    }

    private void indexUpsert(Config entity) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexUpsert("config", entity);
        }
    }

    private void indexDelete(String id) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexDelete("config", id);
        }
    }

    // ============================== 业务查询 ==============================

    @Override
    public String getConfigValue(String configKey) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_KEY_PREFIX + configKey;
            String cached = redisService.get(cacheKey, String.class);
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordConfigCacheHit();
                    return null;
                }
                metrics.recordConfigCacheHit();
                return cached;
            }
            metrics.recordConfigCacheMiss();
            Config config = configRepository.getConfigMapper().selectByConfigKey(configKey);
            if (config != null) {
                redisService.set(cacheKey, config.getConfigValue(), getCacheTtl());
                return config.getConfigValue();
            }
            redisService.set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        } finally {
            metrics.recordConfigRead(System.nanoTime() - start);
        }
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<ConfigVO> getConfigsByGroup(String configGroup) {
        String cacheKey = CACHE_GROUP_PREFIX + configGroup;
        String cached = redisService.get(cacheKey, String.class);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("config_group", configGroup).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = configRepository.getConfigMapper().selectList(wrapper).stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        redisService.set(cacheKey, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<ConfigVO> listPublicConfigs() {
        String cached = redisService.get(CACHE_PUBLIC_KEY, String.class);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        wrapper.eq("is_public", 1).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = configRepository.getConfigMapper().selectList(wrapper).stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        redisService.set(CACHE_PUBLIC_KEY, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    // ============================== 私有方法 ==============================

    private QueryWrapper<Config> buildQueryWrapper(ConfigPageQuery query) {
        QueryWrapper<Config> wrapper = new QueryWrapper<>();
        if (query.getConfigGroup() != null && !query.getConfigGroup().isBlank()) {
            wrapper.eq("config_group", query.getConfigGroup());
        }
        if (query.getConfigKey() != null && !query.getConfigKey().isBlank()) {
            wrapper.like("config_key", query.getConfigKey());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq("status", query.getStatus());
        }
        wrapper.orderByDesc("created_at");
        return wrapper;
    private Config toEntity(ConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        Config entity = new Config();
        entity.setId(dto.getId());
        entity.setConfigGroup(dto.getConfigGroup());
        entity.setConfigKey(dto.getConfigKey());
        entity.setConfigValue(dto.getConfigValue());
        entity.setValueType(dto.getValueType());
        entity.setDefaultValue(dto.getDefaultValue());
        entity.setDescription(dto.getDescription());
        entity.setIsPublic(dto.getIsPublic());
        entity.setSortOrder(dto.getSortOrder());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }

    private void validateValueType(String valueType) {
        ConfigValueType.validate(valueType);
    }

    private void checkDuplicateKey(Config entity) {
        QueryWrapper<Config> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("config_group", entity.getConfigGroup())
                .eq("config_key", entity.getConfigKey());
        if (configRepository.getConfigMapper().selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException(
                    "配置键已存在: " + entity.getConfigGroup() + "/" + entity.getConfigKey());
        }
    }

    private void evictCache(String configKey, String configGroup) {
        if (configKey != null) {
            redisService.delete(CACHE_KEY_PREFIX + configKey);
        }
        if (configGroup != null) {
            redisService.delete(CACHE_GROUP_PREFIX + configGroup);
        }
        redisService.delete(CACHE_PUBLIC_KEY);

        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService != null) {
            try {
                Map<String, String> payload = new HashMap<>();
                if (configKey != null) {
                    payload.put("configKey", configKey);
                }
                if (configGroup != null) {
                    payload.put("configGroup", configGroup);
                }
                outboxService.appendToOutbox(
                        "Config", null, StandardEventTypes.CONFIG_CHANGED,
                        YdszJson.toJson(payload));
            } catch (Exception e) {
                log.warn("Failed to publish CONFIG_CHANGED event: error={}", e.getMessage());
            }
        }
    }

    private Duration getCacheTtl() {
        int minutes = properties.getConfig().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }
}
