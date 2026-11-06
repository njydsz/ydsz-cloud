package com.njydsz.generator.web.controller;


import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.service.TemplateGroupService;
import com.njydsz.generator.service.TemplateService;
import com.njydsz.generator.vo.TemplateValidateVO;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link TemplateController} Smoke Test。
 *
 * <p>验证模板管理端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class TemplateControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private TemplateGroupService groupService;

  private TemplateService templateService;

  @BeforeEach
  void setUp() {
    groupService = mock(TemplateGroupService.class);
    templateService = mock(TemplateService.class);
    TemplateController controller = new TemplateController(groupService, templateService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /generator/groups")
  class ListGroups {

    @Test
    @DisplayName("should return 200 when list groups succeeds")
    void should_return_200_when_list_groups_succeeds() throws Exception {
      GenTemplateGroup group = new GenTemplateGroup();
      group.setId("group-001");
      group.setName("默认分组");
      when(groupService.listAll()).thenReturn(List.of(group));

      mockMvc.perform(get("/generator/groups"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/groups/active")
  class GetActiveGroup {

    @Test
    @DisplayName("should return 200 when get active group succeeds")
    void should_return_200_when_get_active_group_succeeds() throws Exception {
      GenTemplateGroup group = new GenTemplateGroup();
      group.setId("group-001");
      when(groupService.getActive()).thenReturn(group);

      mockMvc.perform(get("/generator/groups/active"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/groups/{id}/activate")
  class ActivateGroup {

    @Test
    @DisplayName("should return 200 when activate group succeeds")
    void should_return_200_when_activate_group_succeeds() throws Exception {
      mockMvc.perform(post("/generator/groups/{id}/activate", "group-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("DELETE /generator/groups/{id}")
  class DeleteGroup {

    @Test
    @DisplayName("should return 200 when delete group succeeds")
    void should_return_200_when_delete_group_succeeds() throws Exception {
      mockMvc.perform(delete("/generator/groups/{id}", "group-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/templates")
  class ListTemplates {

    @Test
    @DisplayName("should return 200 when list templates succeeds")
    void should_return_200_when_list_templates_succeeds() throws Exception {
      GenTemplate template = new GenTemplate();
      template.setId("tpl-001");
      template.setFileName("Controller.java.vm");
      when(templateService.listByGroup("group-001")).thenReturn(List.of(template));

      mockMvc.perform(get("/generator/templates").param("groupId", "group-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /generator/templates/{id}")
  class GetTemplate {

    @Test
    @DisplayName("should return 200 when get template succeeds")
    void should_return_200_when_get_template_succeeds() throws Exception {
      GenTemplate template = new GenTemplate();
      template.setId("tpl-001");
      when(templateService.getById("tpl-001")).thenReturn(template);

      mockMvc.perform(get("/generator/templates/{id}", "tpl-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("POST /generator/templates/validate")
  class ValidateTemplate {

    @Test
    @DisplayName("should return 200 when validate template succeeds")
    void should_return_200_when_validate_template_succeeds() throws Exception {
      TemplateValidateVO vo = new TemplateValidateVO();
      vo.setIsValid(true);
      when(templateService.validateTemplate("#set($x = 1)")).thenReturn(vo);

      mockMvc.perform(
              post("/generator/templates/validate")
                  .contentType(MediaType.TEXT_PLAIN)
                  .content("#set($x = 1)"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
