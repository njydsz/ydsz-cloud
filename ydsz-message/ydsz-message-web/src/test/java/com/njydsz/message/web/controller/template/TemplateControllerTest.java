package com.njydsz.message.web.controller.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.TemplateAuditDTO;
import com.njydsz.message.domain.dto.TemplateCreateDTO;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.service.TemplateService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link TemplateController} 的 MockMvc 集成测试。
 *
 * <p>验证消息模板的完整 CRUD + 审核流程：创建 / 更新 / 删除 / 分页查询 / 详情 / 审核。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@WebMvcTest(TemplateController.class)
class TemplateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private TemplateService templateService;

  @Nested
  @DisplayName("POST /message/template 创建接口测试")
  class CreateEndpoint {

    @Test
    @DisplayName("创建模板应返回模板详情 VO")
    void create_shouldReturnTemplateVo() throws Exception {
      MsgTemplateVO vo = new MsgTemplateVO();
      vo.setId("tpl-001");
      vo.setTemplateCode("SMS_VERIFY");
      vo.setChannel("SMS");
      vo.setContent("您的验证码是 ${code}");

      when(templateService.create(any(TemplateCreateDTO.class))).thenReturn(vo);

      TemplateCreateDTO dto = new TemplateCreateDTO();
      dto.setTemplateCode("SMS_VERIFY");
      dto.setChannel("SMS");
      dto.setContent("您的验证码是 ${code}");

      mockMvc.perform(post("/message/template")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.id").value("tpl-001"))
          .andExpect(jsonPath("$.data.templateCode").value("SMS_VERIFY"));
    }
  }

  @Nested
  @DisplayName("PUT /message/template/{id} 更新接口测试")
  class UpdateEndpoint {

    @Test
    @DisplayName("更新模板应返回更新后的 VO")
    void update_shouldReturnUpdatedVo() throws Exception {
      MsgTemplateVO vo = new MsgTemplateVO();
      vo.setId("tpl-001");
      vo.setTemplateCode("SMS_VERIFY");
      vo.setContent("更新后的内容");

      when(templateService.update(eq("tpl-001"), any(TemplateCreateDTO.class))).thenReturn(vo);

      TemplateCreateDTO dto = new TemplateCreateDTO();
      dto.setContent("更新后的内容");

      mockMvc.perform(put("/message/template/tpl-001")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.content").value("更新后的内容"));
    }
  }

  @Nested
  @DisplayName("DELETE /message/template/{id} 删除接口测试")
  class DeleteEndpoint {

    @Test
    @DisplayName("删除模板应返回操作成功")
    void delete_shouldReturnSuccess() throws Exception {
      doNothing().when(templateService).delete("tpl-001");

      mockMvc.perform(delete("/message/template/tpl-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(200));
    }
  }

  @Nested
  @DisplayName("GET /message/template/{id} 详情接口测试")
  class GetByIdEndpoint {

    @Test
    @DisplayName("查询模板详情应返回完整 VO")
    void getById_shouldReturnTemplateVo() throws Exception {
      MsgTemplateVO vo = new MsgTemplateVO();
      vo.setId("tpl-002");
      vo.setTemplateCode("EMAIL_WELCOME");
      vo.setChannel("EMAIL");
      vo.setStatus("ENABLED");

      when(templateService.getById("tpl-002")).thenReturn(vo);

      mockMvc.perform(get("/message/template/tpl-002"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.id").value("tpl-002"))
          .andExpect(jsonPath("$.data.templateCode").value("EMAIL_WELCOME"))
          .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }
  }

  @Nested
  @DisplayName("GET /message/template/page 分页接口测试")
  class PageEndpoint {

    @Test
    @DisplayName("分页查询应返回模板分页列表")
    void page_shouldReturnPagedTemplates() throws Exception {
      MsgTemplateVO vo = new MsgTemplateVO();
      vo.setId("tpl-001");
      vo.setTemplateCode("SMS_VERIFY");

      PageResponse<List<MsgTemplateVO>> pageResponse = new PageResponse<>();
      pageResponse.setData(List.of(vo));
      pageResponse.setTotal(1L);

      when(templateService.page(any(TemplateQueryDTO.class))).thenReturn(pageResponse);

      mockMvc.perform(get("/message/template/page")
              .param("pageNum", "1")
              .param("pageSize", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.data[0].templateCode").value("SMS_VERIFY"));
    }
  }

  @Nested
  @DisplayName("POST /message/template/{id}/audit 审核接口测试")
  class AuditEndpoint {

    @Test
    @DisplayName("审核模板应返回操作成功")
    void audit_shouldReturnSuccess() throws Exception {
      doNothing().when(templateService).audit(eq("tpl-001"), any(TemplateAuditDTO.class));

      TemplateAuditDTO dto = new TemplateAuditDTO();
      dto.setAuditStatus("APPROVED");
      dto.setAuditRemark("审核通过");

      mockMvc.perform(post("/message/template/tpl-001/audit")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(200));
    }
  }
}
