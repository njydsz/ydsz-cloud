package com.njydsz.generator.web.controller;

import java.util.List;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.service.TemplateGroupService;
import com.njydsz.generator.service.TemplateService;
import com.njydsz.generator.vo.DiffLineVO;
import com.njydsz.generator.vo.TemplateValidateVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * <p><b>补充覆盖（26.11.06）：</b>原文件只覆盖了 7 个端点的 200 路径，未覆盖响应字段、
 * 搜索/更新/diff 三个端点、参数校验负例与失败路径。本次补齐：
 *
 * <ul>
 *   <li>分组 — 列表/激活/创建字段透出、删除系统分组被拒（IllegalStateException → 500）
 *   <li>模板 — 列表字段透出、更新内容、按关键字搜索、关键字为空回落全量
 *   <li>校验/对比 — validateTemplate 的 isValid/errorLine 字段、diffTemplates 的 changeType
 *   <li>负例 — @Valid 校验失败 400、Service 抛业务异常错误码透传
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class TemplateControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private TemplateGroupService groupService;

  private TemplateService templateService;

  private TemplateController controller;

  @BeforeEach
  void setUp() {
    groupService = mock(TemplateGroupService.class);
    templateService = mock(TemplateService.class);
    controller = new TemplateController(groupService, templateService);
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

    @Test
    @DisplayName("分组条目应透出 id/name/description/isActive 字段")
    void should_expose_group_fields() throws Exception {
      GenTemplateGroup group = new GenTemplateGroup();
      group.setId("group-001");
      group.setName("默认分组");
      group.setDescription("DDD 标准栈");
      group.setIsActive(true);
      when(groupService.listAll()).thenReturn(List.of(group));

      mockMvc.perform(get("/generator/groups"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].id").value("group-001"))
          .andExpect(jsonPath("$.data[0].name").value("默认分组"))
          .andExpect(jsonPath("$.data[0].description").value("DDD 标准栈"))
          .andExpect(jsonPath("$.data[0].isActive").value(true));
    }

    @Test
    @DisplayName("无分组时应返回空数组")
    void should_return_empty_array_when_no_groups() throws Exception {
      when(groupService.listAll()).thenReturn(List.of());

      mockMvc.perform(get("/generator/groups"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data.length()").value(0));
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

    @Test
    @DisplayName("未配置激活分组时应返回 200 且无 data 字段")
    void should_return_200_without_data_when_no_active_group() throws Exception {
      when(groupService.getActive()).thenReturn(null);

      mockMvc.perform(get("/generator/groups/active"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());
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

      verify(groupService).activate("group-001");
    }

    @Test
    @DisplayName("分组不存在时（IllegalStateException）应冒泡为 ServletException")
    void should_bubble_when_group_missing() throws Exception {
      doThrow(new IllegalStateException("分组不存在: group-404"))
          .when(groupService).activate("group-404");

      // standaloneSetup 未装配 advice：IllegalStateException 无 @ResponseStatus 映射，
      // 冒泡为 ServletException（生产环境由 MvcExceptionHandler 映射为 500）
      assertThat(catchThrowable(() -> mockMvc.perform(
              post("/generator/groups/{id}/activate", "group-404"))))
          .isInstanceOf(ServletException.class)
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("分组不存在: group-404");
    }
  }

  @Nested
  @DisplayName("POST /generator/groups")
  class CreateGroup {

    @Test
    @DisplayName("应返回 200 并透传创建后的实体")
    void should_return_200_with_created_group() throws Exception {
      GenTemplateGroup created = new GenTemplateGroup();
      created.setId("group-new");
      created.setName("新分组");
      when(groupService.create(any(GenTemplateGroup.class))).thenReturn(created);

      mockMvc.perform(post("/generator/groups")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"新分组\",\"description\":\"测试\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("group-new"))
          .andExpect(jsonPath("$.data.name").value("新分组"));
    }

    @Test
    @DisplayName("name 为空应返回 400 且不调用 Service")
    void should_return_400_when_name_blank() throws Exception {
      mockMvc.perform(post("/generator/groups")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"description\":\"缺名称\"}"))
          .andExpect(status().isBadRequest());

      verify(groupService, never()).create(any(GenTemplateGroup.class));
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

      verify(groupService).deleteById("group-001");
    }

    @Test
    @DisplayName("删除系统内置分组应被 Service 拒绝（IllegalStateException 冒泡）")
    void should_reject_deleting_system_group() throws Exception {
      doThrow(new IllegalStateException("系统内置分组不可删除: default"))
          .when(groupService).deleteById("group-system");

      assertThat(catchThrowable(() -> mockMvc.perform(
              delete("/generator/groups/{id}", "group-system"))))
          .isInstanceOf(ServletException.class)
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("系统内置分组不可删除: default");
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

    @Test
    @DisplayName("模板条目应透出 id/fileName/content 字段")
    void should_expose_template_fields() throws Exception {
      GenTemplate template = new GenTemplate();
      template.setId("tpl-001");
      template.setFileName("Controller.java.vm");
      template.setContent("public class ${className} {}");
      when(templateService.listByGroup("group-001")).thenReturn(List.of(template));

      mockMvc.perform(get("/generator/templates").param("groupId", "group-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].id").value("tpl-001"))
          .andExpect(jsonPath("$.data[0].fileName").value("Controller.java.vm"))
          .andExpect(jsonPath("$.data[0].content").value("public class ${className} {}"));
    }

    @Test
    @DisplayName("缺少 groupId 应返回 400 且不调用 Service")
    void should_return_400_when_group_id_missing() throws Exception {
      mockMvc.perform(get("/generator/templates"))
          .andExpect(status().isBadRequest());

      verify(templateService, never()).listByGroup(anyString());
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

    @Test
    @DisplayName("模板不存在时应返回 200 且无 data 字段")
    void should_return_200_without_data_when_template_missing() throws Exception {
      when(templateService.getById("tpl-404")).thenReturn(null);

      mockMvc.perform(get("/generator/templates/{id}", "tpl-404"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").doesNotExist());
    }
  }

  @Nested
  @DisplayName("POST /generator/templates/update")
  class UpdateTemplate {

    @Test
    @DisplayName("应返回 200 并透传更新后的模板")
    void should_return_200_with_updated_template() throws Exception {
      GenTemplate updated = new GenTemplate();
      updated.setId("tpl-001");
      updated.setFileName("Controller.java.vm");
      updated.setContent("public class ${className} { // v2 }");
      when(templateService.updateContent(any(GenTemplate.class))).thenReturn(updated);

      // GenTemplate.groupId 标注 @NotBlank：缺 groupId 时 standaloneSetup 直接返回 400（空体），
      // 因此这里必须带上 groupId 才能走到 Service
      mockMvc.perform(post("/generator/templates/update")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"id\":\"tpl-001\",\"groupId\":\"group-001\","
                  + "\"fileName\":\"Controller.java.vm\","
                  + "\"content\":\"public class ${className} { // v2 }\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.id").value("tpl-001"))
          .andExpect(jsonPath("$.data.content").value("public class ${className} { // v2 }"));
    }

    @Test
    @DisplayName("id 为空应返回 400 且不调用 Service")
    void should_return_400_when_id_blank() throws Exception {
      mockMvc.perform(post("/generator/templates/update")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"fileName\":\"Controller.java.vm\",\"content\":\"body\"}"))
          .andExpect(status().isBadRequest());

      verify(templateService, never()).updateContent(any(GenTemplate.class));
    }
  }

  @Nested
  @DisplayName("GET /generator/templates/search")
  class SearchTemplates {

    @Test
    @DisplayName("应返回 200 并把 groupId/keyword 透传给 Service")
    void should_return_200_and_pass_criteria() throws Exception {
      when(templateService.search("group-001", "Controller")).thenReturn(List.of());

      mockMvc.perform(get("/generator/templates/search")
              .param("groupId", "group-001")
              .param("keyword", "Controller"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(templateService).search("group-001", "Controller");
    }

    @Test
    @DisplayName("缺少 keyword 应返回 400 且不调用 Service")
    void should_return_400_when_keyword_missing() throws Exception {
      mockMvc.perform(get("/generator/templates/search").param("groupId", "group-001"))
          .andExpect(status().isBadRequest());

      verify(templateService, never()).search(anyString(), anyString());
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

    @Test
    @DisplayName("校验失败时应透出 isValid=false + errorLine")
    void should_expose_invalid_result_with_error_line() throws Exception {
      TemplateValidateVO vo = new TemplateValidateVO();
      vo.setIsValid(false);
      vo.setErrorMessage("解析失败");
      vo.setErrorLine(3);
      when(templateService.validateTemplate("#if(")).thenReturn(vo);

      mockMvc.perform(
              post("/generator/templates/validate")
                  .contentType(MediaType.TEXT_PLAIN)
                  .content("#if("))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.isValid").value(false))
          .andExpect(jsonPath("$.data.errorMessage").value("解析失败"))
          .andExpect(jsonPath("$.data.errorLine").value(3));
    }
  }

  @Nested
  @DisplayName("POST /generator/templates/diff")
  class DiffTemplates {

    @Test
    @DisplayName("应返回 200 并透出 changeType 字段")
    void should_return_200_with_diff_lines() throws Exception {
      when(templateService.diffTemplate("a\nb", "a\nc")).thenReturn(List.of(
          DiffLineVO.builder().lineNumber(1).oldContent("a").newContent("a")
              .changeType("UNCHANGED").build(),
          DiffLineVO.builder().lineNumber(2).oldContent("b").newContent("c")
              .changeType("REMOVED").build()));

      mockMvc.perform(post("/generator/templates/diff")
              .param("oldContent", "a\nb")
              .param("newContent", "a\nc"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(2))
          .andExpect(jsonPath("$.data[0].changeType").value("UNCHANGED"))
          .andExpect(jsonPath("$.data[1].changeType").value("REMOVED"));
    }

    @Test
    @DisplayName("缺少 oldContent 应返回 400 且不调用 Service")
    void should_return_400_when_old_content_missing() throws Exception {
      mockMvc.perform(post("/generator/templates/diff").param("newContent", "a"))
          .andExpect(status().isBadRequest());

      verify(templateService, never()).diffTemplate(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("异常信封（MvcExceptionHandler 已装配）")
  class ErrorEnvelope {

    private MockMvc adviceMockMvc;

    @BeforeEach
    void setUpAdvice() {
      adviceMockMvc =
          standaloneSetupWithAdvice(
              new MvcExceptionHandler(
                  new MockEnvironment(),
                  null,
                  absentProvider(),
                  absentProvider(),
                  absentProvider()),
              controller);
    }

    @Test
    @DisplayName("Service 抛 BusinessException 应透传错误码 + HTTP 状态")
    void should_expose_business_error_code() throws Exception {
      when(templateService.getById("tpl-err"))
          .thenThrow(BusinessException.of(CoreExceptionCode.PARAM_ERROR));

      adviceMockMvc.perform(get("/generator/templates/{id}", "tpl-err"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01052"));
    }

    @Test
    @DisplayName("Service 抛系统异常应返回 500 + 错误码")
    void should_expose_system_error_code() throws Exception {
      doThrow(BusinessException.of(CoreExceptionCode.SYSTEM_ERROR))
          .when(groupService).deleteById("group-boom");

      adviceMockMvc.perform(delete("/generator/groups/{id}", "group-boom"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  /**
   * "容器里没有这个 bean" 的 ObjectProvider，用于脱离 Spring 容器手工装配 advice。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> org.springframework.beans.factory.ObjectProvider<T> absentProvider() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }
    };
  }
}
