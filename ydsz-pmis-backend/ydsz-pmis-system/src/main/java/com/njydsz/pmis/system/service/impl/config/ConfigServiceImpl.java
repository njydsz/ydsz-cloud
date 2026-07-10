package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.system.dto.config.ConfigFormDTO;
import com.njydsz.pmis.system.dto.config.ConfigQueryDTO;
import com.njydsz.pmis.system.entity.config.ConfigDO;
import com.njydsz.pmis.system.mapper.config.ConfigMapper;
import com.njydsz.pmis.system.service.config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** Spring Cache 配置缓存名称 */
    public static final String CACHE_NAME = "config";

    /** 单条配置缓存 Key 前缀 */
    private static final String CACHE_PREFIX = "pmis:cfg:";
    /** 分组配置缓存 Key 前缀 */
    private static final String CACHE_GROUP_PREFIX = "pmis:cfg:group:";
    /** 缓存有效期 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** 配置 Mapper */
    private final ConfigMapper configMapper;
    /** Redis 操作模板（配置缓存） */
    private final StringRedisTemplate redisTemplate;

    /**
     * 分页查询配置
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ConfigDO> page(ConfigQueryDTO query) {
        Page<ConfigDO> page = new Page<>(query.getPage(), Math.min(query.getSize(), PageQuery.MAX_SIZE));
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

    /**
     * 按 ID 查配置
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws BizException 当配置不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public ConfigDO getById(String id) {
        ConfigDO c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "配置不存在");
        }
        return c;
    }

    /**
     * 按 group + key 查配置（优先读缓存）
     *
     * @param group 配置分组
     * @param key   配置键
     * @return 配置实体，无则返回 null
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#group + ':' + #key", unless = "#result == null")
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

    /**
     * 获取某组全部配置（key → value 映射，优先读缓存）
     *
     * @param group 配置分组
     * @return key-value 映射
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getGroupAsMap(String group) {
        String cacheKey = CACHE_GROUP_PREFIX + group;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, new TypeReference<Map<String, String>>() {});
        }
        List<ConfigDO> list = configMapper.selectByGroup(group);
        Map<String, String> map = new HashMap<>();
        for (ConfigDO c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(map), CACHE_TTL);
        return map;
    }

    /**
     * 查询全部公开配置
     *
     * @return 公开配置列表
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'public'", unless = "#result == null || #result.isEmpty()")
    public List<ConfigDO> listPublic() {
        return configMapper.selectPublic();
    }

    /**
     * 创建配置
     *
     * @param dto 配置表单
     * @return 配置 ID
     * @throws BizException 当 valueType 非法、配置已存在或值格式不匹配时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public String create(ConfigFormDTO dto) {
        if (dto.getValueType() == null
                || !Set.of("STRING", "NUMBER", "BOOLEAN", "JSON").contains(dto.getValueType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "valueType 必须是 STRING/NUMBER/BOOLEAN/JSON");
        }
        ConfigDO exists = configMapper.selectByGroupAndKey(dto.getConfigGroup(), dto.getConfigKey());
        if (exists != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "配置已存在: " + dto.getConfigGroup() + "." + dto.getConfigKey());
        }
        ConfigDO entity = new ConfigDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getValueType() == null) entity.setValueType("STRING");
        if (entity.getIsPublic() == null) entity.setIsPublic(0);
        if (entity.getStatus() == null) entity.setStatus("ENABLED");
        // 验证值类型与值格式是否一致
        validateValueFormat(entity);
        configMapper.insert(entity);
        invalidateCache(dto.getConfigGroup());
        return entity.getId();
    }

    /**
     * 更新配置
     *
     * @param dto 配置表单
     * @throws BizException 当 ID 为空、valueType 非法、配置不存在或值格式不匹配时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void update(ConfigFormDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "配置 ID 不能为空");
        }
        if (dto.getValueType() != null
                && !Set.of("STRING", "NUMBER", "BOOLEAN", "JSON").contains(dto.getValueType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "valueType 必须是 STRING/NUMBER/BOOLEAN/JSON");
        }
        ConfigDO exists = configMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "配置不存在");
        }
        ConfigDO entity = new ConfigDO();
        BeanUtils.copyProperties(dto, entity);
        validateValueFormat(entity);
        configMapper.updateById(entity);
        invalidateCache(dto.getConfigGroup());
        log.info("[Config] 更新配置 {}.{} = {}", dto.getConfigGroup(), dto.getConfigKey(), dto.getConfigValue());
    }

    /**
     * 验证 configValue 与 valueType 的格式匹配性
     *
     * @param entity 配置实体
     * @throws BizException 当值格式与类型不匹配时抛出
     */
    private void validateValueFormat(ConfigDO entity) {
        if (entity.getConfigValue() == null || entity.getValueType() == null) {
            return;
        }
        String v = entity.getConfigValue();
        switch (entity.getValueType().toUpperCase()) {
            case "NUMBER" -> {
                try {
                    new BigDecimal(v);
                } catch (NumberFormatException e) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "NUMBER 类型配置值必须是数字: " + v);
                }
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "BOOLEAN 类型配置值必须是 true/false: " + v);
                }
            }
            case "JSON" -> {
                try {
                    JSON.parse(v);
                } catch (Exception e) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "JSON 类型配置值格式不合法: " + v);
                }
            }
            default -> { /* STRING 任意通过 */ }
        }
    }

    /**
     * 删除配置
     *
     * @param id 配置 ID
     * @throws BizException 当配置不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void delete(String id) {
        ConfigDO c = configMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "配置不存在");
        }
        configMapper.deleteById(id);
        invalidateCache(c.getConfigGroup());
    }

    /**
     * 批量按 group 删除配置
     *
     * @param group 配置分组
     * @return 删除条数
     * @throws BizException 当分组为空时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int deleteByGroup(String group) {
        if (!StringUtils.hasText(group)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "配置分组不能为空");
        }
        int n = configMapper.deleteByGroup(group);
        if (n > 0) {
            invalidateCache(group);
            log.info("[Config] 按 group 批量删除配置: group={}, count={}", group, n);
        }
        return n;
    }

    /**
     * 批量按 group 启用/停用
     *
     * @param group  配置分组
     * @param status 目标状态（ENABLED/DISABLED）
     * @return 更新条数
     * @throws BizException 当分组或状态为空、状态值非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int updateStatusByGroup(String group, String status) {
        if (!StringUtils.hasText(group) || !StringUtils.hasText(status)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "分组和状态不能为空");
        }
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "状态值非法: " + status);
        }
        int n = configMapper.updateStatusByGroup(group, status);
        if (n > 0) {
            invalidateCache(group);
            log.info("[Config] 按 group 批量更新状态: group={}, status={}, count={}", group, status, n);
        }
        return n;
    }

    /**
     * 刷新缓存（删除所有 pmis:cfg:* 前缀的 key）
     */
    @Override
    public void refreshCache() {
        // 简化：删除所有 pmis:cfg:* 前缀的 key
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys(CACHE_GROUP_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("[Config] 已刷新配置缓存");
    }

    /**
     * 解析配置值（按 valueType 转换为目标类型）
     *
     * @param config 配置实体
     * @param type   目标类型
     * @param <T>    目标类型泛型
     * @return 解析后的值，配置为空时返回 null
     */
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

    /**
     * 失效指定分组的缓存
     *
     * @param group 配置分组
     */
    private void invalidateCache(String group) {
        if (!StringUtils.hasText(group)) return;
        redisTemplate.delete(CACHE_GROUP_PREFIX + group);
    }
}
