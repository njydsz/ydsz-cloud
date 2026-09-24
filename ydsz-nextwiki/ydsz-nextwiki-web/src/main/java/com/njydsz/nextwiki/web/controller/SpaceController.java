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
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.server.service.SpaceApplicationService;

/**
 * 知识库空间 Controller
 *
 * <p><b>S3-P2-01：空间管理聚合根</b>
 *
 * <p>提供知识库空间的增删查改及成员管理 API。
 *
 * <pre>
 *   GET    /api/nextwiki/spaces              - 查询空间列表
 *   POST   /api/nextwiki/spaces              - 创建空间
 *   GET    /api/nextwiki/spaces/{spaceId}    - 获取空间详情
 *   PUT    /api/nextwiki/spaces/{spaceId}    - 更新空间
 *   DELETE /api/nextwiki/spaces/{spaceId}    - 删除空间
 *   POST   /api/nextwiki/spaces/{spaceId}/members - 添加成员
 *   DELETE /api/nextwiki/spaces/{spaceId}/members/{userId} - 移除成员
 *   GET    /api/nextwiki/spaces/{spaceId}/members - 成员列表
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ApiVersion("26.09.01")
@Slf4j
@RestController
@RequestMapping("/nextwiki/spaces")
@RequiredArgsConstructor
@Tag(name = "知识库空间管理", description = "S3-P2-01：空间管理聚合根")
public class SpaceController {

  private final SpaceApplicationService spaceApplicationService;

  /**
   * 查询当前用户可见的空间列表。
   *
   * <p>返回当前租户下有权限访问的知识库空间列表，结果按创建时间倒序排列。
   * 可见性受空间权限模型约束：私有空间仅对成员可见，组织内空间对同租户用户可见。
   *
   * @param userId 当前用户 ID（由网关通过 X-User-Id 头注入）
   * @return 统一响应结果，data 为 {@link SpaceVO} 列表
   */
  @GetMapping
  @Operation(summary = "查询空间列表", description = "查询当前用户可见的空间列表")
  public YdszResponse<List<SpaceVO>> listSpaces(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    List<SpaceVO> spaces = spaceApplicationService.listSpaces(userId);
    return YdszResponse.success(spaces);
  }

  /**
   * 创建知识库空间。
   *
   * <p>创建新的知识库空间，创建者自动成为空间所有者（owner 角色）。
   * 空间名称在租户内唯一，重复创建会被拒绝。
   *
   * @param request 创建请求（name: 空间名称，description: 空间描述，visibility: 可见性 private/organization/public）
   * @param userId 当前用户 ID（由网关通过 X-User-Id 头注入）
   * @return 统一响应结果，data 为新创建的 {@link SpaceVO}
   */
  @PostMapping
  @Operation(summary = "创建空间", description = "创建新的知识库空间，创建者自动成为所有者")
  public YdszResponse<SpaceVO> createSpace(
      @Valid @RequestBody CreateSpaceRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceVO space = spaceApplicationService.createSpace(
        request.getName(), request.getDescription(), request.getVisibility(), userId);
    return YdszResponse.success(space);
  }

  /**
   * 获取知识库空间详情。
   *
   * <p>返回空间的基本信息、权限配置、成员数量等。仅空间成员或租户管理员可查看。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param userId 当前用户 ID（用于权限校验）
   * @return 统一响应结果，data 为 {@link SpaceVO}；无权限时返回 403
   */
  @GetMapping("/{spaceId}")
  @Operation(summary = "获取空间详情")
  public YdszResponse<SpaceVO> getSpace(
      @PathVariable String spaceId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceVO space = spaceApplicationService.getSpace(spaceId, userId);
    return YdszResponse.success(space);
  }

  /**
   * 更新空间信息。
   *
   * <p>更新空间的名称、描述和可见性配置。仅空间所有者和管理员可执行。
   * 缩小可见性范围不会影响已加入的成员权限。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param request 更新请求（name: 空间名称，description: 空间描述，visibility: 可见性 private/organization/public）
   * @param userId 当前用户 ID（仅空间所有者/管理员可执行）
   * @return 统一响应结果，data 为更新后的 {@link SpaceVO}
   */
  @PutMapping("/{spaceId}")
  @Operation(summary = "更新空间", description = "更新空间名称、描述、可见性（仅所有者/管理员）")
  public YdszResponse<SpaceVO> updateSpace(
      @PathVariable String spaceId,
      @Valid @RequestBody UpdateSpaceRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SpaceVO space = spaceApplicationService.updateSpace(
        spaceId, request.getName(), request.getDescription(), request.getVisibility(), userId);
    return YdszResponse.success(space);
  }

  /**
   * 归档知识库空间。
   *
   * <p>将空间状态更改为 archived，归档后空间内容只读不可修改，成员无法新增文件。
   * 归档操作可由空间所有者或管理员执行，需要时可恢复。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param userId 当前用户 ID（仅空间所有者/管理员可执行）
   * @return 统一响应结果，data 为 true 表示归档成功
   */
  @PostMapping("/{spaceId}/archive")
  @Operation(summary = "归档空间", description = "将空间状态改为 archived（仅所有者/管理员）")
  public YdszResponse<Boolean> archiveSpace(
      @PathVariable String spaceId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    spaceApplicationService.archiveSpace(spaceId, userId);
    return YdszResponse.success(true);
  }

  /**
   * 删除知识库空间（逻辑删除）。
   *
   * <p>将空间标记为已删除（deleted=1），数据保留但不可访问。仅空间所有者和管理员可执行。
   * 空间内的文件记录不会被物理删除，仅解除与空间的关联。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param userId 当前用户 ID（仅空间所有者/管理员可执行）
   * @return 统一响应结果，data 为 true 表示删除成功
   */
  @DeleteMapping("/{spaceId}")
  @Operation(summary = "删除空间", description = "逻辑删除空间（仅所有者/管理员）")
  public YdszResponse<Boolean> deleteSpace(
      @PathVariable String spaceId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    spaceApplicationService.deleteSpace(spaceId, userId);
    return YdszResponse.success(true);
  }

  /**
   * 添加空间成员并赋予角色。
   *
   * <p>将指定用户添加到空间，并赋予对应角色（owner/admin/editor/viewer）。
   * 仅空间所有者和管理员可执行。角色权限层级：owner > admin > editor > viewer。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param request 添加成员请求（userId: 目标用户 ID，role: 角色 owner/admin/editor/viewer）
   * @param userId 当前用户 ID（仅空间所有者/管理员可执行）
   * @return 统一响应结果，data 为 true 表示添加成功
   */
  @PostMapping("/{spaceId}/members")
  @Operation(summary = "添加成员", description = "添加用户到空间并赋予角色（仅所有者/管理员）")
  public YdszResponse<Boolean> addMember(
      @PathVariable String spaceId,
      @Valid @RequestBody AddMemberRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    spaceApplicationService.addMember(spaceId, request.getUserId(), request.getRole(), userId);
    return YdszResponse.success(true);
  }

  /**
   * 移除空间成员。
   *
   * <p>从空间中移除指定用户的所有权限。不能移除空间所有者（owner）。
   * 仅空间所有者和管理员可执行。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param targetUserId 待移除的目标用户 ID
   * @param userId 当前用户 ID（仅空间所有者/管理员可执行）
   * @return 统一响应结果，data 为 true 表示移除成功
   */
  @DeleteMapping("/{spaceId}/members/{targetUserId}")
  @Operation(summary = "移除成员", description = "从空间中移除用户（仅所有者/管理员，不能移除所有者）")
  public YdszResponse<Boolean> removeMember(
      @PathVariable String spaceId,
      @PathVariable String targetUserId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    spaceApplicationService.removeMember(spaceId, targetUserId, userId);
    return YdszResponse.success(true);
  }

  /**
   * 查询空间成员列表。
   *
   * <p>返回空间所有成员信息，包含用户 ID、角色、加入时间等。仅空间成员可查看。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @param userId 当前用户 ID（用于权限校验，仅空间成员可查看）
   * @return 统一响应结果，data 为 {@link SpaceMemberDTO} 列表
   */
  @GetMapping("/{spaceId}/members")
  @Operation(summary = "查询成员列表", description = "查询空间的所有成员（需有读取权限）")
  public YdszResponse<List<SpaceMemberDTO>> listMembers(
      @PathVariable String spaceId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    List<SpaceMemberDTO> members = spaceApplicationService.listMembers(spaceId, userId);
    return YdszResponse.success(members);
  }

  // ==================== 内部请求 DTO ====================

  /** 创建空间请求 */
  @lombok.Data
  @Schema(description = "创建空间请求")
  public static class CreateSpaceRequest {
    @Schema(description = "空间名称", required = true)
    @jakarta.validation.constraints.NotBlank(message = "空间名称不能为空")
    @jakarta.validation.constraints.Size(max = 128, message = "空间名称不能超过128个字符")
    private String name;

    @Schema(description = "空间描述")
    private String description;

    @Schema(description = "可见性：private / organization / public")
    private String visibility;
  }

  /** 更新空间请求 */
  @lombok.Data
  @Schema(description = "更新空间请求")
  public static class UpdateSpaceRequest {
    @Schema(description = "空间名称")
    private String name;

    @Schema(description = "空间描述")
    private String description;

    @Schema(description = "可见性：private / organization / public")
    private String visibility;
  }

  /** 添加成员请求 */
  @lombok.Data
  @Schema(description = "添加成员请求")
  public static class AddMemberRequest {
    @Schema(description = "目标用户ID", required = true)
    @jakarta.validation.constraints.NotBlank(message = "用户ID不能为空")
    private String userId;

    @Schema(description = "角色：owner / admin / editor / viewer", required = true)
    @jakarta.validation.constraints.NotBlank(message = "角色不能为空")
    private String role;
  }
}
