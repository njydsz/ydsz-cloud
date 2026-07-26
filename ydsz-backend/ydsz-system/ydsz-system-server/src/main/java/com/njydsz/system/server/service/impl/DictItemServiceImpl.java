package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.entity.DictItemDO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.DictVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典项 Service 实现。
 *
 * <p>集成 Redis 缓存（TTL 可配置）、Micrometer 指标、缓存穿透防护（空值哨兵）、
 * 字典版本快照（写操作自动记录变更历史）。
 *
 * <p>缓存键：
 * <ul>
 *   <li>{@code system:dict:item:{typeCode}:{itemCode}} — 单个字典项</li>
 *   <li>{@code system:dict:list:{typeCode}} — 字典项列表</li>
 * </ul>
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    private static final String CACHE_ITEM_PREFIX = "system:dict:item:";
    private static final String CACHE_LIST_PREFIX = "system:dict:list:";
    private static final String NULL_SENTINEL = "__NULL__";
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(2);

    private final DictItemMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final SystemMetrics metrics;
    private final SystemProperties properties;
    private final DictVersionService dictVersionService;

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
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordDictCacheHit();
                    return null;
                }
                metrics.recordDictCacheHit();
                return YdszJson.toObject(cached, DictItemVO.class);
            }
            metrics.recordDictCacheMiss();
            DictItemDO entity = mapper.selectByTypeAndCode(typeCode, itemCode);
            DictItemVO vo = toVO(entity);
            Duration ttl = getCacheTtl();
            if (vo != null) {
                redisTemplate.opsForValue().set(cacheKey, YdszJson.toJson(vo), ttl);
            } else {
                // 缓存空值防穿透，短 TTL
                redisTemplate.opsForValue().set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
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
                metrics.recordDictCacheHit();
                return YdszJson.parseArray(cached, DictItemVO.class);
            }
            metrics.recordDictCacheMiss();
            List<DictItemDO> entities = mapper.listEnabledByTypeCode(typeCode);
            List<DictItemVO> vos = entities.stream().map(this::toVO).collect(Collectors.toList());
            redisTemplate.opsForValue().set(cacheKey, YdszJson.toJson(vos), getCacheTtl());
            return vos;
        } finally {
            metrics.recordDictQuery(System.nanoTime() - start);
        }
    }

    @Override
    public List<DictItemVO> listChildren(String parentId) {
        QueryWrapper<DictItemDO> wrapper = new QueryWrapper<>();
        wrapper.eq("parent_id", parentId).orderByAsc("sort_order");
        return mapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public IPage<DictItemVO> page(int pageNum, int pageSize) {
        IPage<DictItemDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), null);
        List<DictItemVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        Page<DictItemVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public List<DictItemVO> list() {
        return mapper.selectList(null).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictItemDTO dto) {
        DictItemDO entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getTypeCode());
        dictVersionService.createVersion(entity.getTypeCode(),
                "v" + System.currentTimeMillis(), "新增字典项: " + entity.getItemCode());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictItemDTO dto) {
        DictItemDO entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result) {
            evictCache(entity.getTypeCode());
            dictVersionService.createVersion(entity.getTypeCode(),
                    "v" + System.currentTimeMillis(), "更新字典项: " + entity.getItemCode());
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
            dictVersionService.createVersion(entity.getTypeCode(),
                    "v" + System.currentTimeMillis(), "删除字典项: " + entity.getItemCode());
        }
        return result;
    }

    private void evictCache(String typeCode) {
        if (typeCode == null) {
            return;
        }
        redisTemplate.delete(CACHE_LIST_PREFIX + typeCode);
        // 使用 SCAN 替代 KEYS，避免 Redis 阻塞
        String pattern = CACHE_ITEM_PREFIX + typeCode + ":*";
        Set<String> keys = scanKeys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 使用 SCAN 迭代收集匹配的 key，避免 KEYS 命令阻塞 Redis。
     *
     * @param pattern key 匹配模式
     * @return 匹配的 key 集合
     */
    private Set<String> scanKeys(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            Cursor<byte[]> cursor = connection.keyCommands().scan(options);
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
            cursor.close();
            return null;
        });
        return keys;
    }

    private Duration getCacheTtl() {
        int minutes = properties.getDict().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 10);
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
