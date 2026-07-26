package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.json.YdszJson;
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

    private static final String CACHE_KEY_PREFIX = "system:config:value:";
    private static final String CACHE_GROUP_PREFIX = "system:config:group:";
    private static final String CACHE_PUBLIC_KEY = "system:config:public";
    private static final String NULL_SENTINEL = "__NULL__";
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(1);

    private final ConfigMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final SystemMetrics metrics;
    private final SystemProperties properties;

    @Override
    public ConfigVO getById(String id) {
        ConfigDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public String getConfigValue(String configKey) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_KEY_PREFIX + configKey;
            String cached = redisTemplate.opsForValue().get(cacheKey);
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
                redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(), getCacheTtl());
                return config.getConfigValue();
            }
            // 缓存空值防穿透
            redisTemplate.opsForValue().set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        } finally {
            metrics.recordConfigRead(System.nanoTime() - start);
        }
    }

    @Override
    public List<ConfigVO> getConfigsByGroup(String configGroup) {
        String cacheKey = CACHE_GROUP_PREFIX + configGroup;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
        wrapper.eq("config_group", configGroup).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = mapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
        redisTemplate.opsForValue().set(cacheKey, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    @Override
    public List<ConfigVO> listPublicConfigs() {
        String cached = redisTemplate.opsForValue().get(CACHE_PUBLIC_KEY);
        if (cached != null) {
            metrics.recordConfigCacheHit();
            return YdszJson.parseArray(cached, ConfigVO.class);
        }
        metrics.recordConfigCacheMiss();
        QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
        wrapper.eq("is_public", 1).eq("status", "ENABLED").orderByAsc("sort_order");
        List<ConfigVO> vos = mapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
        redisTemplate.opsForValue().set(CACHE_PUBLIC_KEY, YdszJson.toJson(vos), getCacheTtl());
        return vos;
    }

    @Override
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

    @Override
    public List<ConfigVO> list() {
        return mapper.selectList(null).stream().map(this::toVO).collect(Collectors.toList());
    }

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

    private void evictCache(String configKey, String configGroup) {
        if (configKey != null) {
            redisTemplate.delete(CACHE_KEY_PREFIX + configKey);
        }
        if (configGroup != null) {
            redisTemplate.delete(CACHE_GROUP_PREFIX + configGroup);
        }
        redisTemplate.delete(CACHE_PUBLIC_KEY);
    }

    private Duration getCacheTtl() {
        int minutes = properties.getConfig().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }

    private void validateValueType(String valueType) {
        ConfigValueType.validate(valueType);
    }

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
