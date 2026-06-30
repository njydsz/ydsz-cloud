package com.njydsz.pmis.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.config.dto.ConfigFormDTO;
import com.njydsz.pmis.config.entity.ConfigDO;
import com.njydsz.pmis.config.mapper.ConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ConfigServiceImpl 单元测试
 */
@DisplayName("ConfigServiceImpl 配置中心测试")
class ConfigServiceImplTest {

    private ConfigMapper mapper;
    private StringRedisTemplate redis;
    private ConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ConfigMapper.class);
        redis = mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new ConfigServiceImpl(mapper, redis);
    }

    @Test
    @DisplayName("create 重复应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(config(1L, "g", "k", "v"));
        ConfigFormDTO dto = form("g", "k", "v");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 应插入并失效缓存")
    void create_ok() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        when(mapper.insert(any(ConfigDO.class))).thenAnswer(inv -> {
            ConfigDO c = inv.getArgument(0);
            c.setId(99L);
            return 1;
        });
        ConfigFormDTO dto = form("g", "k", "v");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(99L);
        org.mockito.Mockito.verify(redis).delete(eq("pmis:cfg:group:g"));
    }

    @Test
    @DisplayName("getByKey 缓存命中应直接返回")
    void getByKey_cacheHit() {
        ConfigDO c = config(1L, "g", "k", "v");
        when(redis.opsForValue().get("pmis:cfg:g:k")).thenReturn(com.alibaba.fastjson2.JSON.toJSONString(c));
        ConfigDO r = service.getByKey("g", "k");
        assertThat(r.getConfigValue()).isEqualTo("v");
    }

    @Test
    @DisplayName("getByKey 缓存未命中应查 DB 并写缓存")
    void getByKey_cacheMiss() {
        when(redis.opsForValue().get("pmis:cfg:g:k")).thenReturn(null);
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(config(1L, "g", "k", "v"));
        ConfigDO r = service.getByKey("g", "k");
        assertThat(r.getConfigValue()).isEqualTo("v");
        org.mockito.Mockito.verify(redis.opsForValue()).set(eq("pmis:cfg:g:k"), anyString(), any());
    }

    @Test
    @DisplayName("getGroupAsMap 应扁平化为 map")
    void getGroupAsMap() {
        when(redis.opsForValue().get("pmis:cfg:group:g")).thenReturn(null);
        when(mapper.selectByGroup("g")).thenReturn(List.of(
                config(1L, "g", "k1", "v1"),
                config(2L, "g", "k2", "v2")
        ));
        Map<String, String> m = service.getGroupAsMap("g");
        assertThat(m).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    @DisplayName("parseValue 支持 STRING/NUMBER/BOOLEAN/JSON")
    void parseValue() {
        assertThat(service.parseValue(config(1L, "g", "k", "100"), Long.class)).isEqualTo(100L);
        assertThat(service.parseValue(config(1L, "g", "k", "true"), Boolean.class)).isTrue();
        assertThat(service.parseValue(config(1L, "g", "k", "hello"), String.class)).isEqualTo("hello");
    }

    @Test
    @DisplayName("parseValue JSON 类型应正确反序列化")
    void parseValue_json() {
        ConfigDO c = config(1L, "g", "k", "{\"a\":1}");
        c.setValueType("JSON");
        Map<?, ?> m = service.parseValue(c, Map.class);
        assertThat(m.get("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("refreshCache 应删除全部 cfg 缓存")
    void refresh() {
        when(redis.keys("pmis:cfg:*")).thenReturn(java.util.Set.of("a", "b"));
        when(redis.keys("pmis:cfg:group:*")).thenReturn(java.util.Set.of("c"));
        service.refreshCache();
        // delete 被调用两次：cfg 单 key + cfg group
        org.mockito.Mockito.verify(redis, org.mockito.Mockito.times(2))
                .delete(org.mockito.ArgumentMatchers.<java.util.Set<String>>any());
    }

    @Test
    @DisplayName("update 不存在应抛 NOT_FOUND")
    void update_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        ConfigFormDTO dto = form("g", "k", "v");
        dto.setId(99L);
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    private ConfigDO config(Long id, String group, String key, String value) {
        ConfigDO c = new ConfigDO();
        c.setId(id);
        c.setConfigGroup(group);
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setValueType("STRING");
        c.setStatus("ENABLED");
        return c;
    }

    private ConfigFormDTO form(String group, String key, String value) {
        ConfigFormDTO dto = new ConfigFormDTO();
        dto.setConfigGroup(group);
        dto.setConfigKey(key);
        dto.setConfigValue(value);
        return dto;
    }
}
