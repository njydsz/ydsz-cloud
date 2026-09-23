package com.njydsz.nextwiki.web.controller.template;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.domain.entity.SpaceTemplate;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.service.SpaceTemplateApplicationService;

/**
 * 文件模板 REST API Controller（P2-3：文件模板三级可见性体系）。
 *
 * <p>允许用户将文件保存为模板、从模板创建新文件、管理模板列表。
 *
 * <p><b>三级可见性：</b>
 *
 * <ul>
 *   <li>system — 系统内置模板，所有租户可见可用（如"会议纪要"、"周报"、"OKR"）
 *   <li>org — 组织内可见模板，同租户用户共享
 *   <li>private — 私有模板，仅创建者可见可用
 * </ul>
 *
 * <pre>
 *   POST   /api/nextwiki/templates/file                   - 将文件保存为模板
 *   GET    /api/nextwiki/templates/file                   - 查询当前用户可见的文件模板列表
 *   POST   /api/nextwiki/templates/file/{templateId}/use  - 从模板创建新文件
 *   DELETE /api/nextwiki/templates/file/{templateId}      - 删除文件模板
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@ApiVersion("26.09.23")
@Slf4j
@RestController
@RequestMapping("/nextwiki/templates")
@RequiredArgsConstructor
@Tag(name = "文件模板", description = "文件级模板管理、三级可见性（system/org/private）")
public class FileTemplateController {

  /** 空间模板应用服务（复用模板基础设施） */
  private final SpaceTemplateApplicationService spaceTemplateApplicationService;

  /**
   * 将指定文件保存为模板。
   *
   * <p>复制源文件节点元数据创建一条 templateType=file 的模板记录，
   * 可见性默认 private（用户私有），可通过 visibility 参数调整。
   *
   * @param request 模板创建请求
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为创建的模板 ID
   */
  @PostMapping("/file")
  @Operation(summary = "将文件保存为模板")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public YdszResponse<String> createFromFile(
      @Valid @RequestBody CreateFileTemplateRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceTemplateDTO dto = new SpaceTemplateDTO();
    dto.setName(request.getName());
    dto.setDescription(request.getDescription());
    dto.setTemplateType(SpaceTemplate.TEMPLATE_TYPE_FILE);
    dto.setSourceNodeId(request.getSourceNodeId());
    dto.setVisibility(request.getVisibility() != null
        ? request.getVisibility()
        : SpaceTemplate.VISIBILITY_PRIVATE);
    dto.setCreatedBy(userId);
    dto.setTenantId(request.getTenantId());

    String templateId = spaceTemplateApplicationService.createTemplate(dto);
    log.info("[FileTemplateController] 文件保存为模板: templateId={}, sourceNodeId={}, userId={}",
        templateId, request.getSourceNodeId(), userId);
    return YdszResponse.success(templateId);
  }

  /**
   * 查询当前用户可见的文件模板列表。
   *
   * <p>自动按三级可见性过滤：system 模板全租户可见，org 模板仅同租户可见，private 模板仅创建者可见。
   *
   * @param userId 当前用户 ID
   * @param category 分类筛选（可选）
   * @return 统一响应结果，data 为可见模板列表
   */
  @GetMapping("/file")
  @Operation(summary = "查询文件模板列表（三级可见性自动过滤）")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VIEW)
  public YdszResponse<List<SpaceTemplateDTO>> listFileTemplates(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam(value = "category", required = false) String category) {

    List<SpaceTemplateDTO> templates = spaceTemplateApplicationService
        .listFileTemplates(userId, category);
    return YdszResponse.success(templates);
  }

  /**
   * 从模板创建新文件。
   *
   * <p>读取模板对应的源文件存储对象，复制为新的文件节点写入目标父目录。
   *
   * @param templateId 模板 ID
   * @param userId 当前用户 ID
   * @param parentId 目标父目录 ID
   * @return 统一响应结果，data 为新创建的文件节点信息
   */
  @PostMapping("/file/{templateId}/use")
  @Operation(summary = "从模板创建新文件")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public YdszResponse<FileNodeVO> createFromFileTemplate(
      @PathVariable String templateId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam("parentId") String parentId) {

    FileNodeVO newFile = spaceTemplateApplicationService
        .createFileFromTemplate(templateId, parentId, userId);
    log.info("[FileTemplateController] 从模板创建文件: templateId={}, parentId={}, userId={}",
        templateId, parentId, userId);
    return YdszResponse.success(newFile);
  }

  /**
   * 删除文件模板。
   *
   * <p>仅模板创建者或系统管理员可删除。系统内置（visibility=system）模板不允许删除。
   *
   * @param templateId 模板 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @DeleteMapping("/file/{templateId}")
  @Operation(summary = "删除文件模板")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
  public YdszResponse<Void> deleteFileTemplate(
      @PathVariable String templateId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    spaceTemplateApplicationService.deleteFileTemplate(templateId, userId);
    log.info("[FileTemplateController] 删除文件模板: templateId={}, userId={}", templateId, userId);
    return YdszResponse.success();
  }

  /** 文件模板创建请求 DTO */
  @Data
  @Builder
  public static class CreateFileTemplateRequest {
    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String name;

    /** 模板描述 */
    private String description;

    /** 源文件节点 ID */
    @NotBlank(message = "源文件节点ID不能为空")
    private String sourceNodeId;

    /** 可见性（system/org/private），默认 private */
    private String visibility;

    /** 租户 ID（org 可见性时必传） */
    private String tenantId;
  }
}
