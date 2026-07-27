package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.auth.annotation.DataScope;

/**
 * 系统配置 Service 实现。
 *
 * <p>集成 Redis 缓存（TTL 可配置）、Micrometer 指标、值类型校验、缓存穿透防护。
 * 支持按 key 查询、按 group 批量查询、公开配置查询。
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

    /** 系统配置 Mapper */
    private final ConfigMapper mapper;
    /** Redis 缓存服务 */
    private final RedisService redisService;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;
    /** 系统配置属性 */
    private final SystemProperties properties;
    /** Outbox 服务（可选依赖，用于发布配置变更事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigVO getById(String id) {
        ConfigDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>优先查 Redis 缓存（含空值哨兵防穿透），未命中时查 DB 并回写缓存。
     * 同时记录缓存命中/未命中指标和读取耗时指标。
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
            ConfigDO config = mapper.selectByConfigKey(configKey);
            if (config != null) {
                redisService.set(cacheKey, config.getConfigValue(), getCacheTtl());
                return config.getConfigValue();
            }
            // 缓存空值防穿透
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
        List<ConfigVO> vos = mapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
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
        List<ConfigVO> vos = mapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
        redisService.set(CACHE_PUBLIC_KEY, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    /**
     * {@inheritDoc}
     * <p>支持按 configGroup 精确匹配、configKey 模糊匹配、status 精确匹配过滤。
     *
     * @param pageNum     页码（1-based）
     * @param pageSize    每页条数
     * @param configGroup 配置组名（可选过滤条件）
     * @param configKey   配置键（可选，模糊匹配）
     * @param status      状态（可选过滤条件）
     * @return 分页结果
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public IPage<ConfigVO> page(int pageNum, int pageSize, String configGroup, String configKey, String status) {
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
        if (configGroup != null && !configGroup.isBlank()) {
            wrapper.eq("config_group", configGroup);
        }
        if (configKey != null && !configKey.isBlank()) {
            wrapper.like("config_key", configKey);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<ConfigDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ConfigVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        Page<ConfigVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部配置列表（不区分状态）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<ConfigVO> list() {
        return mapper.selectList(null).stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行值类型校验和唯一性校验（configGroup + configKey 组合不能重复），
     * 插入后清除相关缓存。
     *
     * @param dto 配置数据
     * @return 新创建的配置 ID
     * @throws IllegalArgumentException 当值类型无效或配置键已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ConfigDTO dto) {
        validateValueType(dto.getValueType());
        // 唯一性校验：(configGroup, configKey) 组合不能重复
        QueryWrapper<ConfigDO> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("config_group", dto.getConfigGroup())
                .eq("config_key", dto.getConfigKey());
        if (mapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException(
                    "配置键已存在: " + dto.getConfigGroup() + "/" + dto.getConfigKey());
        }
        ConfigDO entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getConfigKey(), entity.getConfigGroup());
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>执行值类型校验，更新成功后清除相关缓存。
     *
     * @param dto 配置数据（需包含 id）
     * @return true 表示更新成功
     * @throws IllegalArgumentException 当值类型无效时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ConfigDTO dto) {
        validateValueType(dto.getValueType());
        ConfigDO entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>删除成功后清除相关缓存。
     *
     * @param id 配置 ID
     * @return true 表示删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        ConfigDO entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null) {
            evictCache(entity.getConfigKey(), entity.getConfigGroup());
        }
        return result;
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

        // 发布配置变更事件（OutboxService 可选依赖，不存在时安全降级）
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
     * 获取配置缓存 TTL，从 {@link SystemProperties.Config#getCacheTtlMinutes()}读取，
     * 默认 5 分钟。
     *
     * @return 缓存 TTL
     */
    private Duration getCacheTtl() {
        int minutes = properties.getConfig().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }

    /**
     * 校验值类型是否合法，委托给 {@link ConfigValueType#validate(String)}。
     *
     * @param valueType 值类型（STRING/NUMBER/BOOLEAN/JSON）
     * @throws IllegalArgumentException 当值类型无效时抛出
     */
    private void validateValueType(String valueType) {
        ConfigValueType.validate(valueType);
    }

    /**
     * 将 DO 转换为 VO。
     *
     * @param entity 数据库实体
     * @return 视图对象，entity 为 null 时返回 null
     */
    private ConfigVO toVO(ConfigDO entity) {
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

    /**
     * 将 DTO 转换为 DO，status 为空时默认 ENABLED。
     *
     * @param dto 数据传输对象
     * @return 数据库实体
     */
    private ConfigDO toEntity(ConfigDTO dto) {
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
}