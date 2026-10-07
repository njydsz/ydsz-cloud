package com.njydsz.message.web.controller.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

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

import com.njydsz.message.domain.dto.TemplatePreviewDTO;
import com.njydsz.message.domain.dto.TemplateTestSendDTO;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.message.domain.vo.MsgTemplateVersionVO;
import com.njydsz.message.server.service.template.TemplateVersionService;

/**
 * TemplateVersionController 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建轻量级 Web 层测试，通过 Mockito {@code @Mock} 模拟
 * {@link TemplateVersionService} 依赖，验证版本历史、回滚、预览、试发等 HTTP 端点。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class TemplateVersionControllerTest {

  private MockMvc mockMvc;

  @Mock
  private TemplateVersionService templateVersionService;

  @InjectMocks
  private TemplateVersionController templateVersionController;

  private MsgTemplateVersionVO sampleVersion;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(templateVersionController).build();

    sampleVersion = new MsgTemplateVersionVO();
    sampleVersion.setId("ver-001");
    sampleVersion.setTemplateCode("SMS_VERIFY_CODE");
    sampleVersion.setVersion(1);
    sampleVersion.setContent("您的验证码为 ${code}，5 分钟内有效");
    sampleVersion.setVariableDefs("[{\"name\":\"code\",\"type\":\"string\",\"required\":true}]");
    sampleVersion.setAuditStatus("APPROVED");
    sampleVersion.setAuditor("admin");
    sampleVersion.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
  }

  @Nested
  @DisplayName("GET /message/template/version/list/{templateCode} - listVersions")
  class ListVersions {

    @Test
    @DisplayName("查询模板版本历史返回 200 + 版本列表")
    void shouldReturnVersionList() throws Exception {
      when(templateVersionService.listVersions("SMS_VERIFY_CODE"))
          .thenReturn(List.of(sampleVersion));

      mockMvc.perform(get("/message/template/version/list/{templateCode}", "SMS_VERIFY_CODE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data[0].id").value("ver-001"))
          .andExpect(jsonPath("$.data[0].templateCode").value("SMS_VERIFY_CODE"))
          .andExpect(jsonPath("$.data[0].version").value(1))
          .andExpect(jsonPath("$.data[0].auditStatus").value("APPROVED"));

      verify(templateVersionService, times(1)).listVersions("SMS_VERIFY_CODE");
    }

    @Test
    @DisplayName("无版本记录时返回 200 + 空列表")
    void shouldReturnEmptyListWhenNoVersions() throws Exception {
      when(templateVersionService.listVersions("SMS_NEW_CODE"))
          .thenReturn(List.of());

      mockMvc.perform(get("/message/template/version/list/{templateCode}", "SMS_NEW_CODE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").isArray());
    }
  }

  @Nested
  @DisplayName("POST /message/template/version/rollback - rollback")
  class Rollback {

    @Test
    @DisplayName("回滚版本成功时返回 200 + 回滚后内容")
    void shouldReturnRolledBackContent() throws Exception {
      when(templateVersionService.rollbackToVersion("SMS_VERIFY_CODE", 1))
          .thenReturn("回滚到版本1的内容");

      mockMvc.perform(post("/message/template/version/rollback")
              .param("templateCode", "SMS_VERIFY_CODE")
              .param("version", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value("回滚到版本1的内容"));

      verify(templateVersionService, times(1)).rollbackToVersion("SMS_VERIFY_CODE", 1);
    }
  }

  @Nested
  @DisplayName("POST /message/template/version/preview - preview")
  class Preview {

    @Test
    @DisplayName("预览渲染成功时返回 200 + 渲染后内容")
    void shouldReturnRenderedContent() throws Exception {
      TemplatePreviewDTO dto = new TemplatePreviewDTO();
      dto.setTemplateCode("SMS_VERIFY_CODE");
      dto.setContent("您的验证码为 123456，5 分钟内有效");

      when(templateVersionService.preview(any(TemplatePreviewDTO.class)))
          .thenReturn("您的验证码为 123456，5 分钟内有效");

      mockMvc.perform(post("/message/template/version/preview")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"SMS_VERIFY_CODE\","
                  + "\"content\":\"您的验证码为 123456，5 分钟内有效\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data").value("您的验证码为 123456，5 分钟内有效"));

      verify(templateVersionService, times(1)).preview(any(TemplatePreviewDTO.class));
    }
  }

  @Nested
  @DisplayName("POST /message/template/version/testSend - testSend")
  class TestSend {

    @Test
    @DisplayName("试发模板成功时返回 200 + 发送结果")
    void shouldReturnSendResult() throws Exception {
      MessageSendResultVO result = MessageSendResultVO.ok("SMS", "trace-001");

      TemplateTestSendDTO dto = new TemplateTestSendDTO();
      dto.setTemplateCode("SMS_VERIFY_CODE");
      dto.setTestReceiver("1*********0");

      when(templateVersionService.testSend(any(TemplateTestSendDTO.class))).thenReturn(result);

      mockMvc.perform(post("/message/template/version/testSend")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"SMS_VERIFY_CODE\","
                  + "\"testReceiver\":\"1*********0\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.isSuccess").value(true))
          .andExpect(jsonPath("$.data.traceId").value("trace-001"))
          .andExpect(jsonPath("$.data.status").value("SUCCESS"));

      verify(templateVersionService, times(1)).testSend(any(TemplateTestSendDTO.class));
    }
  }
}
