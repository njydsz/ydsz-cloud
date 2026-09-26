package com.njydsz.generator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.CodePreviewVO;

/**
 * CodeGenController 纯 Mockito 单元测试（无 Spring 容器）。
 *
 * <p>因 Controller 类级别 @Secured("ROLE_GENERATOR_USER") 需要 Security 上下文，
 * 完整 @WebMvcTest 需要加载 Spring Security 全链路，故退化为 Mockito 单元测试，
 * 验证 Controller 正确委托 Service 并封装响应。
 */
class CodeGenControllerTest {

  @InjectMocks private CodeGenController codeGenController;

  @Mock private CodeGenService mockCodeGenService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("preview: 正常返回 A00000 及代码预览列表")
  void preview_success() {
    // arrange
    CodePreviewVO mockPreview = new CodePreviewVO();
    mockPreview.setFileName("UserController.java");
    mockPreview.setFilePath("src/main/java/controller/UserController.java");
    List<CodePreviewVO> mockList = List.of(mockPreview);
    when(mockCodeGenService.preview(anyLong(), anyLong(), any())).thenReturn(mockList);

    // act
    YdszResponse<List<CodePreviewVO>> response =
        codeGenController.preview(1L, 1L, "sys_user");

    // assert: code / success flag / data structure
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isEqualTo("A00000");
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData()).hasSize(1);
    assertThat(response.getData().get(0).getFileName()).isEqualTo("UserController.java");
    verify(mockCodeGenService).preview(1L, 1L, "sys_user");
  }
}
