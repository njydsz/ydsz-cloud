package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统配置 Service 实现。
 *
 * <p>集成 Redis 缓存（TTL 5 分钟）、Micrometer 指标、值类型校验。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private static final String CACHE_KEY_PREFIX = "system:config:value:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ConfigMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final SystemMetrics metrics;

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
                metrics.recordConfigCacheHit();
                return cached;
            }
            metrics.recordConfigCacheMiss();
            ConfigDO config = mapper.selectByConfigKey(configKey);
            if (config != null) {
                redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(), CACHE_TTL);
                return config.getConfigValue();
            }
            return null;
        } finally {
            metrics.recordConfigRead(System.nanoTime() - start);
        }
    }

    @Override
    public IPage<ConfigDO> page(int pageNum, int pageSize) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), null);
    }

    @Override
    public List<ConfigDO> list() {
        return mapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(ConfigDTO dto) {
        validateValueType(dto.getValueType());
        ConfigDO entity = toEntity(dto);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ConfigDTO dto) {
        validateValueType(dto.getValueType());
        ConfigDO entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result) {
            evictCache(entity.getConfigKey());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        ConfigDO entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null) {
            evictCache(entity.getConfigKey());
        }
        return result;
    }

    private void evictCache(String configKey) {
        if (configKey != null) {
            redisTemplate.delete(CACHE_KEY_PREFIX + configKey);
        }
    }

    private void validateValueType(String valueType) {
        if (valueType != null && !valueType.equals("STRING") && !valueType.equals("NUMBER")
                && !valueType.equals("BOOLEAN") && !valueType.equals("JSON")) {
            throw new IllegalArgumentException("无效的值类型: " + valueType + "，支持: STRING/NUMBER/BOOLEAN/JSON");
        }
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
