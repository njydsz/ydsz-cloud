package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.user.entity.DictItemDO;
import com.njydsz.pmis.user.entity.DictTypeDO;
import com.njydsz.pmis.user.mapper.DictItemMapper;
import com.njydsz.pmis.user.mapper.DictTypeMapper;
import com.njydsz.pmis.user.service.DictService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 字典服务实现
 *
 * <p>字典项数据量小、读取频繁，使用 Redis 缓存（TTL 30 分钟）。
 * 字典变更时需手动调用 refreshCache 刷新（或由管理后台调用）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final String CACHE_PREFIX = "pmis:dict:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<DictTypeDO> listAllTypes() {
        return dictTypeMapper.selectList(null);
    }

    @Override
    public List<DictItemDO> listItems(String typeCode) {
        String key = CACHE_PREFIX + typeCode;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return JSON.parseArray(cached, DictItemDO.class);
        }
        List<DictItemDO> items = dictItemMapper.selectByTypeCode(typeCode);
        redisTemplate.opsForValue().set(key, JSON.toJSONString(items), CACHE_TTL);
        return items;
    }

    @Override
    public void refreshCache(String typeCode) {
        String key = CACHE_PREFIX + typeCode;
        List<DictItemDO> items = dictItemMapper.selectByTypeCode(typeCode);
        redisTemplate.opsForValue().set(key, JSON.toJSONString(items), CACHE_TTL);
        log.info("[Dict] 刷新字典缓存 typeCode={} count={}", typeCode, items.size());
    }
}
