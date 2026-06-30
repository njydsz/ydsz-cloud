package com.njydsz.pmis.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.config.dto.ConfigFormDTO;
import com.njydsz.pmis.config.dto.ConfigQueryDTO;
import com.njydsz.pmis.config.entity.ConfigDO;
import com.njydsz.pmis.config.mapper.ConfigMapper;
import com.njydsz.pmis.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置中心服务实现
 *
 * <p>使用 Redis 缓存 10 分钟，变更后主动失效缓存。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private static final String CACHE_PREFIX = "pmis:cfg:";
    private static final String CACHE_GROUP_PREFIX = "pmis:cfg:group:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Page<ConfigDO> page(ConfigQueryDTO query) {
        Page<ConfigDO> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<ConfigDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.and(qw -> qw.like(ConfigDO::getConfigKey, query.getKeyword())
                    .or().like(ConfigDO::getConfigValue, query.getKeyword())
                    .or().like(ConfigDO::getDescription, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getConfigGroup())) {
            w.eq(ConfigDO::getConfigGroup, query.getConfigGroup());
        }
        if (StringUtils.hasText(query.getStatus())) {
            w.eq(ConfigDO::getStatus, query.getStatus());
        }
        if (query.getIsPublic() != null) {
            w.eq(ConfigDO::getIsPublic, query.getIsPublic());
        }
        w.orderByAsc(ConfigDO::getConfigGroup).orderByAsc(ConfigDO::getSortOrder);
        return configMapper.selectPage(page, w);
    }

    @Override
    public ConfigDO getById(Long id) {
        ConfigDO c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "配置不存在");
        }
        return c;
    }

    @Override
    public ConfigDO getByKey(String group, String key) {
        String cacheKey = CACHE_PREFIX + group + ":" + key;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, ConfigDO.class);
        }
        ConfigDO c = configMapper.selectByGroupAndKey(group, key);
        if (c != null) {
            redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(c), CACHE_TTL);
        }
        return c;
    }

    @Override
    public Map<String, String> getGroupAsMap(String group) {
        String cacheKey = CACHE_GROUP_PREFIX + group;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
        }
        List<ConfigDO> list = configMapper.selectByGroup(group);
        Map<String, String> map = new HashMap<>();
        for (ConfigDO c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(map), CACHE_TTL);
        return map;
    }

    @Override
    public List<ConfigDO> listPublic() {
        return configMapper.selectPublic();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ConfigFormDTO dto) {
        ConfigDO exists = configMapper.selectByGroupAndKey(dto.getConfigGroup(), dto.getConfigKey());
        if (exists != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "配置已存在: " + dto.getConfigGroup() + "." + dto.getConfigKey());
        }
        ConfigDO entity = new ConfigDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getValueType() == null) entity.setValueType("STRING");
        if (entity.getIsPublic() == null) entity.setIsPublic(0);
        if (entity.getStatus() == null) entity.setStatus("ENABLED");
        configMapper.insert(entity);
        invalidateCache(dto.getConfigGroup());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ConfigFormDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "配置 ID 不能为空");
        }
        ConfigDO exists = configMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "配置不存在");
        }
        ConfigDO entity = new ConfigDO();
        BeanUtils.copyProperties(dto, entity);
        configMapper.updateById(entity);
        invalidateCache(dto.getConfigGroup());
        log.info("[Config] 更新配置 {}.{} = {}", dto.getConfigGroup(), dto.getConfigKey(), dto.getConfigValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ConfigDO c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "配置不存在");
        }
        configMapper.deleteById(id);
        invalidateCache(c.getConfigGroup());
    }

    @Override
    public void refreshCache() {
        // 简化：删除所有 pmis:cfg:* 前缀的 key
        java.util.Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys(CACHE_GROUP_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("[Config] 已刷新配置缓存");
    }

    @Override
    public <T> T parseValue(ConfigDO config, Class<T> type) {
        if (config == null) {
            return null;
        }
        String value = StringUtils.hasText(config.getConfigValue()) ? config.getConfigValue() : config.getDefaultValue();
        if (value == null) {
            return null;
        }
        // 数值/布尔类型：按目标类型优先解析，避免被 valueType=STRING 阻断
        if (type == Long.class || type == Integer.class || type == Double.class || type == Short.class || type == Byte.class || type == Float.class) {
            if (type == Double.class || type == Float.class) {
                return type.cast(Double.parseDouble(value));
            }
            return type.cast(Long.parseLong(value));
        }
        if (type == Boolean.class) {
            return type.cast(Boolean.parseBoolean(value));
        }
        String vt = config.getValueType() == null ? "STRING" : config.getValueType().toUpperCase();
        Object parsed;
        switch (vt) {
            case "NUMBER" -> parsed = Long.parseLong(value);
            case "BOOLEAN" -> parsed = Boolean.parseBoolean(value);
            case "JSON" -> parsed = JSON.parseObject(value, type);
            default -> parsed = value;
        }
        if (type == String.class) {
            return type.cast(String.valueOf(parsed));
        }
        return type.cast(parsed);
    }

    private void invalidateCache(String group) {
        if (!StringUtils.hasText(group)) return;
        redisTemplate.delete(CACHE_GROUP_PREFIX + group);
    }
}
