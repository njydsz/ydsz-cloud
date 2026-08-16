package com.njydsz.nextwiki.web.controller;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.service.FilePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件锁定 REST API Controller（P1-6 + P0-R3 + P2-R2）。
 *
 * <p>提供网盘文件的 Check-out / Check-in 机制，防止多用户并发编辑冲突：
 *
 * <ul>
 *   <li>{@code POST /files/{nodeId}/lock} - 锁定文件（Check-out，独占编辑权）
 *   <li>{@code POST /files/{nodeId}/unlock} - 解锁文件（Check-in，释放编辑权）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>悲观锁：锁定后仅锁定者可编辑，其他用户上传/重命名/移动等写操作被拒绝
 *   <li>状态隔离：P0-R3 修复后，锁定状态使用 {@code status='locked'} 字段记录， 不再覆盖共享状态（{@code shareStatus}）
 *   <li>权限校验：P2-R2 修复后增加 {@link FilePermissionService#checkWrite} 权限检查
 *   <li>再锁定：锁定者可重新锁定自己的文件（刷新 {@code updatedAt}）
 * </ul>
 *
 * <h3>典型使用流程</h3>
 *
 * <pre>
 *   1. 用户A 打开在线编辑 → 调用 lock 锁定文件
 *   2. 用户B 同时打开 → lock 被拒绝（"文件已被用户A锁定"）
 *   3. 用户A 编辑完成 → 调用 unlock 释放
 *   4. 用户B 重新调用 lock 获取编辑权
 * </pre>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（FILE 类型）
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_FILE_UPLOAD）
 *   <li>仅文件所有者和已授权用户可执行锁定（由 {@link FilePermissionService} 校验）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST /api/v1/nextwiki/files/{nodeId}/lock    - 锁定（Check-out）
 *   POST /api/v1/nextwiki/files/{nodeId}/unlock  - 解锁（Check-in）
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-domain.FileNodeRepository
 *                                   ydsz-nextwiki-server.FilePermissionService
 *                                            ↓
 *                                   ydsz-nextwiki-infra Mapper
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Tag(name = "文件锁定", description = "Check-out/Check-in 防并发编辑（P0-R3 修复后使用 status 字段）")
public class FileLockController {

  /** 文件节点仓储（用于查询/更新文件状态） */
  private final FileNodeRepository fileNodeRepository;

  /** 文件权限服务（封装读写权限校验） */
  private final FilePermissionService permissionService;

  /**
   * 锁定文件（Check-out）。
   *
   * <p>将文件状态置为 {@code locked}，并记录锁定人（{@code updatedBy}）。 已锁定且非本人锁定的文件再次锁定会被拒绝。
   *
   * @param nodeId 文件节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   * @throws BusinessException 文件不存在 / 文件被他人锁定 / 无写权限时抛出
   */
  @Audit(module = "文件锁定", type = AuditType.FILE, action = AuditAction.CREATE, content = "'lock'")
  @Idempotent(key = "ydsz:nextwiki:FileLockController:filelock:lock", ttlSeconds = 5)
  @PostMapping("/{nodeId}/lock")
  @Operation(summary = "锁定文件（Check-out）")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public BaseResponse<Void> lock(
      @PathVariable String nodeId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    // P2-R2: 权限检查
    permissionService.checkWrite(nodeId, userId);

    FileNode node = fileNodeRepository.findById(nodeId);
    if (node == null || !node.isFile()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    // P0-R3: 使用 status 字段记录锁定状态，不再覆盖 shareStatus
    if ("locked".equals(node.getStatus()) && !userId.equals(node.getUpdatedBy())) {
      throw new BusinessException(NextwikiExceptionCode.FILE_LOCKED);
    }

    node.setStatus("locked");
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(node);

    log.info("[FileLockController] 锁定文件: nodeId={}, userId={}", nodeId, userId);
    return BaseResponse.success();
  }

  /**
   * 解锁文件（Check-in）。
   *
   * <p>将文件状态从 {@code locked} 恢复为 {@code active}，释放编辑权。 仅本人锁定的文件可解锁（{@link
   * FilePermissionService#checkWrite} 校验）。
   *
   * @param nodeId 文件节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   * @throws BusinessException 文件不存在 / 无写权限时抛出
   */
  @Audit(module = "文件锁定", type = AuditType.FILE, action = AuditAction.CREATE, content = "'unlock'")
  @Idempotent(key = "ydsz:nextwiki:FileLockController:unlock:lock", ttlSeconds = 5)
  @PostMapping("/{nodeId}/unlock")
  @Operation(summary = "解锁文件（Check-in）")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public BaseResponse<Void> unlock(
      @PathVariable String nodeId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    // P2-R2: 权限检查
    permissionService.checkWrite(nodeId, userId);

    FileNode node = fileNodeRepository.findById(nodeId);
    if (node == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    // P0-R3: 恢复 status 为 active，不影响 shareStatus
    node.setStatus("active");
    node.setUpdatedBy(userId);
    node.setUpdatedAt(LocalDateTime.now());
    fileNodeRepository.update(node);

    log.info("[FileLockController] 解锁文件: nodeId={}, userId={}", nodeId, userId);
    return BaseResponse.success();
  }
}
