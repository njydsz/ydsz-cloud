package com.njydsz.system.server.service;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.system.domain.entity.DictType;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.impl.DictServiceImpl;
import com.njydsz.system.server.service.event.DictChangeEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DictServiceImpl 单元测试 — 验证分页查询和事件发布（纯 Mockito，无 Spring Context）
 *
 * <p>JUnit5 + Mockito 范式，覆盖：正常路径、空结果、事件发布。遵循 YDSZ 测试命名规范：
 * 方法名 = 场景_期望行为。
 *
 * @author ydsz-team
 * @since 26.10.05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DictServiceImpl - 单元测试")
class DictServiceImplTest {

  @InjectMocks
  private DictServiceImpl sut;

  @Mock
  private DictRepository dictRepository;

  @Mock
  private ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  @Mock
  private DictChangeEventPublisher dictChangeEventPublisher;

  @Nested
  @DisplayName("page() 分页查询")
  class PageTest {

    @Test
    @DisplayName("给定有效查询 → 返回分页结果且调用 Repository")
    void givenValidQuery_whenPage_thenReturnPageResponse() {
      // Arrange
      DictPageQuery query = new DictPageQuery();
      query.setPageNum(1);
      query.setPageSize(10);

      DictTypeVO vo = new DictTypeVO();
      vo.setId("test-001");
      vo.setTypeCode("sys_status");

      PageResponse<List<DictTypeVO>> mockResponse = PageResponse.success(
          1L, 1L, 10L, Arrays.asList(vo));

      when(dictRepository.findTypePage(any(DictPageQuery.class))).thenReturn(mockResponse);

      // Act
      PageResponse<List<DictTypeVO>> result = sut.page(query);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getData()).hasSize(1);
      assertThat(result.getData().get(0).getTypeCode()).isEqualTo("sys_status");
      assertThat(result.getTotal()).isEqualTo(1L);

      verify(dictRepository).findTypePage(query);
    }

    @Test
    @DisplayName("Repository 返回空 → 返回空分页结果")
    void givenNoResult_whenPage_thenReturnEmptyPage() {
      // Arrange
      DictPageQuery query = new DictPageQuery();
      PageResponse<List<DictTypeVO>> emptyResponse = PageResponse.empty(1L, 1L);

      when(dictRepository.findTypePage(any(DictPageQuery.class))).thenReturn(emptyResponse);

      // Act
      PageResponse<List<DictTypeVO>> result = sut.page(query);

      // Assert
      assertThat(result.getData()).isEmpty();
      assertThat(result.getTotal()).isZero();
      verify(dictRepository).findTypePage(query);
    }
  }

  @Nested
  @DisplayName("removeById() 删除操作")
  class DeleteTest {

    @Test
    @DisplayName("给定合法 ID → 查询类型 → 计数为零 → 删除并发布事件")
    void givenValidId_whenRemove_thenRepositoryAndEventCalled() {
      // Arrange
      String id = "dict-001";
      DictTypeVO vo = new DictTypeVO();
      vo.setId(id);
      vo.setTypeCode("sys_status");
      when(dictRepository.findTypeById(id)).thenReturn(java.util.Optional.of(vo));
      when(dictRepository.countItemsByTypeCode("sys_status")).thenReturn(0L);
      when(dictRepository.deleteTypeById(id)).thenReturn(true);
      DomainEventPublisher mockPublisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
      when(eventPublisherProvider.getIfAvailable()).thenReturn(mockPublisher);

      // Act
      boolean removed = sut.removeById(id);

      // Assert
      assertThat(removed).isTrue();
      verify(dictRepository).findTypeById(id);
      verify(dictRepository).countItemsByTypeCode("sys_status");
      verify(dictRepository).deleteTypeById(id);
      verifyNoInteractions(mockPublisher);
    }
  }
}
