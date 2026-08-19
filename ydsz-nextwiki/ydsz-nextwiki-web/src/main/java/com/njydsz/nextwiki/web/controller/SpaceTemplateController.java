package com.njydsz.nextwiki.web.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.response.YdszResponse;
import com.njydsz.common.web.constants.AuthHeaderConstants;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.server.service.SpaceTemplateApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 空间模板 Controller
 *
 * <p><b>S4-P3-02：文档模板体系</b>
 *
 * <p>提供预定义空间结构模板的查询和管理 API。
 *
 * <pre>
 *   GET    /api/v1/nextwiki/templates               - 查询模板列表
 *   GET    /api/v1/nextwiki/templates/{templateId}  - 获取模板详情
 *   POST   /api/v1/nextwiki/templates               - 创建自定义模板
 *   PUT    /api/v1/nextwiki/templates/{templateId}  - 更新模板
 *   DELETE /api/v1/nextwiki/templates/{templateId}  - 删除模板
 *   POST   /api/v1/nextwiki/templates/{templateId}/use - 使用模板创建空间
 * </pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/templates")
@RequiredArgsConstructor
@Tag(name = "空间模板管理", description = "S4-P3-02：文档模板体系")
public class SpaceTemplateController {

  private final SpaceTemplateApplicationService spaceTemplateApplicationService;

  /**
   * 查询可用模板列表。
   *
   * @param category 模板分类（可选）
   * @return 模板DTO列表
   */
  @GetMapping
  @Operation(summary = "查询模板列表", description = "查询可用模板（系统公开模板 + 租户自定义模板）")
  public YdszResponse<List<SpaceTemplateDTO>> listTemplates(
      @RequestParam(required = false) String category) {

    List<SpaceTemplateDTO> templates = spaceTemplateApplicationService.listTemplates(category);
    return YdszResponse.success(templates);
  }

  /**
   * 获取模板详情。
   *
   * @param templateId 模板ID
   * @return 模板DTO
   */
  @GetMapping("/{templateId}")
  @Operation(summary = "获取模板详情")
  public YdszResponse<SpaceTemplateDTO> getTemplate(@PathVariable String templateId) {
    SpaceTemplateDTO template = spaceTemplateApplicationService.getTemplate(templateId);
    return YdszResponse.success(template);
  }

  /**
   * 创建自定义模板。
   *
   * @param request 创建请求
   * @param userId 当前用户ID
   * @return 新创建的模板DTO
   */
  @PostMapping
  @Operation(summary = "创建模板", description = "创建租户自定义模板")
  public YdszResponse<SpaceTemplateDTO> createTemplate(
      @Valid @RequestBody CreateTemplateRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceTemplateDTO template = spaceTemplateApplicationService.createTemplate(
        request.getName(), request.getDescription(), request.getCategory(),
        request.getStructureJson(), userId);
    return YdszResponse.success(template);
  }

  /**
   * 更新自定义模板。
   *
   * @param templateId 模板ID
   * @param request 更新请求
   * @param userId 当前用户ID
   * @return 更新后的模板DTO
   */
  @PutMapping("/{templateId}")
  @Operation(summary = "更新模板", description = "更新租户自定义模板（系统模板不可修改）")
  public YdszResponse<SpaceTemplateDTO> updateTemplate(
      @PathVariable String templateId,
      @Valid @RequestBody UpdateTemplateRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceTemplateDTO template = spaceTemplateApplicationService.updateTemplate(
        templateId, request.getName(), request.getDescription(), request.getCategory(),
        request.getStructureJson(), userId);
    return YdszResponse.success(template);
  }

  /**
   * 删除自定义模板。
   *
   * @param templateId 模板ID
   * @param userId 当前用户ID
   * @return 操作结果
   */
  @DeleteMapping("/{templateId}")
  @Operation(summary = "删除模板", description = "删除租户自定义模板（系统模板不可删除）")
  public YdszResponse<Boolean> deleteTemplate(
      @PathVariable String templateId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    spaceTemplateApplicationService.deleteTemplate(templateId, userId);
    return YdszResponse.success(true);
  }

  /**
   * 使用模板创建空间。
   *
   * @param templateId 模板ID
   * @param request 创建空间请求
   * @param userId 当前用户ID
   * @return 新创建的空间视图
   */
  @PostMapping("/{templateId}/use")
  @Operation(summary = "使用模板创建空间", description = "基于模板预定义结构创建新空间")
  public YdszResponse<com.njydsz.nextwiki.domain.vo.SpaceVO> useTemplate(
      @PathVariable String templateId,
      @Valid @RequestBody UseTemplateRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    com.njydsz.nextwiki.domain.vo.SpaceVO space =
        spaceTemplateApplicationService.createSpaceFromTemplate(templateId, request.getSpaceName(), userId);
    return YdszResponse.success(space);
  }

  // ==================== 内部请求 DTO ====================

  /** 创建模板请求 */
  @lombok.Data
  @io.swagger.v3.oas.annotations.media.Schema(description = "创建模板请求")
  public static class CreateTemplateRequest {
    @io.swagger.v3.oas.annotations.media.Schema(description = "模板名称", required = true)
    @jakarta.validation.constraints.NotBlank(message = "模板名称不能为空")
    private String name;

    @io.swagger.v3.oas.annotations.media.Schema(description = "模板描述")
    private String description;

    @io.swagger.v3.oas.annotations.media.Schema(description = "模板分类：general / project / meeting / knowledge")
    private String category;

    @io.swagger.v3.oas.annotations.media.Schema(description = "模板结构JSON（定义目录树）", required = true)
    @jakarta.validation.constraints.NotBlank(message = "模板结构不能为空")
    private String structureJson;
  }

  /** 更新模板请求 */
  @lombok.Data
  @io.swagger.v3.oas.annotations.media.Schema(description = "更新模板请求")
  public static class UpdateTemplateRequest {
    @io.swagger.v3.oas.annotations.media.Schema(description = "模板名称")
    private String name;

    @io.swagger.v3.oas.annotations.media.Schema(description = "模板描述")
    private String description;

    @io.swagger.v3.oas.annotations.media.Schema(description = "模板分类")
    private String category;

    @io.swagger.v3.oas.annotations.media.Schema(description = "模板结构JSON")
    private String structureJson;
  }

  /** 使用模板请求 */
  @lombok.Data
  @io.swagger.v3.oas.annotations.media.Schema(description = "使用模板创建空间请求")
  public static class UseTemplateRequest {
    @io.swagger.v3.oas.annotations.media.Schema(description = "新空间名称", required = true)
    @jakarta.validation.constraints.NotBlank(message = "空间名称不能为空")
    private String spaceName;
  }
}
