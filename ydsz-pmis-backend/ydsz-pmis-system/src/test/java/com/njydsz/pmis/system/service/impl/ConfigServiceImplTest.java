package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.system.dto.ConfigFormDTO;
import com.njydsz.pmis.system.dto.ConfigQueryDTO;
import com.njydsz.pmis.system.entity.ConfigDO;
import com.njydsz.pmis.system.mapper.ConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigServiceImpl 单元测试")
class ConfigServiceImplTest {

    @Mock
    private ConfigMapper configMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ConfigServiceImpl configService;

    @Nested
    @DisplayName("getByKey 方法")
    class GetByKeyTest {

        @Test
        @DisplayName("缓存命中时应直接返回缓存值")
        void shouldReturnCachedValueWhenCacheHit() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            ConfigDO config = new ConfigDO();
            config.setId(1L);
            config.setConfigKey("test_key");
            config.setConfigValue("test_value");
            when(valueOperations.get(anyString()))
                    .thenReturn("{\"id\":1,\"configKey\":\"test_key\",\"configValue\":\"test_value\"}");

            ConfigDO result = configService.getByKey("test_group", "test_key");

            assertThat(result).isNotNull();
            assertThat(result.getConfigKey()).isEqualTo("test_key");
            verify(configMapper, never()).selectByGroupAndKey(anyString(), anyString());
        }

        @Test
        @DisplayName("缓存未命中时应查询数据库并回写缓存")
        void shouldQueryDatabaseWhenCacheMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            ConfigDO config = new ConfigDO();
            config.setId(1L);
            config.setConfigKey("test_key");
            when(configMapper.selectByGroupAndKey("test_group", "test_key")).thenReturn(config);

            ConfigDO result = configService.getByKey("test_group", "test_key");

            assertThat(result).isNotNull();
            verify(configMapper).selectByGroupAndKey("test_group", "test_key");
            verify(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        }
    }

    @Nested
    @DisplayName("listPublic 方法")
    class ListPublicTest {

        @Test
        @DisplayName("应返回公开配置列表")
        void shouldReturnPublicConfigs() {
            ConfigDO config = new ConfigDO();
            config.setId(1L);
            config.setConfigKey("public_key");
            when(configMapper.selectPublic()).thenReturn(List.of(config));

            List<ConfigDO> result = configService.listPublic();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConfigKey()).isEqualTo("public_key");
        }

        @Test
        @DisplayName("无公开配置时应返回空列表")
        void shouldReturnEmptyWhenNoPublicConfigs() {
            when(configMapper.selectPublic()).thenReturn(Collections.emptyList());

            List<ConfigDO> result = configService.listPublic();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create 方法")
    class CreateTest {

        @Test
        @DisplayName("创建配置成功时应返回配置 ID")
        void shouldCreateConfigSuccessfully() {
            ConfigFormDTO dto = new ConfigFormDTO();
            dto.setConfigGroup("test_group");
            dto.setConfigKey("new_key");
            dto.setConfigValue("new_value");
            dto.setValueType("STRING");

            when(configMapper.selectByGroupAndKey("test_group", "new_key")).thenReturn(null);
            doAnswer(invocation -> {
                ConfigDO entity = invocation.getArgument(0);
                entity.setId(100L);
                return 1;
            }).when(configMapper).insert(any(ConfigDO.class));
            when(redisTemplate.delete(anyString())).thenReturn(true);

            Long id = configService.create(dto);

            assertThat(id).isEqualTo(100L);
            verify(configMapper).insert(any(ConfigDO.class));
        }

        @Test
        @DisplayName("valueType 非法时应抛出异常")
        void shouldThrowWhenValueTypeInvalid() {
            ConfigFormDTO dto = new ConfigFormDTO();
            dto.setConfigGroup("test_group");
            dto.setConfigKey("new_key");
            dto.setValueType("INVALID");

            assertThatThrownBy(() -> configService.create(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("valueType");
        }

        @Test
        @DisplayName("配置已存在时应抛出异常")
        void shouldThrowWhenConfigExists() {
            ConfigFormDTO dto = new ConfigFormDTO();
            dto.setConfigGroup("test_group");
            dto.setConfigKey("existing_key");
            dto.setValueType("STRING");

            ConfigDO existing = new ConfigDO();
            when(configMapper.selectByGroupAndKey("test_group", "existing_key")).thenReturn(existing);

            assertThatThrownBy(() -> configService.create(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("已存在");
        }
    }

    @Nested
    @DisplayName("update 方法")
    class UpdateTest {

        @Test
        @DisplayName("更新配置成功时应调用 mapper.updateById")
        void shouldUpdateConfigSuccessfully() {
            ConfigFormDTO dto = new ConfigFormDTO();
            dto.setId(1L);
            dto.setConfigGroup("test_group");
            dto.setConfigKey("updated_key");
            dto.setConfigValue("updated_value");
            dto.setValueType("STRING");

            ConfigDO existing = new ConfigDO();
            existing.setId(1L);
            when(configMapper.selectById(1L)).thenReturn(existing);
            when(configMapper.updateById(any(ConfigDO.class))).thenReturn(1);
            when(redisTemplate.delete(anyString())).thenReturn(true);

            assertThatCode(() -> configService.update(dto)).doesNotThrowAnyException();
            verify(configMapper).updateById(any(ConfigDO.class));
        }

        @Test
        @DisplayName("ID 为空时应抛出异常")
        void shouldThrowWhenIdIsNull() {
            ConfigFormDTO dto = new ConfigFormDTO();
            dto.setConfigGroup("test_group");
            dto.setConfigKey("key");

            assertThatThrownBy(() -> configService.update(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("ID");
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除配置成功时应调用 mapper.deleteById")
        void shouldDeleteConfigSuccessfully() {
            ConfigDO config = new ConfigDO();
            config.setId(1L);
            config.setConfigGroup("test_group");
            when(configMapper.selectById(1L)).thenReturn(config);
            when(configMapper.deleteById(1L)).thenReturn(1);
            when(redisTemplate.delete(anyString())).thenReturn(true);

            assertThatCode(() -> configService.delete(1L)).doesNotThrowAnyException();
            verify(configMapper).deleteById(1L);
        }

        @Test
        @DisplayName("配置不存在时应抛出异常")
        void shouldThrowWhenConfigNotFound() {
            when(configMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> configService.delete(999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("分页查询应返回正确结果")
    void shouldReturnPagedConfigs() {
        ConfigQueryDTO query = new ConfigQueryDTO();
        query.setPage(1);
        query.setSize(10);
        when(configMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

        Page<ConfigDO> result = configService.page(query);

        assertThat(result).isNotNull();
        verify(configMapper).selectPage(any(Page.class), any());
    }
}