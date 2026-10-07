package com.njydsz.generator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.query.GenCodeGenerateQuery;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.CodePreviewVO;
import com.njydsz.generator.vo.GenResultVO;

/**
 * {@link CodeGenController} 单元测试。
 *
 * <p>覆盖代码预览与生成两个核心路径。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class CodeGenControllerTest {

  private MockMvc mockMvc;

  @Mock
  private CodeGenService codeGenService;

  @InjectMocks
  private CodeGenController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 预览代码生成返回 200。
   */
  @Test
  @DisplayName("GET /generator/code/preview 预览代码返回 200")
  void previewReturnsOk() throws Exception {
    CodePreviewVO preview = new CodePreviewVO();
    preview.setFileName("UserEntity.java");
    preview.setContent("public class UserEntity {}");

    when(codeGenService.preview(any(Long.class), any(Long.class), any(String.class)))
        .thenReturn(List.of(preview));

    mockMvc.perform(get("/generator/code/preview")
            .param("datasourceId", "1")
            .param("templateGroupId", "1")
            .param("tableName", "ydsz_user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 正式生成代码返回 200。
   */
  @Test
  @DisplayName("POST /generator/code/generate 正式生成代码返回 200")
  void generateReturnsOk() throws Exception {
    GenResultVO result = new GenResultVO();
    result.setHistoryId(1L);
    result.setFileCount(5);
    result.setSuccessCount(5);

    when(codeGenService.generate(any(GenCodeGenerateQuery.class))).thenReturn(result);

    String body = "{\"datasourceId\":1,\"templateGroupId\":1,\"tableName\":\"ydsz_user\","
        + "\"outputDir\":\"/tmp/gen\",\"conflictStrategy\":\"SKIP\"}";
    mockMvc.perform(post("/generator/code/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
