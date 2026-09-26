package com.njydsz.nextwiki.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.FileApplicationService;

/**
 * FileController 纯 Mockito 单元测试（无 Spring 容器）。
 *
 * <p>因 Controller 依赖 @AuthApiPermission / @Idempotent / @Audit 等多个 AOP 切面，
 * 完整 @WebMvcTest 需要加载 Security + Redis 等重量级上下文，故退化为 Mockito 单元测试，
 * 验证 Controller 正确委托 Service 并封装响应。
 */
class FileControllerTest {

  @InjectMocks private FileController fileController;

  @Mock private FileApplicationService mockFileApplicationService;

  @Mock private NextwikiMetrics mockNextwikiMetrics;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("listFiles: 正常返回文件列表分页")
  void listFiles_success() {
    // arrange
    PageResponse<List<FileNodeVO>> emptyPage = PageResponse.empty(1L, 50L);
    YdszResponse<PageResponse<List<FileNodeVO>>> serviceResult = YdszResponse.success(emptyPage);
    when(mockFileApplicationService.listFiles(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(serviceResult);

    // act
    YdszResponse<PageResponse<List<FileNodeVO>>> response =
        fileController.listFiles(null, null, null, "all", 1, 50, "user-001");

    // assert: code / success flag / data structure
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotal()).isEqualTo(0L);
    assertThat(response.getData().getPageNum()).isEqualTo(1L);
    assertThat(response.getData().getPageSize()).isEqualTo(50L);
    verify(mockFileApplicationService)
        .listFiles(any(), any(), any(), any(), any(), anyInt(), anyInt());
  }
}
