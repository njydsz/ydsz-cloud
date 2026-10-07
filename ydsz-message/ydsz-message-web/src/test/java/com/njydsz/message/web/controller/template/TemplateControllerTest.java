package com.njydsz.message.web.controller.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.TemplateCreateDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.service.TemplateService;

/**
 * TemplateController 集成测试。
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup()} 构建轻量级 Web 层测试，通过 Mockito {@code @Mock} 模拟
 * {@link TemplateService} 依赖，验证 HTTP 路由、请求参数绑定、响应格式正确性。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {

  private MockMvc mockMvc;

  @Mock
  private TemplateService templateService;

  @InjectMocks
  private TemplateController templateController;

  private MsgTemplateVO sampleTemplate;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(templateController).build();

    sampleTemplate = new MsgTemplateVO();
    sampleTemplate.setId("tpl-001");
    sampleTemplate.setTemplateCode("SMS_VERIFY_CODE");
    sampleTemplate.setChannel("SMS");
    sampleTemplate.setLocale("zh_CN");
    sampleTemplate.setSubject("验证码通知");
    sampleTemplate.setContent("您的验证码为 ${code}，5 分钟内有效");
    sampleTemplate.setStatus("ENABLED");
    sampleTemplate.setAuditStatus("APPROVED");
    sampleTemplate.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
  }

  @Nested
  @DisplayName("POST /message/template - create")
  class Create {

    @Test
    @DisplayName("创建模板成功时返回 200 + 模板 VO")
    void shouldReturnTemplateOnSuccess() throws Exception {
      TemplateCreateDTO dto = new TemplateCreateDTO();
      dto.setTemplateCode("SMS_VERIFY_CODE");
      dto.setChannel("SMS");
      dto.setContent("您的验证码为 ${code}，5 分钟内有效");

      when(templateService.create(any(TemplateCreateDTO.class))).thenReturn(sampleTemplate);

      mockMvc.perform(post("/message/template")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"SMS_VERIFY_CODE\",\"channel\":\"SMS\","
                  + "\"content\":\"您的验证码为 ${code}，5 分钟内有效\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.id").value("tpl-001"))
          .andExpect(jsonPath("$.data.templateCode").value("SMS_VERIFY_CODE"))
          .andExpect(jsonPath("$.data.channel").value("SMS"));

      verify(templateService, times(1)).create(any(TemplateCreateDTO.class));
    }
  }

  @Nested
  @DisplayName("PUT /message/template/{id} - update")
  class Update {

    @Test
    @DisplayName("更新模板成功时返回 200 + 更新后模板 VO")
    void shouldReturnUpdatedTemplate() throws Exception {
      MsgTemplateVO updated = new MsgTemplateVO();
      updated.setId("tpl-001");
      updated.setTemplateCode("SMS_VERIFY_CODE");
      updated.setChannel("SMS");
      updated.setContent("更新后的验证码内容");

      when(templateService.update(eq("tpl-001"), any(TemplateCreateDTO.class))).thenReturn(updated);

      mockMvc.perform(put("/message/template/{id}", "tpl-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"templateCode\":\"SMS_VERIFY_CODE\",\"channel\":\"SMS\","
                  + "\"content\":\"更新后的验证码内容\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.content").value("更新后的验证码内容"));

      verify(templateService, times(1)).update(eq("tpl-001"), any(TemplateCreateDTO.class));
    }
  }

  @Nested
  @DisplayName("DELETE /message/template/{id} - delete")
  class Delete {

    @Test
    @DisplayName("删除模板成功时返回 200 + Void")
    void shouldReturnSuccessOnDelete() throws Exception {
      mockMvc.perform(delete("/message/template/{id}", "tpl-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(templateService, times(1)).delete("tpl-001");
    }
  }

  @Nested
  @DisplayName("GET /message/template/{id} - getById")
  class GetById {

    @Test
    @DisplayName("模板存在时返回 200 + MsgTemplateVO")
    void shouldReturnTemplateWhenExists() throws Exception {
      when(templateService.getById("tpl-001")).thenReturn(sampleTemplate);

      mockMvc.perform(get("/message/template/{id}", "tpl-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.id").value("tpl-001"))
          .andExpect(jsonPath("$.data.templateCode").value("SMS_VERIFY_CODE"))
          .andExpect(jsonPath("$.data.subject").value("验证码通知"));

      verify(templateService, times(1)).getById("tpl-001");
    }

    @Test
    @DisplayName("模板不存在时返回 200 + null data")
    void shouldReturnNullWhenTemplateNotFound() throws Exception {
      when(templateService.getById("tpl-404")).thenReturn(null);

      mockMvc.perform(get("/message/template/{id}", "tpl-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(templateService, times(1)).getById("tpl-404");
    }
  }

  @Nested
  @DisplayName("GET /message/template/page - page")
  class Page {

    @Test
    @DisplayName("分页查询返回 PageResponse 包装数据")
    void shouldReturnPagedResult() throws Exception {
      @SuppressWarnings("unchecked")
      PageResponse<List<MsgTemplateVO>> pageResult = new PageResponse<>();
      pageResult.setData(List.of(sampleTemplate));
      pageResult.setTotal(1L);
      pageResult.setPageNum(1L);
      pageResult.setPageSize(20L);

      when(templateService.page(any())).thenReturn(pageResult);

      mockMvc.perform(get("/message/template/page")
              .param("pageNum", "1")
              .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].id").value("tpl-001"))
          .andExpect(jsonPath("$.data.data[0].templateCode").value("SMS_VERIFY_CODE"));

      verify(templateService, times(1)).page(any());
    }
  }

  @Nested
  @DisplayName("POST /message/template/{id}/audit - audit")
  class Audit {

    @Test
    @DisplayName("审核模板成功时返回 200 + Void")
    void shouldReturnSuccessOnAudit() throws Exception {
      mockMvc.perform(post("/message/template/{id}/audit", "tpl-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"auditStatus\":\"APPROVED\",\"auditRemark\":\"审核通过\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(templateService, times(1)).audit(eq("tpl-001"), any());
    }
  }
}
