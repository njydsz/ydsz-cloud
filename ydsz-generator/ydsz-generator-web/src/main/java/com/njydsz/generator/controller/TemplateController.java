package com.njydsz.generator.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.service.TemplateGroupService;
import com.njydsz.generator.service.TemplateService;
import com.njydsz.generator.vo.DiffLineVO;
import com.njydsz.generator.vo.TemplateValidateVO;

/**
 * 模板管理 REST 控制器（含分组管理）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@Secured("ROLE_GENERATOR_USER")
@RestController
@RequestMapping("/generator")
@RequiredArgsConstructor
public class TemplateController {

  private final TemplateGroupService groupService;
  private final TemplateService templateService;

  // ══════════════ 分组管理 ══════════════

  /**
   * 查询全部分组。
   *
   * <p>模板分组用于管理不同技术栈的 Velocity 模板集合（如"Spring Boot 标准栈"、
   * "Vue3 + Element Plus"等），每个分组包含一组相互配套的前后端模板。
   *
   * @return 全部分组列表，每个元素包含 id、name、description、isActive 等
   */
  @GetMapping("/groups")
  public YdszResponse<List<GenTemplateGroup>> listGroups() {
    return YdszResponse.success(groupService.listAll());
  }

  /**
   * 获取当前激活的模板分组。
   *
   * <p>激活分组决定了代码生成和预览时使用的模板集合。
   *
   * @return 当前激活的分组实体；未配置时返回 null
   */
  @GetMapping("/groups/active")
  public YdszResponse<GenTemplateGroup> getActiveGroup() {
    return YdszResponse.success(groupService.getActive());
  }

  /**
   * 激活指定的模板分组。
   *
   * <p>同一时刻只有一个分组处于激活状态。激活后，代码生成和预览将使用该分组下的模板。
   *
   * @param id 分组 ID
   * @return 操作结果，成功时 data 为 null
   */
  @PostMapping("/groups/{id}/activate")
  @Audit(module = "模板管理", action = AuditAction.ENABLE, content = "'激活模板分组:' + #id")
  public YdszResponse<Void> activateGroup(@PathVariable Long id) {
    groupService.activate(id);
    return YdszResponse.success(null);
  }

  /**
   * 创建新的模板分组。
   *
   * <p>分组创建后需上传 Velocity 模板文件（通过模板管理接口），
   * 并激活后方可用于代码生成。
   *
   * @param group 分组实体，需包含 name（名称，非空）和 description（描述）
   * @return 持久化后的分组实体
   */
  @PostMapping("/groups")
  @Audit(module = "模板管理", action = AuditAction.CREATE, content = "'创建模板分组:' + #group.name")
  public YdszResponse<GenTemplateGroup> createGroup(@RequestBody GenTemplateGroup group) {
    return YdszResponse.success(groupService.create(group));
  }

  /**
   * 删除指定的模板分组。
   *
   * <p>删除分组将同时删除分组下所有 Velocity 模板文件。
   * 若删除的是当前激活分组，需先激活其他分组或将受影响。
   *
   * @param id 分组 ID
   * @return 操作结果，成功时 data 为 null
   */
  @DeleteMapping("/groups/{id}")
  @Audit(module = "模板管理", action = AuditAction.DELETE, content = "'删除模板分组:' + #id")
  public YdszResponse<Void> deleteGroup(@PathVariable Long id) {
    groupService.deleteById(id);
    return YdszResponse.success(null);
  }

  // ══════════════ 模板管理 ══════════════

  /**
   * 查询分组下的全部 Velocity 模板。
   *
   * <p>模板使用 Velocity 模板语法（{@code $variable}、{@code #if/#foreach} 等），
   * 变量占位符格式为 {@code ${tableName}}、${className}、${columns} 等。
   *
   * @param groupId 模板分组 ID，决定模板风格与目标技术栈
   * @return 模板列表，每个元素包含 id、fileName、content（Velocity 模板文本）、description 等
   */
  @GetMapping("/templates")
  public YdszResponse<List<GenTemplate>> listTemplates(@RequestParam Long groupId) {
    return YdszResponse.success(templateService.listByGroup(groupId));
  }

  /**
   * 查询单个 Velocity 模板详情。
   *
   * @param id 模板 ID
   * @return 模板实体，包含 fileName、content（Velocity 模板语法文本）、description 等
   */
  @GetMapping("/templates/{id}")
  public YdszResponse<GenTemplate> getTemplate(@PathVariable Long id) {
    return YdszResponse.success(templateService.getById(id));
  }

  /**
   * 更新 Velocity 模板内容。
   *
   * <p>模板使用 Velocity 语法：变量占位符格式为 {@code $variableName} 或 {@code ${variableName}}，
   * 控制结构包括 {@code #if}、{@code #foreach}、{@code #set} 等。
   * 常用内置变量：tableName、className、moduleName、columns（字段列表）、tableComment 等。
   *
   * @param template 模板实体，id 必填（指定更新目标），content 为新的 Velocity 模板文本
   * @return 更新后的模板实体
   */
  @PostMapping("/templates/update")
  @Audit(module = "模板管理", action = AuditAction.UPDATE, content = "'更新模板:' + #template.id")
  public YdszResponse<GenTemplate> updateTemplate(@RequestBody GenTemplate template) {
    return YdszResponse.success(templateService.updateContent(template));
  }

  /**
   * 在分组内按文件名或描述搜索 Velocity 模板。
   *
   * @param groupId 模板分组 ID
   * @param keyword 搜索关键词，匹配 fileName 或 description 字段，可为空（返回全部分组模板）
   * @return 匹配的模板列表，无匹配时返回空列表
   */
  @GetMapping("/templates/search")
  public YdszResponse<List<GenTemplate>> search(
      @RequestParam Long groupId, @RequestParam String keyword) {
    return YdszResponse.success(templateService.search(groupId, keyword));
  }

  // ══════════════ 模板编辑器增强 ══════════════

  /**
   * 校验 Velocity 模板语法的正确性。
   *
   * <p>检查 Velocity 模板中的变量引用（{@code $var}）、控制结构（{@code #if/#foreach/#set}）
   * 是否合法。用于在保存模板前发现语法错误，避免代码生成时失败。
   *
   * @param content Velocity 模板文本内容
   * @return 校验结果，包含 valid（是否通过）、errorLine（错误行号，通过时为 null）、
   *         errorMessage（错误描述，通过时为 null）
   */
  @PostMapping("/templates/validate")
  public YdszResponse<TemplateValidateVO> validateTemplate(@RequestBody String content) {
    return YdszResponse.success(templateService.validateTemplate(content));
  }

  /**
   * 对比两个版本的 Velocity 模板内容，生成逐行 diff 结果。
   *
   * <p>用于模板编辑器中查看修改前后的差异，辅助模板版本管理。
   *
   * @param oldContent 旧版本 Velocity 模板文本
   * @param newContent 新版本 Velocity 模板文本
   * @return diff 行列表，每行包含 lineNumber（行号）、lineContent（行文本）、
   *         changeType（变更类型：UNCHANGED 未变更 / ADDED 新增 / REMOVED 删除）
   */
  @PostMapping("/templates/diff")
  public YdszResponse<List<DiffLineVO>> diffTemplates(
      @RequestParam String oldContent, @RequestParam String newContent) {
    return YdszResponse.success(templateService.diffTemplate(oldContent, newContent));
  }
}
