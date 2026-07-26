package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.entity.VariableDO;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.VariableService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统变量 Service 实现。
 *
 * <p>集成 Redis 缓存（TTL 可配置）、Micrometer 指标、缓存穿透防护，与 ConfigService 能力对齐。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

    private static final String CACHE_KEY_PREFIX = "system:variable:value:";
    private static final String NULL_SENTINEL = "__NULL__";
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(1);

    private final VariableMapper mapper;
    private final RedisService redisService;
    private final SystemProperties properties;
    private final SystemMetrics metrics;

    @Override
    public VariableVO getById(String id) {
        VariableDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public String getVariableValue(String variableKey) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_KEY_PREFIX + variableKey;
            String cached = redisService.get(cacheKey, String.class);
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordVariableCacheHit();
                    return null;
                }
                metrics.recordVariableCacheHit();
                return cached;
            }
            metrics.recordVariableCacheMiss();
            QueryWrapper<VariableDO> wrapper = new QueryWrapper<>();
            wrapper.eq("variable_key", variableKey).eq("status", "ENABLED");
            VariableDO entity = mapper.selectOne(wrapper);
            if (entity != null) {
                redisService.set(cacheKey, entity.getVariableValue(), getCacheTtl());
                return entity.getVariableValue();
            }
            redisService.set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        } finally {
            metrics.recordVariableRead(System.nanoTime() - start);
        }
    }

    @Override
    public IPage<VariableVO> page(int pageNum, int pageSize, String variableKey, String status) {
        QueryWrapper<VariableDO> wrapper = new QueryWrapper<>();
        if (variableKey != null && !variableKey.isBlank()) {
            wrapper.like("variable_key", variableKey);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<VariableDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<VariableVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        Page<VariableVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public List<VariableVO> list() {
        return mapper.selectList(null).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(VariableDTO dto) {
        VariableDO entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getVariableKey());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(VariableDTO dto) {
        VariableDO entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result && entity.getVariableKey() != null) {
            evictCache(entity.getVariableKey());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        VariableDO entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null && entity.getVariableKey() != null) {
            evictCache(entity.getVariableKey());
        }
        return result;
    }

    private void evictCache(String variableKey) {
        if (variableKey != null) {
            redisService.delete(CACHE_KEY_PREFIX + variableKey);
        }
    }

    private Duration getCacheTtl() {
        int minutes = properties.getVariable().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }

    private VariableVO toVO(VariableDO entity) {
        if (entity == null) {
            return null;
        }
        VariableVO vo = new VariableVO();
        vo.setId(entity.getId());
        vo.setVariableKey(entity.getVariableKey());
        vo.setVariableValue(entity.getVariableValue());
        vo.setValueType(entity.getValueType());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private VariableDO toEntity(VariableDTO dto) {
        VariableDO entity = new VariableDO();
        entity.setId(dto.getId());
        entity.setVariableKey(dto.getVariableKey());
        entity.setVariableValue(dto.getVariableValue());
        entity.setValueType(dto.getValueType());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
