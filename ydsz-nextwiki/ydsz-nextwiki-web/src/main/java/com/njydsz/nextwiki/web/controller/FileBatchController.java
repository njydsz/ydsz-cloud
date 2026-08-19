package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.server.service.BatchTaskService;
import com.njydsz.nextwiki.server.service.BatchTaskService.BatchTaskStatus;
import com.njydsz.nextwiki.server.service.FileApplicationService;
import com.njydsz.nextwiki.server.service.VersionDiffApplicationService;
import com.njydsz.nextwiki.server.service.VersionDiffService;

/**
 * 文件批量操作与版本管理 REST API Controller。
 *
 * <p>从原 {@code FileController} 拆分而来，专门承担批量操作、版本管理与个性化星标等聚合类接口。
 *
 * <h3>业务背景</h3>
 *
 * <p>网盘文件（NextWiki）模块在日常使用中存在大量"一次操作多个节点"或"对历史版本进行管理"的场景， 例如批量清理、批量归档、版本回滚等。将这些能力从核心单文件 CRUD
 * 中剥离，便于按需扩展批量策略 与版本审计逻辑，同时降低单 Controller 的代码体积与心智负担。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>批量操作：批量删除 / 批量移动，统一返回 {@link FileApplicationService.BatchResult}（含成功/失败明细）
 *   <li>版本管理：版本历史查询（按版本号倒序）、回滚到指定版本（保留历史，新增回滚记录）
 *   <li>个性化：切换文件星标状态，便于"我的星标"视图聚合展示
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（FILE 类型）
 *   <li>所有写操作均加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_FILE_*）
 *   <li>用户身份通过 {@code X-User-Id} 请求头传递（由网关层注入）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST   /api/v1/nextwiki/files/batch/delete           - 批量删除（同步）
 *   POST   /api/v1/nextwiki/files/batch/move             - 批量移动（同步）
 *   POST   /api/v1/nextwiki/files/batch/async-delete     - 异步批量删除
 *   POST   /api/v1/nextwiki/files/batch/async-move       - 异步批量移动
 *   GET    /api/v1/nextwiki/files/batch/task/{taskId}    - 查询批量任务状态
 *   GET    /api/v1/nextwiki/files/{nodeId}/versions      - 版本历史
 *   POST   /api/v1/nextwiki/files/{nodeId}/versions/{ver}/rollback - 版本回滚
 *   PUT    /api/v1/nextwiki/files/{nodeId}/star          - 切换星标
 * </pre>
 *
 * <h3>相关拆分</h3>
 *
 * <ul>
 *   <li>{@link FileController} — 核心单文件操作（上传/目录/移动/重命名/删除/复制）
 *   <li>{@link FileChunkController} — 大文件分片上传（断点续传）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ApiVersion("v1")
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Validated
@Tag(name = "网盘文件批量与版本管理", description = "批量删除/移动、版本历史/回滚、星标切换")
public class FileBatchController {

  /** 文件应用服务（封装批量操作/版本管理/星标等业务编排） */
  private final FileApplicationService fileApplicationService;

  /** 批量任务异步执行服务 */
  private final BatchTaskService batchTaskService;

  /** 版本对比应用服务 */
  private final VersionDiffApplicationService versionDiffApplicationService;

  /**
   * 批量删除文件/文件夹（移入回收站）。
   *
   * <p>返回每条的处理结果（成功/失败原因），由前端根据 {@link FileApplicationService.BatchResult} 展示。
   *
   * @param nodeIds 节点 ID 列表
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为批量处理结果（successCount / failCount / failures）
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'batchDelete'")
  @Idempotent(key = "ydsz:nextwiki:FileController:batchDelete:lock", ttlSeconds = 5)
  @PostMapping("/batch/delete")
  @Operation(summary = "批量删除")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
  public YdszResponse<FileApplicationService.BatchResult> batchDelete(
      @RequestBody List<String> nodeIds,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileApplicationService.BatchResult result = fileApplicationService.batchDelete(nodeIds, userId);
    return YdszResponse.success(result);
  }

  /**
   * 批量移动文件/文件夹到指定目录。
   *
   * <p>所有节点必须属于同一用户；目标位置不能是任一节点的子孙。
   *
   * @param request 批量移动请求（nodeIds / targetParentId）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为批量处理结果
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'batchMove'")
  @Idempotent(key = "ydsz:nextwiki:FileController:batchMove:lock", ttlSeconds = 5)
  @PostMapping("/batch/move")
  @Operation(summary = "批量移动")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
  public YdszResponse<FileApplicationService.BatchResult> batchMove(
      @Valid @RequestBody NextwikiDTOs.BatchMoveRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileApplicationService.BatchResult result =
        fileApplicationService.batchMove(request.getNodeIds(), request.getTargetParentId(), userId);
    return YdszResponse.success(result);
  }

  /**
   * 获取文件的版本历史。
   *
   * <p>按版本号倒序返回全部历史版本，每条记录包含版本号、大小、上传人、上传时间、备注等。 当前最新版本固定在列表首位。
   *
   * @param nodeId 文件节点 ID
   * @return 统一响应结果，data 为 {@link FileVersionVO} 列表
   */
  @GetMapping("/{nodeId}/versions")
  @Operation(summary = "获取版本历史")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_VIEW)
  public YdszResponse<List<FileVersionVO>> getVersionHistory(@PathVariable String nodeId) {
    return YdszResponse.success(fileApplicationService.getVersionHistory(nodeId));
  }

  /**
   * 回滚文件到指定版本。
   *
   * <p>将当前版本替换为指定历史版本的内容，同时在版本历史中新增一条"回滚"记录。 不会删除任何历史版本，可在版本历史中再次切回。
   *
   * @param nodeId 文件节点 ID
   * @param version 目标版本号
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为回滚后的文件节点信息（含新版本号）
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'rollbackVersion'")
  @Idempotent(key = "ydsz:nextwiki:FileController:rollbackVersion:lock", ttlSeconds = 5)
  @PostMapping("/{nodeId}/versions/{version}/rollback")
  @Operation(summary = "回滚到指定版本")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_ROLLBACK)
  public YdszResponse<FileNodeVO> rollbackVersion(
      @PathVariable String nodeId,
      @PathVariable Integer version,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileNodeVO result = fileApplicationService.rollbackVersion(nodeId, version, userId);
    return YdszResponse.success(result);
  }

  /**
   * 对比两个版本的差异（文本文件）。
   *
   * <p>仅支持文本类文件（txt、md、json、xml、csv 等），文件大小不超过 1MB。 返回行粒度的差异信息（新增、删除、未变更）。
   *
   * @param nodeId 文件节点 ID
   * @param oldVersion 旧版本号
   * @param newVersion 新版本号
   * @return 统一响应结果，data 为 diff 结果（含差异条目 + 统计信息）
   */
  @GetMapping("/{nodeId}/versions/diff")
  @Operation(summary = "对比版本差异")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_VIEW)
  public YdszResponse<VersionDiffService.DiffResult> diffVersions(
      @PathVariable String nodeId,
      @RequestParam int oldVersion,
      @RequestParam int newVersion) {
    return YdszResponse.success(
        versionDiffApplicationService.diffVersions(nodeId, oldVersion, newVersion));
  }

  /**
   * 异步批量删除（提交任务后立即返回任务 ID）。
   *
   * <p>适用于大批量操作（{@code nodeIds.size() > 10}），通过 task ID 轮询执行结果。
   *
   * @param nodeIds 节点 ID 列表
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为任务 ID
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'asyncBatchDelete'")
  @Idempotent(key = "ydsz:nextwiki:FileController:asyncBatchDelete:lock", ttlSeconds = 5)
  @PostMapping("/batch/async-delete")
  @Operation(summary = "异步批量删除")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
  public YdszResponse<String> asyncBatchDelete(
      @RequestBody List<String> nodeIds,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String taskId = batchTaskService.submitBatchDelete(nodeIds, userId);
    return YdszResponse.success(taskId);
  }

  /**
   * 异步批量移动（提交任务后立即返回任务 ID）。
   *
   * @param request 批量移动请求（nodeIds / targetParentId）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为任务 ID
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'asyncBatchMove'")
  @Idempotent(key = "ydsz:nextwiki:FileController:asyncBatchMove:lock", ttlSeconds = 5)
  @PostMapping("/batch/async-move")
  @Operation(summary = "异步批量移动")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
  public YdszResponse<String> asyncBatchMove(
      @Valid @RequestBody NextwikiDTOs.BatchMoveRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String taskId =
        batchTaskService.submitBatchMove(request.getNodeIds(), request.getTargetParentId(), userId);
    return YdszResponse.success(taskId);
  }

  /**
   * 查询异步批量任务状态。
   *
   * @param taskId 任务 ID（由 asyncDelete 或 asyncMove 返回）
   * @return 统一响应结果，data 为任务状态（含进度、结果）
   */
  @GetMapping("/batch/task/{taskId}")
  @Operation(summary = "查询批量任务状态")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VIEW)
  public YdszResponse<BatchTaskStatus> getBatchTaskStatus(@PathVariable String taskId) {
    BatchTaskStatus status = batchTaskService.getTaskStatus(taskId);
    return YdszResponse.success(status);
  }

  /**
   * 切换文件星标状态。
   *
   * <p>星标文件会在前端"我的星标"视图中聚合显示，便于快速访问。
   *
   * @param nodeId 节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.UPDATE,
      content = "'toggleStar'")
  @Idempotent(key = "ydsz:nextwiki:FileController:toggleStar:lock", ttlSeconds = 5)
  @PutMapping("/{nodeId}/star")
  @Operation(summary = "切换星标状态")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_STAR)
  public YdszResponse<Void> toggleStar(
      @PathVariable String nodeId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    fileApplicationService.toggleStar(nodeId, userId);
    return YdszResponse.success();
  }
}
