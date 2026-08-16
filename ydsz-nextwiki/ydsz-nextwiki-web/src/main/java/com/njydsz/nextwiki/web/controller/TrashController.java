package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.server.service.TrashApplicationService;

/**
 * 回收站 REST API Controller。
 *
 * <p>提供回收站的列表查询、恢复、永久删除、清空能力，是网盘"软删除"机制的对外接口：
 *
 * <ul>
 *   <li>{@code GET /trash/list} - 查询当前用户的回收站列表
 *   <li>{@code POST /trash/{id}/restore} - 恢复单个回收站项目
 *   <li>{@code POST /trash/batch-restore} - 批量恢复多个项目
 *   <li>{@code DELETE /trash/{id}} - 永久删除单个项目（不可恢复）
 *   <li>{@code DELETE /trash/empty} - 清空整个回收站（不可恢复）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>软删除：{@code FileController#delete} 不会立即清理物理文件，而是移入回收站
 *   <li>保留期：默认 30 天，超期由定时任务自动永久删除（{@code NextwikiScheduledJobs}）
 *   <li>恢复语义：恢复后节点回到删除前的父目录（parentId 已记录在 TrashItem 中）
 *   <li>级联恢复：恢复文件夹会递归恢复其下所有文件
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（操作人 + 项目 ID 留痕）
 *   <li>永久删除（purge/empty）为不可逆操作，需 NEXTWIKI_TRASH_PURGE 权限码
 *   <li>所有接口均加 {@link AuthApiPermission} 权限码校验
 *   <li>仅本人可恢复/删除自己的回收站项目（service 层按 userId 过滤）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   GET    /api/v1/nextwiki/trash/list                  - 回收站列表
 *   POST   /api/v1/nextwiki/trash/{id}/restore          - 恢复
 *   POST   /api/v1/nextwiki/trash/batch-restore         - 批量恢复
 *   DELETE /api/v1/nextwiki/trash/{id}                  - 永久删除
 *   DELETE /api/v1/nextwiki/trash/empty                 - 清空回收站
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.TrashApplicationService
 *                                            ↓
 *                                   ydsz-nextwiki-server.NextwikiScheduledJobs (定时清理)
 *                                            ↓
 *                                   ydsz-nextwiki-infra.TrashItemMapper
 *                                            ↓
 *                                   ydsz_trash_item
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/trash")
@RequiredArgsConstructor
@Tag(name = "回收站管理", description = "回收站列表、恢复、永久删除、清空（默认 30 天保留期）")
public class TrashController {

  /** 回收站应用服务（封装回收站 CRUD + 恢复 + 永久删除） */
  private final TrashApplicationService trashApplicationService;

  /**
   * 查询当前用户的回收站项目列表。
   *
   * <p>按删除时间倒序返回该用户的所有回收站项目，包含文件/文件夹的原始信息及删除时间/路径。
   *
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link TrashItem} 列表
   */
  @GetMapping("/list")
  @Operation(summary = "查询回收站列表")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_LIST)
  public BaseResponse<List<TrashItem>> list(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(trashApplicationService.listTrash(userId));
  }

  /**
   * 从回收站恢复单个项目。
   *
   * <p>恢复后节点回到删除前的父目录；若原父目录已不存在则恢复到根目录。 文件夹下文件会被级联恢复。
   *
   * @param trashItemId 回收站项目 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "回收站",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'restore'")
  @Idempotent(key = "ydsz:nextwiki:TrashController:restore:lock", ttlSeconds = 5)
  @PostMapping("/{trashItemId}/restore")
  @Operation(summary = "从回收站恢复")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_RESTORE)
  public BaseResponse<Void> restore(
      @PathVariable String trashItemId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    trashApplicationService.restore(trashItemId, userId);
    return BaseResponse.success();
  }

  /**
   * 批量从回收站恢复多个项目。
   *
   * <p>单个项目恢复失败不会中断其他项目的处理；失败的会在日志中记录原因。
   *
   * @param trashItemIds 回收站项目 ID 列表
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "回收站",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchRestore'")
  @Idempotent(key = "ydsz:nextwiki:TrashController:batchRestore:lock", ttlSeconds = 5)
  @PostMapping("/batch-restore")
  @Operation(summary = "批量恢复")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_RESTORE)
  public BaseResponse<Void> batchRestore(
      @RequestBody List<String> trashItemIds,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    trashApplicationService.batchRestore(trashItemIds, userId);
    return BaseResponse.success();
  }

  /**
   * 永久删除回收站中的单个项目（不可恢复）。
   *
   * <p>会级联删除：节点记录 + 历史版本 + 物理文件 + 关联 ACL/Tag/Comment 等。 高危操作，需 NEXTWIKI_TRASH_PURGE 权限。
   *
   * @param trashItemId 回收站项目 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "回收站",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'purge'")
  @Idempotent(key = "ydsz:nextwiki:TrashController:purge:lock", ttlSeconds = 5)
  @DeleteMapping("/{trashItemId}")
  @Operation(summary = "永久删除")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_PURGE)
  public BaseResponse<Void> purge(
      @PathVariable String trashItemId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    trashApplicationService.purge(trashItemId, userId);
    return BaseResponse.success();
  }

  /**
   * 清空整个回收站（不可恢复）。
   *
   * <p>会一次性永久删除该用户在回收站中的所有项目。属于高危批量操作，需 NEXTWIKI_TRASH_EMPTY 权限。
   *
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "回收站",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'emptyTrash'")
  @Idempotent(key = "ydsz:nextwiki:TrashController:emptyTrash:lock", ttlSeconds = 5)
  @DeleteMapping("/empty")
  @Operation(summary = "清空回收站")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_EMPTY)
  public BaseResponse<Void> emptyTrash(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    trashApplicationService.emptyTrash(userId);
    return BaseResponse.success();
  }
}
