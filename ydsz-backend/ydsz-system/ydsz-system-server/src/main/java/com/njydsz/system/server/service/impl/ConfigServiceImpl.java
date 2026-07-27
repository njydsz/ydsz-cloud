package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.domain.service.impl.AbstractCrudService;
import com.njydsz.common.domain.specification.Specification;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.jdbc.specification.MyBatisSpecification;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统配置 Service 实现。
 *
 * <p>基于 {@link AbstractCrudService} 复用通用 CRUD 能力，
 * 通过生命周期钩子集成 Redis 缓存、Micrometer 指标、缓存穿透防护和配置变更事件。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl
        extends AbstractCrudService<ConfigDO, ConfigDTO, ConfigVO, ConfigPageQuery, String>
        implements ConfigService {

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

    @Override
    protected ConfigRepository getRepository() {
        return configRepository;
    }

    @Override
    protected String getId(ConfigDTO dto) {
        return dto != null ? dto.getId() : null;
    }

    @Override
    protected ConfigVO toVO(ConfigDO entity) {
        if (entity == null) {
            return null;
        }
        ConfigVO vo = new ConfigVO();
        vo.setId(entity.getId());
        vo.setConfigGroup(entity.getConfigGroup());
        vo.setConfigKey(entity.getConfigKey());
        vo.setConfigValue(entity.getConfigValue());
        vo.setValueType(entity.getValueType());
        vo.setDefaultValue(entity.getDefaultValue());
        vo.setDescription(entity.getDescription());
        vo.setIsPublic(entity.getIsPublic());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    @Override
    protected ConfigDO toEntity(ConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        ConfigDO entity = new ConfigDO();
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

    @Override
    protected Specification<ConfigDO> getPageSpecification(ConfigPageQuery query) {
        return (MyBatisSpecification<ConfigDO>) wrapper -> {
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
        };
    }

    @Override
    protected void doBeforeSave(ConfigDTO dto, ConfigDO entity) {
        validateValueType(entity.getValueType());
        checkDuplicateKey(entity);
    }

    @Override
    protected void doBeforeUpdate(ConfigDTO dto, ConfigDO entity) {
        validateValueType(entity.getValueType());
    }

    @Override
    protected void doAfterSave(ConfigDO saved, boolean isNew) {
        evictCache(saved.getConfigKey(), saved.getConfigGroup());
    }

    @Override
    protected void doAfterUpdate(ConfigDO saved, boolean updated) {
        if (updated) {
            evictCache(saved.getConfigKey(), saved.getConfigGroup());
        }
    }

    @Override
    protected void doAfterDelete(String id, boolean removed) {
        if (!removed) {
            return;
        }
        ConfigDO entity = configRepository.getConfigMapper().selectById(id);
        if (entity != null) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
        }
    }

    @Override
    protected void doAfterBatchDelete(Iterable<String> ids, List<Boolean> result) {
        if (ids == null) {
            return;
        }
        ConfigMapper mapper = configRepository.getConfigMapper();
        for (String id : ids) {
            ConfigDO entity = mapper.selectById(id);
            if (entity != null) {
                evictCache(entity.getConfigKey(), entity.getConfigGroup());
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存（含空值哨兵防穿透），未命中时查 DB 并回写缓存。
     *
     * @param configKey 配置键
     * @return 配置值字符串，不存在时返回 null
     */
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
            ConfigDO config = configRepository.getConfigMapper().selectByConfigKey(configKey);
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

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存，未命中时查 DB 并回写缓存。仅返回启用状态的配置。
     *
     * @param configGroup 配置组名
     * @return 该组下所有启用配置列表（按 sortOrder 升序）
     */
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
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
        wrapper.eq("config_group", configGroup).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = configRepository.getConfigMapper().selectList(wrapper).stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        redisService.set(cacheKey, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存，未命中时查 DB 并回写缓存。
     * 仅返回 is_public=1 且 status=ENABLED 的配置。
     *
     * @return 公开配置列表（按 sortOrder 升序）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<ConfigVO> listPublicConfigs() {
        String cached = redisService.get(CACHE_PUBLIC_KEY, String.class);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
        wrapper.eq("is_public", 1).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = configRepository.getConfigMapper().selectList(wrapper).stream()
                .map(this::toVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        redisService.set(CACHE_PUBLIC_KEY, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    /**
     * 校验值类型是否合法，委托给 {@link ConfigValueType#validate(String)}。
     *
     * @param valueType 值类型（STRING/NUMBER/BOOLEAN/JSON）
     */
    private void validateValueType(String valueType) {
        ConfigValueType.validate(valueType);
    }

    /**
     * 唯一性校验：(configGroup, configKey) 组合不能重复。
     *
     * @param entity 配置实体
     */
    private void checkDuplicateKey(ConfigDO entity) {
        ConfigMapper mapper = configRepository.getConfigMapper();
        QueryWrapper<ConfigDO> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("config_group", entity.getConfigGroup())
                .eq("config_key", entity.getConfigKey());
        if (mapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException(
                    "配置键已存在: " + entity.getConfigGroup() + "/" + entity.getConfigKey());
        }
    }

    /**
     * 清除指定配置相关的所有缓存（单值缓存 + 组缓存 + 公开配置缓存），
     * 并发布 CONFIG_CHANGED 事件通知其他模块刷新本地缓存。
     *
     * @param configKey   配置键（可为 null）
     * @param configGroup 配置组名（可为 null）
     */
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

    /**
     * 获取配置缓存 TTL。
     *
     * @return 缓存 TTL
     */
    private Duration getCacheTtl() {
        int minutes = properties.getConfig().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }
}
