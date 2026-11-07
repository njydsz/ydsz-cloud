package com.njydsz.message.web.controller.template;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.service.TemplateService;
import com.njydsz.message.server.template.MessageTemplateRenderer;
import com.njydsz.message.server.template.TemplateVariableDef;
import com.njydsz.message.server.template.TemplateVariableValidator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TemplatePreviewController} Smoke Test。
 *
 * <p>验证模板预览两个端点的真实 HTTP 路径/方法、请求体绑定、变量校验、响应字段与负例。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>POST /by-code 成功 → 200 + content/subject 渲染结果
 *   <li>POST /by-code 模板不存在 → 200 + code=B91001
 *   <li>POST /by-code templateCode 为空 → 200 + code=A10002（VALIDATION_FAILED）
 *   <li>POST /by-code 变量校验失败 → 异常冒泡
 *   <li>POST /by-code channel/locale 缺省 → 默认 INAPP / zh-CN
 *   <li>POST /raw 成功 → 200 + 渲染字符串
 *   <li>POST /raw template 为空 → 200 + code=A10002
 *   <li>请求体非法 JSON → 400
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class TemplatePreviewControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private TemplateService templateService;

  @Mock private MessageTemplateRenderer templateEngine;

  @Mock private TemplateVariableValidator variableValidator;

  @Mock private I18nMessages i18nMessages;

  @InjectMocks private TemplatePreviewController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
    when(i18nMessages.resolve(anyString())).thenAnswer(inv -> inv.getArgument(0));
    when(i18nMessages.resolve(anyString(), any(Object[].class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Nested
  @DisplayName("POST /message/template/preview/by-code")
  class PreviewByCode {

    @Test
    @DisplayName("预览成功 → 200 + content/subject 渲染结果")
    void should_render_template_success() throws Exception {
      MsgTemplateVO template = new MsgTemplateVO();
      template.setTemplateCode("ORDER_NOTIFY");
      template.setContent("您好 ${name}，订单 ${orderId} 已发货");
      template.setSubject("订单通知");
      when(templateService.loadByCodeAndChannel("ORDER_NOTIFY", "INAPP", "zh-CN", null))
          .thenReturn(template);
      when(variableValidator.parse(anyString())).thenReturn(List.of());
      when(templateEngine.render(anyString(), anyMap()))
          .thenAnswer(inv -> inv.getArgument(0));

      mockMvc
          .perform(
              post("/message/template/preview/by-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"templateCode\":\"ORDER_NOTIFY\",\"channel\":\"INAPP\","
                          + "\"locale\":\"zh-CN\",\"params\":{\"name\":\"Alice\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.content").value("您好 ${name}，订单 ${orderId} 已发货"))
          .andExpect(jsonPath("$.data.subject").value("订单通知"));
    }

    @Test
    @DisplayName("模板不存在 → 200 + code=B91001（TEMPLATE_NOT_FOUND）")
    void should_return_template_not_found() throws Exception {
      when(templateService.loadByCodeAndChannel("MISSING", "INAPP", "zh-CN", null))
          .thenReturn(null);

      mockMvc
          .perform(
              post("/message/template/preview/by-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"templateCode\":\"MISSING\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(MessageExceptionCode.TEMPLATE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("templateCode 为空 → 200 + code=A10002（VALIDATION_FAILED），服务零调用")
    void should_reject_blank_template_code() throws Exception {
      mockMvc
          .perform(
              post("/message/template/preview/by-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"templateCode\":\"\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(YdszResultCode.VALIDATION_FAILED.getCode()));

      verify(templateService, never())
          .loadByCodeAndChannel(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("channel/locale 缺省 → 使用默认值 INAPP / zh-CN 调用服务")
    void should_use_default_channel_and_locale() throws Exception {
      MsgTemplateVO template = new MsgTemplateVO();
      template.setContent("content");
      template.setSubject("subject");
      when(templateService.loadByCodeAndChannel("ORDER_NOTIFY", "INAPP", "zh-CN", null))
          .thenReturn(template);
      when(variableValidator.parse(any())).thenReturn(List.of());
      when(templateEngine.render(anyString(), anyMap()))
          .thenAnswer(inv -> inv.getArgument(0));

      mockMvc
          .perform(
              post("/message/template/preview/by-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"templateCode\":\"ORDER_NOTIFY\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(templateService).loadByCodeAndChannel("ORDER_NOTIFY", "INAPP", "zh-CN", null);
    }

    @Test
    @DisplayName("变量校验失败（validateAndFill 抛异常）→ 异常冒泡，渲染不执行")
    void should_propagate_validation_exception() throws Exception {
      MsgTemplateVO template = new MsgTemplateVO();
      template.setContent("content");
      template.setVariableDefs("[{\"name\":\"code\",\"type\":\"STRING\",\"required\":true}]");
      when(templateService.loadByCodeAndChannel("ORDER_NOTIFY", "INAPP", "zh-CN", null))
          .thenReturn(template);
      TemplateVariableDef def = new TemplateVariableDef();
      def.setName("code");
      def.setType(TemplateVariableDef.VariableType.STRING);
      def.setRequired(true);
      when(variableValidator.parse(anyString())).thenReturn(List.of(def));
      doThrow(new SysException("必填变量缺失: code"))
          .when(variableValidator)
          .validateAndFill(anyMap(), anyList(), anyString());

      assertThrows(
          Exception.class,
          () ->
              mockMvc.perform(
                  post("/message/template/preview/by-code")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"templateCode\":\"ORDER_NOTIFY\",\"params\":{\"other\":\"x\"}}")));

      verify(templateEngine, never()).render(anyString(), anyMap());
    }

    @Test
    @DisplayName("variableDefs 有值时触发变量校验 + 默认值填充")
    void should_trigger_variable_validation_when_defs_present() throws Exception {
      MsgTemplateVO template = new MsgTemplateVO();
      template.setContent("code=${code}");
      template.setSubject("subject");
      template.setVariableDefs("[{\"name\":\"code\",\"type\":\"STRING\",\"defaultValue\":\"000000\"}]");
      when(templateService.loadByCodeAndChannel("ORDER_NOTIFY", "INAPP", "zh-CN", null))
          .thenReturn(template);
      TemplateVariableDef def = new TemplateVariableDef();
      def.setName("code");
      def.setType(TemplateVariableDef.VariableType.STRING);
      def.setDefaultValue("000000");
      when(variableValidator.parse(anyString())).thenReturn(List.of(def));
      when(templateEngine.render(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

      mockMvc
          .perform(
              post("/message/template/preview/by-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"templateCode\":\"ORDER_NOTIFY\"}"))
          .andExpect(status().isOk());

      verify(variableValidator).validateAndFill(anyMap(), anyList(), anyString());
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（服务零调用）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/template/preview/by-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(templateService, never())
          .loadByCodeAndChannel(anyString(), anyString(), anyString(), any());
    }
  }

  @Nested
  @DisplayName("POST /message/template/preview/raw")
  class PreviewRaw {

    @Test
    @DisplayName("原始预览成功 → 200 + 渲染字符串")
    void should_render_raw_template() throws Exception {
      when(templateEngine.render("你好 ${name}", Map.of("name", "Alice")))
          .thenReturn("你好 Alice");

      mockMvc
          .perform(
              post("/message/template/preview/raw")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"template\":\"你好 ${name}\",\"params\":{\"name\":\"Alice\"}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").value("你好 Alice"));
    }

    @Test
    @DisplayName("template 为空 → 200 + code=A10002（VALIDATION_FAILED），渲染不执行")
    void should_reject_blank_template() throws Exception {
      mockMvc
          .perform(
              post("/message/template/preview/raw")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"template\":\"\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(YdszResultCode.VALIDATION_FAILED.getCode()));

      verify(templateEngine, never()).render(anyString(), anyMap());
    }

    @Test
    @DisplayName("params 缺省 → 使用空 Map 渲染")
    void should_render_with_empty_params_when_absent() throws Exception {
      when(templateEngine.render("static content", Map.of())).thenReturn("static content");

      mockMvc
          .perform(
              post("/message/template/preview/raw")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"template\":\"static content\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").value("static content"));
    }

    @Test
    @DisplayName("请求体非法 JSON → 400（渲染不执行）")
    void should_reject_malformed_json() throws Exception {
      mockMvc
          .perform(
              post("/message/template/preview/raw")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad"))
          .andExpect(status().isBadRequest());

      verify(templateEngine, never()).render(anyString(), anyMap());
    }
  }
}
