package com.njydsz.system.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import com.njydsz.system.server.constant.SystemCacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.ConfigExcelService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.rollback.ConfigRollbackStrategy;
import com.njydsz.system.server.service.impl.ConfigServiceImpl;
import com.njydsz.system.server.config.SystemProperties.ConfigCache;

/**
 * ConfigServiceImpl 单元测试（纯 Mockito，无 Spring 容器，云顶编码规范 §14.1 测试金字塔顶层）。
 *
 * <p>覆盖核心分支：
 *
 * <ul>
 *   <li>{@code pageByCursor} — 页大小边界归一化与 nextCursor 计算</li>
 *   <li>{@code save} — 重复 key 拦截 + 值类型格式校验</li>
 *   <li>{@code getConfigValue} — 仓储委托与 null 传播</li>
 * </ul>
 *
 * <p>写操作（更新/删除）因覆盖事件发布 + 快照创建等副作用，已通过 {@link com.njydsz.system.infra.repository.ConfigRepositoryIT}
 * 与 E2E 链路覆盖，单元测试聚焦纯计算与分支决策逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceImplTest {

  @Mock private ConfigRepository configRepository;
  @Mock private SystemMetrics metrics;
  @Mock private SystemProperties properties;
  @Mock private ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  @Mock private EntityVersionService entityVersionService;
  @Mock private CacheManager cacheManager;
  @Mock private CacheKeyBuilder cacheKeyBuilder;
  @Mock private ConfigExcelService configExcelService;
  @Mock private ConfigRollbackStrategy rollbackStrategy;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private ConfigServiceImpl configService;

  @BeforeEach
  void setUp() {
    // strictValidation 默认为 false（向后兼容存量非法值）
    ConfigCache configCache = mock(ConfigCache.class);
    when(properties.getConfig()).thenReturn(configCache);
    when(configCache.isStrictValidation()).thenReturn(false);
    // SpEL @cacheKeyBuilder 调用全部返回 testKey，不影响黑盒行为
    when(cacheKeyBuilder.configValue(anyString())).thenReturn("testKey");
    when(cacheKeyBuilder.configGroup(anyString())).thenReturn("testGroup");
    when(cacheKeyBuilder.configPublic()).thenReturn("testPublic");
    when(cacheManager.getCache(SystemCacheConstants.SYSTEM_CONFIG_CACHE)).thenReturn(mock(Cache.class));
    // eventPublisherProvider 返回 null 时走降级逻辑
    when(eventPublisherProvider.getIfAvailable()).thenReturn(null);
  }

  @Nested
  @DisplayName("pageByCursor — 游标分页")
  class PageByCursor {

    @Test
    @DisplayName("页大小 ≤ 0 时应归一化为 1")
    void shouldNormalizePageSizeWhenZeroOrNegative() {
      when(configRepository.findForCursor("grp", "k", null, 1)).thenReturn(List.of());

      PageResponse<ConfigVO> result = configService.pageByCursor("grp", "k", 0, null);

      verify(configRepository).findForCursor("grp", "k", null, 1);
      assertThat((java.util.List<ConfigVO>) result.getData()).isEmpty();
      assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("页大小 > 500 时应归一化为 500")
    void shouldNormalizePageWhenExceeds500() {
      when(configRepository.findForCursor("grp", "k", null, 500)).thenReturn(List.of());

      configService.pageByCursor("grp", "k", 9999, null);

      verify(configRepository).findForCursor("grp", "k", null, 500);
    }

    @Test
    @DisplayName("本页满且存在后续数据时应返回 nextCursor = 最后一条 ID")
    void shouldReturnNextCursorWhenMoreData() {
      ConfigVO record = new ConfigVO();
      record.setId("last-id-1");
      ConfigVO record2 = new ConfigVO();
      record2.setId("last-id-2");
      when(configRepository.findForCursor("grp", "k", null, 2)).thenReturn(List.of(record, record2));
      when(configRepository.existsAfterCursor("grp", "k", "last-id-2")).thenReturn(true);

      PageResponse<ConfigVO> result = configService.pageByCursor("grp", "k", 2, null);

      assertThat(result.getNextCursor()).isEqualTo("last-id-2");
    }

    @Test
    @DisplayName("本页满但无后续数据时应返回 nextCursor = null")
    void shouldReturnNullCursorWhenNoMoreData() {
      ConfigVO record = new ConfigVO();
      record.setId("only-one");
      when(configRepository.findForCursor("grp", "k", null, 1)).thenReturn(List.of(record));
      when(configRepository.existsAfterCursor("grp", "k", "only-one")).thenReturn(false);

      PageResponse<ConfigVO> result = configService.pageByCursor("grp", "k", 1, null);

      assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("传入游标应透传到仓储查询")
    void shouldPassCursorToRepository() {
      when(configRepository.findForCursor("grp", "k", "prev-cursor", 10)).thenReturn(List.of());

      configService.pageByCursor("grp", "k", 10, "prev-cursor");

      verify(configRepository).findForCursor("grp", "k", "prev-cursor", 10);
    }
  }

  @Nested
  @DisplayName("save — 新增配置")
  class Save {

    @Test
    @DisplayName("应调用 repository.insert 并返回 DTO ID")
    void shouldCallInsertAndReturnId() {
      ConfigDTO dto = new ConfigDTO();
      dto.setId("new-id-123");
      dto.setConfigGroup("system");
      dto.setConfigKey("app.name");
      dto.setConfigValue("test");
      dto.setValueType("STRING");
      when(configRepository.existsByGroupAndKey("system", "app.name")).thenReturn(false);

      String result = configService.save(dto);

      verify(configRepository).insert(dto);
      assertThat(result).isEqualTo("new-id-123");
    }

    @Test
    @DisplayName("重复 key 时应抛出 CONFIG_KEY_DUPLICATE 异常")
    void shouldThrowWhenDuplicateKey() {
      ConfigDTO dto = new ConfigDTO();
      dto.setConfigGroup("system");
      dto.setConfigKey("duplicate.key");
      dto.setValueType("STRING");
      when(configRepository.existsByGroupAndKey("system", "duplicate.key")).thenReturn(true);

      assertThatThrownBy(() -> configService.save(dto))
          .isInstanceOf(BusinessException.class);

      verify(configRepository, never()).insert(any());
    }

    @Test
    @DisplayName("strictValidation=true + 格式非法时应抛出异常")
    void shouldThrowWhenStrictValidationFails() {
      when(properties.getConfig().isStrictValidation()).thenReturn(true);
      ConfigDTO dto = new ConfigDTO();
      dto.setConfigGroup("sys");
      dto.setConfigKey("num.key");
      dto.setConfigValue("not-a-number");
      dto.setValueType("NUMBER");
      when(configRepository.existsByGroupAndKey("sys", "num.key")).thenReturn(false);

      assertThatThrownBy(() -> configService.save(dto))
          .isInstanceOf(BusinessException.class);

      verify(configRepository, never()).insert(any());
    }

    @Test
    @DisplayName("strictValidation=false + 格式非法时应放行（仅告警）")
    void shouldPassWhenLooseValidation() {
      when(properties.getConfig().isStrictValidation()).thenReturn(false);
      ConfigDTO dto = new ConfigDTO();
      dto.setId("loose-id");
      dto.setConfigGroup("sys");
      dto.setConfigKey("loose.key");
      dto.setConfigValue("not-a-number");
      dto.setValueType("NUMBER");
      when(configRepository.existsByGroupAndKey("sys", "loose.key")).thenReturn(false);

      String result = configService.save(dto);

      verify(configRepository).insert(dto);
      assertThat(result).isEqualTo("loose-id");
    }
  }

  @Nested
  @DisplayName("getConfigValue — 读配置")
  class GetConfigValue {

    @Test
    @DisplayName("仓储命中时应返回 configValue")
    void shouldReturnConfigValueWhenFound() {
      ConfigVO vo = new ConfigVO();
      vo.setConfigValue("hit-value");
      when(configRepository.findEnabledByKey("some.key")).thenReturn(Optional.of(vo));

      String result = configService.getConfigValue("some.key");

      assertThat(result).isEqualTo("hit-value");
    }

    @Test
    @DisplayName("仓储未命中时应返回 null")
    void shouldReturnNullWhenNotFound() {
      when(configRepository.findEnabledByKey("miss.key")).thenReturn(Optional.empty());

      String result = configService.getConfigValue("miss.key");

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("getById / page — 简单委托")
  class Delegation {

    @Test
    @DisplayName("getById 应委托 Repository.findById")
    void getByIdShouldDelegate() {
      ConfigVO vo = new ConfigVO();
      vo.setId("cfg-1");
      when(configRepository.findById("cfg-1")).thenReturn(Optional.of(vo));

      ConfigVO result = configService.getById("cfg-1");

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo("cfg-1");
    }

    @Test
    @DisplayName("getById null 时应返回 null")
    void getByIdShouldReturnNullWhenNotFound() {
      when(configRepository.findById("ghost")).thenReturn(Optional.empty());

      ConfigVO result = configService.getById("ghost");

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("page 应委托 Repository.findByPage")
    void pageShouldDelegate() {
      ConfigPageQuery query = new ConfigPageQuery();
      PageResponse<List<ConfigVO>> expected = PageResponse.empty(1L, 20L);
      when(configRepository.findByPage(query)).thenReturn(expected);

      PageResponse<List<ConfigVO>> result = configService.page(query);

      assertThat(result).isSameAs(expected);
    }
  }
}
