package com.njydsz.generator.controller;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.service.TemplateGroupService;
import com.njydsz.generator.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模板管理 REST 控制器（含分组管理）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generator")
@RequiredArgsConstructor
public class TemplateController {

  private final TemplateGroupService groupService;
  private final TemplateService templateService;

  // ══════════════ 分组管理 ══════════════

  /**
   * 查询全部分组。
   *
   * @return 分组列表
   */
  @GetMapping("/groups")
  public YdszResponse<List<GenTemplateGroup>> listGroups() {
    return YdszResponse.success(groupService.listAll());
  }

  /**
   * 获取当前激活分组。
   *
   * @return 激活分组
   */
  @GetMapping("/groups/active")
  public YdszResponse<GenTemplateGroup> getActiveGroup() {
    return YdszResponse.success(groupService.getActive());
  }

  /**
   * 激活指定分组。
   *
   * @param id 分组 ID
   * @return 操作结果
   */
  @PostMapping("/groups/{id}/activate")
  public YdszResponse<Void> activateGroup(@PathVariable Long id) {
    groupService.activate(id);
    return YdszResponse.success(null);
  }

  /**
   * 创建分组。
   *
   * @param group 分组实体
   * @return 持久化后实体
   */
  @PostMapping("/groups")
  public YdszResponse<GenTemplateGroup> createGroup(@RequestBody GenTemplateGroup group) {
    return YdszResponse.success(groupService.create(group));
  }

  /**
   * 删除分组。
   *
   * @param id 分组 ID
   * @return 操作结果
   */
  @DeleteMapping("/groups/{id}")
  public YdszResponse<Void> deleteGroup(@PathVariable Long id) {
    groupService.deleteById(id);
    return YdszResponse.success(null);
  }

  // ══════════════ 模板管理 ══════════════

  /**
   * 查询分组全部模板。
   *
   * @param groupId 分组 ID
   * @return 模板列表
   */
  @GetMapping("/templates")
  public YdszResponse<List<GenTemplate>> listTemplates(@RequestParam Long groupId) {
    return YdszResponse.success(templateService.listByGroup(groupId));
  }

  /**
   * 查询单个模板。
   *
   * @param id 模板 ID
   * @return 模板实体
   */
  @GetMapping("/templates/{id}")
  public YdszResponse<GenTemplate> getTemplate(@PathVariable Long id) {
    return YdszResponse.success(templateService.getById(id));
  }

  /**
   * 更新模板内容。
   *
   * @param template 模板实体（含 id + content）
   * @return 更新后实体
   */
  @PostMapping("/templates/update")
  public YdszResponse<GenTemplate> updateTemplate(@RequestBody GenTemplate template) {
    return YdszResponse.success(templateService.updateContent(template));
  }

  /**
   * 搜索模板（按文件名/描述）。
   *
   * @param groupId 分组 ID
   * @param keyword 关键词
   * @return 匹配结果
   */
  @GetMapping("/templates/search")
  public YdszResponse<List<GenTemplate>> search(
      @RequestParam Long groupId, @RequestParam String keyword) {
    return YdszResponse.success(templateService.search(groupId, keyword));
  }
}
