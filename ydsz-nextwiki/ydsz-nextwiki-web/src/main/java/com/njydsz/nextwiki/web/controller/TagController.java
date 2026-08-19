package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.server.service.TagApplicationService;

/**
 * 标签管理 REST API Controller。
 *
 * <p>提供网盘标签的创建、查询、绑定、自动推荐能力，是网盘文件归类与检索的关键辅助：
 *
 * <ul>
 *   <li>{@code POST /tags} - 创建标签（自定义名称 + 颜色）
 *   <li>{@code GET /tags} - 查询所有可用标签
 *   <li>{@code POST /tags/bind} - 为文件绑定标签（支持批量）
 *   <li>{@code GET /tags/file/{fileNodeId}} - 查询指定文件的全部标签
 *   <li>{@code GET /tags/recommend/{fileNodeId}} - 基于文件内容推荐标签（AI 能力）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>手动标签：用户自定义名称 + 颜色（如"合同-红色"、"财务-蓝色"）
 *   <li>AI 推荐：基于文件标题/内容（关键词提取）智能推荐标签
 *   <li>批量绑定：单次请求可为文件绑定多个标签
 *   <li>标签筛选：前端"按标签筛选文件"功能依赖此接口
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志
 *   <li>所有接口均加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_TAG_*）
 *   <li>标签命名全局唯一，由 service 层去重校验
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST /api/v1/nextwiki/tags                       - 创建标签
 *   GET  /api/v1/nextwiki/tags                       - 查询所有标签
 *   POST /api/v1/nextwiki/tags/bind                  - 为文件绑定标签
 *   GET  /api/v1/nextwiki/tags/file/{fileNodeId}     - 查询文件的标签
 *   GET  /api/v1/nextwiki/tags/recommend/{fileNodeId}- 智能推荐标签
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.TagApplicationService
 *                                            ↓
 *                                   ydsz-nextwiki-infra.TagMapper
 *                                            ↓
 *                                   ydsz_tag / ydsz_file_tag
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ApiVersion("v1")
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(
    name = "标签管理",
    description = "标签创建、绑定、智能推荐") // FQN-OK: name conflict with TagDO entity
public class TagController {

  /** 标签应用服务（封装标签 CRUD + 绑定 + 推荐） */
  private final TagApplicationService tagApplicationService;

  /**
   * 创建自定义标签。
   *
   * <p>标签名在同一作用域内唯一；重复创建会被 service 层拒绝。
   *
   * @param request 创建标签请求（name / color）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为新创建的 {@link TagDO}
   */
  @Audit(
      module = "标签管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createTag'")
  @Idempotent(key = "ydsz:nextwiki:TagController:createTag:lock", ttlSeconds = 5)
  @PostMapping
  @Operation(summary = "创建标签")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_CREATE)
  public BaseResponse<TagDTO> createTag(
      @RequestBody NextwikiDTOs.CreateTagRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    TagDTO tagDTO = tagApplicationService.createTag(request.getName(), request.getColor(), userId);
    return BaseResponse.success(tagDTO);
  }

  /**
   * 查询所有可用的标签列表。
   *
   * <p>返回全局可见的标签集合，供前端"标签选择器"组件渲染。
   *
   * @return 统一响应结果，data 为 {@link TagDO} 列表
   */
  @GetMapping
  @Operation(summary = "查询所有标签")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_LIST)
  public BaseResponse<List<TagVO>> listTags() {
    return BaseResponse.success(tagApplicationService.getAllTags());
  }

  /**
   * 为文件绑定标签。
   *
   * <p>批量绑定（一次请求多个 tagId），重复绑定会自动去重。
   *
   * @param request 绑定请求（fileNodeId / tagIds）
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "标签管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'bindTag'")
  @Idempotent(key = "ydsz:nextwiki:TagController:bindTag:lock", ttlSeconds = 5)
  @PostMapping("/bind")
  @Operation(summary = "为文件绑定标签")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_BIND)
  public BaseResponse<Void> bindTag(
      @RequestBody NextwikiDTOs.BindTagRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    tagApplicationService.batchBindTags(request.getFileNodeId(), request.getTagIds(), userId);
    return BaseResponse.success();
  }

  /**
   * 查询指定文件已绑定的所有标签。
   *
   * @param fileNodeId 文件节点 ID
   * @return 统一响应结果，data 为 {@link TagDO} 列表
   */
  @GetMapping("/file/{fileNodeId}")
  @Operation(summary = "查询文件的标签")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_LIST)
  public BaseResponse<List<TagVO>> getFileTags(@PathVariable String fileNodeId) {
    return BaseResponse.success(tagApplicationService.getFileTags(fileNodeId));
  }

  /**
   * 基于文件内容智能推荐标签。
   *
   * <p>通常基于文件标题/正文/AI Summary 做关键词提取，匹配现有标签库， 返回 TopN 推荐结果。建议前端在用户上传后自动调用此接口预填标签。
   *
   * @param fileNodeId 文件节点 ID
   * @return 统一响应结果，data 为推荐的 {@link TagDO} 列表
   */
  @GetMapping("/recommend/{fileNodeId}")
  @Operation(summary = "推荐标签")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_LIST)
  public BaseResponse<List<TagVO>> recommendTags(@PathVariable String fileNodeId) {
    return BaseResponse.success(tagApplicationService.recommendTags(fileNodeId));
  }
}
