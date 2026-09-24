package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.server.service.SpaceTemplateApplicationService;

/**
 * 空间模板 Controller
 *
 * <p><b>S4-P3-02：文档模板体系</b>
 *
 * <p>提供预定义空间结构模板的查询和管理 API。
 *
 * <pre>
 *   GET    /api/nextwiki/templates               - 查询模板列表
 *   GET    /api/nextwiki/templates/{templateId}  - 获取模板详情
 *   POST   /api/nextwiki/templates               - 创建自定义模板
 *   PUT    /api/nextwiki/templates/{templateId}  - 更新模板
 *   DELETE /api/nextwiki/templates/{templateId}  - 删除模板
 *   POST   /api/nextwiki/templates/{templateId}/use - 使用模板创建空间
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ApiVersion("26.09.01")
@Slf4j
@RestController
@RequestMapping("/nextwiki/templates")
@RequiredArgsConstructor
@Tag(name = "空间模板管理", description = "S4-P3-02：文档模板体系")
public class SpaceTemplateController {

  private final SpaceTemplateApplicationService spaceTemplateApplicationService;

  /**
   * 查询可用模板列表。
   *
   * <p>返回系统公开模板和当前租户自定义模板的列表。支持按分类筛选。
   * 系统模板对所有租户可见可用，自定义模板仅创建者所属租户可见。
   *
   * @param category 模板分类（可选，如 general / project / meeting / knowledge）
   * @return 统一响应结果，data 为 {@link SpaceTemplateDTO} 列表
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
   * <p>返回模板的完整信息，包含名称、描述、分类和结构 JSON（定义目录树）。
   * 系统模板和当前租户自定义模板均可查询。
   *
   * @param templateId 模板 ID
   * @return 统一响应结果，data 为 {@link SpaceTemplateDTO}；不存在时返回 404
   */
  @GetMapping("/{templateId}")
  @Operation(summary = "获取模板详情")
  public YdszResponse<SpaceTemplateDTO> getTemplate(@PathVariable String templateId) {
    SpaceTemplateDTO template = spaceTemplateApplicationService.getTemplate(templateId);
    return YdszResponse.success(template);
  }

  /**
   * 创建租户自定义模板。
   *
   * <p>基于 structureJson 定义的目录树结构创建自定义模板。
   * 系统模板不可通过此接口创建，仅管理员可通过数据脚本初始化。
   *
   * @param request 创建请求（name: 模板名称，description: 描述，category: 分类，structureJson: 模板结构 JSON）
   * @param userId 当前用户 ID（作为模板创建者）
   * @return 统一响应结果，data 为新创建的 {@link SpaceTemplateDTO}
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
   * 更新租户自定义模板。
   *
   * <p>更新自定义模板的名称、描述、分类和结构 JSON。系统内置模板（system 级别）不可修改。
   * 仅模板创建者或租户管理员可执行。
   *
   * @param templateId 模板 ID
   * @param request 更新请求（name: 模板名称，description: 描述，category: 分类，structureJson: 模板结构 JSON）
   * @param userId 当前用户 ID（仅模板创建者/管理员可执行）
   * @return 统一响应结果，data 为更新后的 {@link SpaceTemplateDTO}
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
   * 删除租户自定义模板。
   *
   * <p>删除指定的自定义模板。系统内置模板（system 级别）不允许删除。
   * 仅模板创建者或租户管理员可执行。已通过该模板创建的空间不受影响。
   *
   * @param templateId 模板 ID
   * @param userId 当前用户 ID（仅模板创建者/管理员可执行）
   * @return 统一响应结果，data 为 true 表示删除成功
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
   * 基于模板创建新空间。
   *
   * <p>使用预定义的空间模板创建新空间，自动按模板结构 JSON 在空间内创建目录树。
   * 创建者自动成为空间所有者。模板可以是系统模板或当前租户自定义模板。
   *
   * @param templateId 模板 ID
   * @param request 使用模板请求（spaceName: 新空间名称，必填且租户内唯一）
   * @param userId 当前用户 ID（自动成为新空间所有者）
   * @return 统一响应结果，data 为新创建的 {@link SpaceVO}
   */
  @PostMapping("/{templateId}/use")
  @Operation(summary = "使用模板创建空间", description = "基于模板预定义结构创建新空间")
  public YdszResponse<SpaceVO> useTemplate(
      @PathVariable String templateId,
      @Valid @RequestBody UseTemplateRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceVO space =
        spaceTemplateApplicationService.createSpaceFromTemplate(templateId, request.getSpaceName(), userId);
    return YdszResponse.success(space);
  }

  // ==================== 内部请求 DTO ====================

  /** 创建模板请求 */
  @lombok.Data
  @Schema(description = "创建模板请求")
  public static class CreateTemplateRequest {
    @Schema(description = "模板名称", required = true)
    @jakarta.validation.constraints.NotBlank(message = "模板名称不能为空")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "模板分类：general / project / meeting / knowledge")
    private String category;

    @Schema(description = "模板结构JSON（定义目录树）", required = true)
    @jakarta.validation.constraints.NotBlank(message = "模板结构不能为空")
    private String structureJson;
  }

  /** 更新模板请求 */
  @lombok.Data
  @Schema(description = "更新模板请求")
  public static class UpdateTemplateRequest {
    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "模板分类")
    private String category;

    @Schema(description = "模板结构JSON")
    private String structureJson;
  }

  /** 使用模板请求 */
  @lombok.Data
  @Schema(description = "使用模板创建空间请求")
  public static class UseTemplateRequest {
    @Schema(description = "新空间名称", required = true)
    @jakarta.validation.constraints.NotBlank(message = "空间名称不能为空")
    private String spaceName;
  }
}
