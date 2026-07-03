package com.njydsz.pmis.iam.service.impl;

import com.njydsz.pmis.iam.entity.DictItemDO;
import com.njydsz.pmis.iam.entity.DictTypeDO;
import com.njydsz.pmis.iam.mapper.DictItemMapper;
import com.njydsz.pmis.iam.mapper.DictTypeMapper;
import com.njydsz.pmis.iam.service.DictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典服务实现
 *
 * <p>P2-6 改进：使用 Spring Cache 声明式缓存（@Cacheable/@CachePut）替代手动 StringRedisTemplate，
 * 代码更简洁、可观测性更强。缓存名称 {@value #CACHE_NAME}，TTL 由 Redisson Spring Cache 配置统一管理
 * （见 application.yml: spring.cache.redis.time-to-live，默认 30 分钟）。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>{@link #listItems(String)} — 读：命中缓存直接返回，未命中查库后写入缓存</li>
 *   <li>{@link #refreshCache(String)} — 写：{@code @CachePut} 主动刷新缓存（不删除，直接覆盖）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    /** 字典项缓存名称 */
    public static final String CACHE_NAME = "dict:items";

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    public List<DictTypeDO> listAllTypes() {
        return dictTypeMapper.selectList(null);
    }

    /**
     * 按 typeCode 查询字典项（带 Redis 缓存）
     *
     * <p>缓存 key = typeCode，命中时直接返回缓存值；未命中时执行方法体并将返回值写入缓存。
     * 由于 Redisson Spring Cache 默认配置了 TTL，缓存会在到期后自动失效。
     */
    @Override
    @Cacheable(value = CACHE_NAME, key = "#typeCode", unless = "#result == null || #result.isEmpty()")
    public List<DictItemDO> listItems(String typeCode) {
        return dictItemMapper.selectByTypeCode(typeCode);
    }

    /**
     * 主动刷新字典缓存
     *
     * <p>使用 {@code @CachePut} 而非 {@code @CacheEvict}：
     * 主动查库并覆盖缓存值，避免刷新后第一个请求承受回源开销。
     *
     * @param typeCode 字典类型编码
     */
    @Override
    @CachePut(value = CACHE_NAME, key = "#typeCode")
    public List<DictItemDO> refreshCache(String typeCode) {
        List<DictItemDO> items = dictItemMapper.selectByTypeCode(typeCode);
        log.info("[Dict] 刷新字典缓存 typeCode={} count={}", typeCode, items.size());
        return items;
    }
}