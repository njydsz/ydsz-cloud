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
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.response.YdszResponse;
import com.njydsz.common.web.constants.AuthHeaderConstants;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.server.service.SpaceApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 知识库空间 Controller
 *
 * <p><b>S3-P2-01：空间管理聚合根</b>
 *
 * <p>提供知识库空间的增删查改及成员管理 API。
 *
 * <pre>
 *   GET    /api/v1/nextwiki/spaces              - 查询空间列表
 *   POST   /api/v1/nextwiki/spaces              - 创建空间
 *   GET    /api/v1/nextwiki/spaces/{spaceId}    - 获取空间详情
 *   PUT    /api/v1/nextwiki/spaces/{spaceId}    - 更新空间
 *   DELETE /api/v1/nextwiki/spaces/{spaceId}    - 删除空间
 *   POST   /api/v1/nextwiki/spaces/{spaceId}/members - 添加成员
 *   DELETE /api/v1/nextwiki/spaces/{spaceId}/members/{userId} - 移除成员
 *   GET    /api/v1/nextwiki/spaces/{spaceId}/members - 成员列表
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/spaces")
@RequiredArgsConstructor
@Tag(name = "知识库空间管理", description = "S3-P2-01：空间管理聚合根")
public class SpaceController {

  private final SpaceApplicationService spaceApplicationService;

  /**
   * 查询当前租户的空间列表。
   *
   * @param userId 当前用户ID
   * @return 空间视图列表
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
   * @param request 创建请求
   * @param userId 当前用户ID
   * @return 新创建的空间视图
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
   * 获取空间详情。
   *
   * @param spaceId 空间ID
   * @param userId 当前用户ID
   * @return 空间视图
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
   * @param spaceId 空间ID
   * @param request 更新请求
   * @param userId 当前用户ID
   * @return 更新后的空间视图
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
   * 归档空间。
   *
   * @param spaceId 空间ID
   * @param userId 当前用户ID
   * @return 操作结果
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
   * 删除空间（逻辑删除）。
   *
   * @param spaceId 空间ID
   * @param userId 当前用户ID
   * @return 操作结果
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
   * 添加空间成员。
   *
   * @param spaceId 空间ID
   * @param request 添加成员请求
   * @param userId 当前用户ID
   * @return 操作结果
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
   * @param spaceId 空间ID
   * @param targetUserId 目标用户ID
   * @param userId 当前用户ID
   * @return 操作结果
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
   * @param spaceId 空间ID
   * @param userId 当前用户ID
   * @return 成员列表
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
