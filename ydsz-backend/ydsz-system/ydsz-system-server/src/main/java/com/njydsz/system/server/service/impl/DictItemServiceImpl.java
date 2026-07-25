package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.entity.DictItemDO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.common.json.YdszJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典项 Service 实现。
 *
 * <p>集成 Redis 缓存（TTL 10 分钟）、Micrometer 指标。缓存键：{@code system:dict:item:{typeCode}:{itemCode}}
 * 和 {@code system:dict:list:{typeCode}}。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    private static final String CACHE_ITEM_PREFIX = "system:dict:item:";
    private static final String CACHE_LIST_PREFIX = "system:dict:list:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final DictItemMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final SystemMetrics metrics;

    @Override
    public DictItemVO getById(String id) {
        DictItemDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public DictItemVO getByTypeAndCode(String typeCode, String itemCode) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_ITEM_PREFIX + typeCode + ":" + itemCode;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                metrics.recordConfigCacheHit();
                return YdszJson.toObject(cached, DictItemVO.class);
            }
            metrics.recordConfigCacheMiss();
            DictItemDO entity = mapper.selectByTypeAndCode(typeCode, itemCode);
            DictItemVO vo = toVO(entity);
            if (vo != null) {
                redisTemplate.opsForValue().set(cacheKey, YdszJson.toJson(vo), CACHE_TTL);
            }
            return vo;
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    @Override
    public List<DictItemVO> listEnabledByTypeCode(String typeCode) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_LIST_PREFIX + typeCode;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                metrics.recordConfigCacheHit();
                return YdszJson.parseArray(cached, DictItemVO.class);
            }
            metrics.recordConfigCacheMiss();
            List<DictItemDO> entities = mapper.listEnabledByTypeCode(typeCode);
            List<DictItemVO> vos = entities.stream().map(this::toVO).collect(Collectors.toList());
            redisTemplate.opsForValue().set(cacheKey, YdszJson.toJson(vos), CACHE_TTL);
            return vos;
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    @Override
    public IPage<DictItemDO> page(int pageNum, int pageSize) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), null);
    }

    @Override
    public List<DictItemDO> list() {
        return mapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictItemDTO dto) {
        DictItemDO entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getTypeCode());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictItemDTO dto) {
        DictItemDO entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result) {
            evictCache(entity.getTypeCode());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        DictItemDO entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null) {
            evictCache(entity.getTypeCode());
        }
        return result;
    }

    private void evictCache(String typeCode) {
        if (typeCode != null) {
            redisTemplate.delete(CACHE_LIST_PREFIX + typeCode);
            redisTemplate.delete(redisTemplate.keys(CACHE_ITEM_PREFIX + typeCode + ":*"));
        }
    }

    private DictItemVO toVO(DictItemDO entity) {
        if (entity == null) {
            return null;
        }
        DictItemVO vo = new DictItemVO();
        vo.setId(entity.getId());
        vo.setTypeCode(entity.getTypeCode());
        vo.setItemCode(entity.getItemCode());
        vo.setItemValue(entity.getItemValue());
        vo.setSortOrder(entity.getSortOrder());
        vo.setParentId(entity.getParentId());
        vo.setDescription(entity.getDescription());
        vo.setExtJson(entity.getExtJson());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private DictItemDO toEntity(DictItemDTO dto) {
        DictItemDO entity = new DictItemDO();
        entity.setId(dto.getId());
        entity.setTypeCode(dto.getTypeCode());
        entity.setItemCode(dto.getItemCode());
        entity.setItemValue(dto.getItemValue());
        entity.setSortOrder(dto.getSortOrder());
        entity.setParentId(dto.getParentId());
        entity.setDescription(dto.getDescription());
        entity.setExtJson(dto.getExtJson());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
