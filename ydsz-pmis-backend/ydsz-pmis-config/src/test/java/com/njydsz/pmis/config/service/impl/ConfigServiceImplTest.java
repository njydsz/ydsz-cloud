package com.njydsz.pmis.config.service.impl;

import com.alibaba.fastjson2.JSON;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConfigServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
@DisplayName("ConfigServiceImpl 配置中心测试")
class ConfigServiceImplTest {

    /** 配置 Mapper（Mock） */
    private ConfigMapper mapper;
    /** Redis 操作模板（Mock） */
    private StringRedisTemplate redis;
    /** 待测服务实例 */
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
        verify(redis).delete(eq("pmis:cfg:group:g"));
    }

    @Test
    @DisplayName("getByKey 缓存命中应直接返回")
    void getByKey_cacheHit() {
        ConfigDO c = config(1L, "g", "k", "v");
        when(redis.opsForValue().get("pmis:cfg:g:k")).thenReturn(JSON.toJSONString(c));
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
        verify(redis.opsForValue()).set(eq("pmis:cfg:g:k"), anyString(), any());
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
        when(redis.keys("pmis:cfg:*")).thenReturn(Set.of("a", "b"));
        when(redis.keys("pmis:cfg:group:*")).thenReturn(Set.of("c"));
        service.refreshCache();
        // delete 被调用两次：cfg 单 key + cfg group
        verify(redis, times(2))
                .delete(any(Set.class));
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

    @Test
    @DisplayName("create NUMBER 类型值非法应抛 BAD_REQUEST")
    void create_invalidNumberValue() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        ConfigFormDTO dto = form("g", "k", "abc");
        dto.setValueType("NUMBER");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create BOOLEAN 类型值非法应抛 BAD_REQUEST")
    void create_invalidBooleanValue() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        ConfigFormDTO dto = form("g", "k", "yes");
        dto.setValueType("BOOLEAN");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create JSON 类型值非法应抛 BAD_REQUEST")
    void create_invalidJsonValue() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        ConfigFormDTO dto = form("g", "k", "{a:1}");
        dto.setValueType("JSON");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create valueType 非法应抛 BAD_REQUEST")
    void create_invalidValueType() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        ConfigFormDTO dto = form("g", "k", "v");
        dto.setValueType("ARRAY");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create NUMBER 类型合法值应通过")
    void create_validNumber() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        when(mapper.insert(any(ConfigDO.class))).thenAnswer(inv -> {
            ConfigDO c = inv.getArgument(0);
            c.setId(101L);
            return 1;
        });
        ConfigFormDTO dto = form("g", "k", "100");
        dto.setValueType("NUMBER");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(101L);
    }

    @Test
    @DisplayName("create BOOLEAN 类型合法值应通过")
    void create_validBoolean() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        when(mapper.insert(any(ConfigDO.class))).thenAnswer(inv -> {
            ConfigDO c = inv.getArgument(0);
            c.setId(102L);
            return 1;
        });
        ConfigFormDTO dto = form("g", "k", "true");
        dto.setValueType("BOOLEAN");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(102L);
    }

    @Test
    @DisplayName("create JSON 类型合法值应通过")
    void create_validJson() {
        when(mapper.selectByGroupAndKey("g", "k")).thenReturn(null);
        when(mapper.insert(any(ConfigDO.class))).thenAnswer(inv -> {
            ConfigDO c = inv.getArgument(0);
            c.setId(103L);
            return 1;
        });
        ConfigFormDTO dto = form("g", "k", "{\"a\":1}");
        dto.setValueType("JSON");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(103L);
    }

    @Test
    @DisplayName("deleteByGroup 空 group 应抛 BAD_REQUEST")
    void deleteByGroup_empty() {
        assertThatThrownBy(() -> service.deleteByGroup(""))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("deleteByGroup 成功应清除缓存")
    void deleteByGroup_ok() {
        when(mapper.deleteByGroup("g")).thenReturn(3);
        int n = service.deleteByGroup("g");
        assertThat(n).isEqualTo(3);
        verify(redis).delete(eq("pmis:cfg:group:g"));
    }

    @Test
    @DisplayName("deleteByGroup 无数据不应清除缓存")
    void deleteByGroup_zero() {
        when(mapper.deleteByGroup("g")).thenReturn(0);
        int n = service.deleteByGroup("g");
        assertThat(n).isEqualTo(0);
        verify(redis, never())
                .delete(eq("pmis:cfg:group:g"));
    }

    @Test
    @DisplayName("updateStatusByGroup 状态非法应抛 BAD_REQUEST")
    void updateStatusByGroup_invalidStatus() {
        assertThatThrownBy(() -> service.updateStatusByGroup("g", "UNKNOWN"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("updateStatusByGroup 空分组应抛 BAD_REQUEST")
    void updateStatusByGroup_empty() {
        assertThatThrownBy(() -> service.updateStatusByGroup("", "ENABLED"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("updateStatusByGroup 启用成功")
    void updateStatusByGroup_enable() {
        when(mapper.updateStatusByGroup("g", "ENABLED")).thenReturn(5);
        int n = service.updateStatusByGroup("g", "ENABLED");
        assertThat(n).isEqualTo(5);
        verify(redis).delete(eq("pmis:cfg:group:g"));
    }

    @Test
    @DisplayName("updateStatusByGroup 停用成功")
    void updateStatusByGroup_disable() {
        when(mapper.updateStatusByGroup("g", "DISABLED")).thenReturn(2);
        int n = service.updateStatusByGroup("g", "DISABLED");
        assertThat(n).isEqualTo(2);
    }

    @Test
    @DisplayName("update NUMBER 类型值非法应抛 BAD_REQUEST")
    void update_invalidNumber() {
        when(mapper.selectById(99L)).thenReturn(config(99L, "g", "k", "old"));
        ConfigFormDTO dto = form("g", "k", "abc");
        dto.setId(99L);
        dto.setValueType("NUMBER");
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("update valueType 非法应抛 BAD_REQUEST")
    void update_invalidValueType() {
        when(mapper.selectById(99L)).thenReturn(config(99L, "g", "k", "v"));
        ConfigFormDTO dto = form("g", "k", "v");
        dto.setId(99L);
        dto.setValueType("XYZ");
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class);
    }

    /**
     * 构造测试用配置实体
     *
     * @param id    配置 ID
     * @param group 配置分组
     * @param key   配置键
     * @param value 配置值
     * @return 配置实体
     */
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

    /**
     * 构造测试用配置表单
     *
     * @param group 配置分组
     * @param key   配置键
     * @param value 配置值
     * @return 配置表单
     */
    private ConfigFormDTO form(String group, String key, String value) {
        ConfigFormDTO dto = new ConfigFormDTO();
        dto.setConfigGroup(group);
        dto.setConfigKey(key);
        dto.setConfigValue(value);
        return dto;
    }
}
