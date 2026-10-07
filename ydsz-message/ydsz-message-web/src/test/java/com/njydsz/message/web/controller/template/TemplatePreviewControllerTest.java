package com.njydsz.message.web.controller.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.service.TemplateService;
import com.njydsz.message.server.template.MessageTemplateRenderer;
import com.njydsz.message.server.template.TemplateVariableValidator;

/**
 * TemplatePreviewController 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建轻量 Web 层测试。通过 Mockito {@code @Mock} 模拟
 * TemplateService / MessageTemplateRenderer / TemplateVariableValidator 三个依赖，验证模板渲染预览端点。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class TemplatePreviewControllerTest {

  private MockMvc mockMvc;

  @Mock
  private TemplateService templateService;

  @Mock
  private MessageTemplateRenderer templateEngine;

  @Mock
  private TemplateVariableValidator variableValidator;

  @InjectMocks
  private TemplatePreviewController templatePreviewController;

  private MsgTemplateVO sampleTemplate;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(templatePreviewController).build();

    sampleTemplate = new MsgTemplateVO();
    sampleTemplate.setId("tpl-001");
    sampleTemplate.setTemplateCode("SMS_VERIFY_CODE");
    sampleTemplate.setChannel("SMS");
    sampleTemplate.setLocale("zh_CN");
    sampleTemplate.setSubject("验证码：${code}");
    sampleTemplate.setContent("您的验证码为 ${code}，5 分钟内有效");
    sampleTemplate.setStatus("ENABLED");
    sampleTemplate.setAuditStatus("APPROVED");
    sampleTemplate.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
  }

  @Nested
  @DisplayName("POST /message/template/preview/by-code - previewByCode")
  class PreviewByCode {

    @Test
    @DisplayName("模板编码存在时返回渲染结果 content + subject")
    void shouldReturnRenderedResult() throws Exception {
      when(templateService.loadByCodeAndChannel(
              eq("SMS_VERIFY_CODE"), eq("SMS"), eq("zh-CN"), any()))
          .thenReturn(sampleTemplate);
      when(templateEngine.render(eq("您的验证码为 ${code}，5 分钟内有效"),
              any(Map.class)))
          .thenReturn("您的验证码为 123456，5 分钟内有效");
      when(templateEngine.render(eq("验证码：${code}"), any(Map.class)))
          .thenReturn("验证码：123456");

      mockMvc.perform(post("/message/template/preview/by-code")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"SMS_VERIFY_CODE\","
                  + "\"channel\":\"SMS\",\"locale\":\"zh-CN\","
                  + "\"params\":{\"code\":\"123456\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.content").value("您的验证码为 123456，5 分钟内有效"))
          .andExpect(jsonPath("$.data.subject").value("验证码：123456"));

      verify(templateService, times(1))
          .loadByCodeAndChannel(eq("SMS_VERIFY_CODE"), eq("SMS"), eq("zh-CN"), any());
      verify(templateEngine, times(1))
          .render(eq("您的验证码为 ${code}，5 分钟内有效"), any(Map.class));
    }

    @Test
    @DisplayName("模板为空时返回 200 + null data")
    void shouldReturnNullWhenTemplateNotFound() throws Exception {
      when(templateService.loadByCodeAndChannel(
              eq("SMS_UNKNOWN"), any(), any(), any()))
          .thenReturn(null);

      mockMvc.perform(post("/message/template/preview/by-code")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"SMS_UNKNOWN\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));
    }

    @Test
    @DisplayName("模板编码为空时返回错误响应")
    void shouldReturnErrorWhenTemplateCodeBlank() throws Exception {
      mockMvc.perform(post("/message/template/preview/by-code")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"));
    }
  }

  @Nested
  @DisplayName("POST /message/template/preview/raw - previewRaw")
  class PreviewRaw {

    @Test
    @DisplayName("原始模板渲染成功时返回渲染后字符串")
    void shouldReturnRenderedString() throws Exception {
      when(templateEngine.render(eq("Hello ${name}"), any(Map.class)))
          .thenReturn("Hello world");

      mockMvc.perform(post("/message/template/preview/raw")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"template\":\"Hello ${name}\","
                  + "\"params\":{\"name\":\"world\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value("Hello world"));

      verify(templateEngine, times(1)).render(eq("Hello ${name}"), any(Map.class));
    }

    @Test
    @DisplayName("模板内容为空时返回错误响应")
    void shouldReturnErrorWhenTemplateBlank() throws Exception {
      mockMvc.perform(post("/message/template/preview/raw")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"template\":\"\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A10002"));
    }
  }
}
