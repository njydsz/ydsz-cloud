package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
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
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.FileApplicationService;

/**
 * 文件管理核心 REST API Controller（核心单文件操作）。
 *
 * <p>网盘文件（NextWiki）模块的核心 REST 接口，承担文件最基础的生命周期管理能力： 上传、目录创建、列目录、移动、重命名、删除（移入回收站）、复制。
 *
 * <p>本类已从原 533 行的胖 Controller 拆分为 3 个职责清晰的 Controller：
 *
 * <ul>
 *   <li>本类 {@code FileController} —— 核心单文件操作
 *   <li>{@link FileBatchController} —— 批量操作、版本管理、星标切换
 *   <li>{@link FileChunkController} —— 大文件分片上传（断点续传）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>单文件上传：基于 {@code MultipartFile} 的标准上传，自动创建首版本
 *   <li>目录管理：创建目录、列出目录内容（支持排序/过滤/分页）
 *   <li>节点操作：移动 / 重命名 / 复制 / 删除（软删除，移入回收站）
 *   <li>回收站：删除操作实际为"软删除"，文件移入回收站，可由 {@code TrashController} 恢复
 *   <li>目录树：基于 parentId 自引用构建无限层级目录树
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
 *   POST   /api/v1/nextwiki/files/upload                 - 上传文件
 *   POST   /api/v1/nextwiki/files/folders                - 创建目录
 *   GET    /api/v1/nextwiki/files/list                   - 列出目录内容
 *   PUT    /api/v1/nextwiki/files/{nodeId}/move          - 移动文件/文件夹
 *   PUT    /api/v1/nextwiki/files/{nodeId}/rename        - 重命名
 *   DELETE /api/v1/nextwiki/files/{nodeId}               - 删除（移入回收站）
 *   POST   /api/v1/nextwiki/files/{nodeId}/copy          - 复制
 *   PUT    /api/v1/nextwiki/files/sort                   - 批量排序（拖拽排序）
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                    ydsz-nextwiki-server.FileApplicationService
 *                                       └── NextwikiMetrics (指标采集)
 *                                            ↓
 *                                    ydsz-nextwiki-infra Mapper
 *                                            ↓
 *                                    ydsz_file_node / ydsz_file_version
 * </pre>
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
@Tag(name = "网盘文件管理", description = "文件上传、目录、移动、重命名、删除、复制")
public class FileController {

  /** 文件应用服务（封装上传/移动/重命名/复制/删除等业务编排） */
  private final FileApplicationService fileApplicationService;

  /** Micrometer 指标采集（记录上传/删除次数等关键指标） */
  private final NextwikiMetrics nextwikiMetrics;

  /**
   * 上传文件（单文件模式）。
   *
   * <p>将单个文件上传到指定目录，自动创建首版本记录（version=1）。如目标位置已存在同名文件， 会按版本管理策略递增版本号。同一文件短时间内重复上传会被 {@link
   * Idempotent} 拦截。
   *
   * @param file 源文件（{@code multipart/form-data}）
   * @param parentId 父目录 ID（{@code ydsz_file_node.id}，可空表示根目录）
   * @param rename 重命名（可选；为空则保留原文件名）
   * @param versionRemark 版本备注（可选，描述本次上传的变更点）
   * @param userId 当前用户 ID（从 {@code X-User-Id} 头获取）
   * @return 统一响应结果，data 为上传后的文件节点信息（含 nodeId / fileName / version 等）
   */
  @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'upload'")
  @Idempotent(key = "ydsz:nextwiki:FileController:upload:lock", ttlSeconds = 5)
  @PostMapping("/upload")
  @Operation(summary = "上传文件", description = "支持单文件上传，自动创建版本记录")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public BaseResponse<FileNodeVO> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "parentId", required = false) String parentId,
      @RequestParam(value = "rename", required = false) String rename,
      @RequestParam(value = "versionRemark", required = false) String versionRemark,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileNodeVO result =
        fileApplicationService.upload(file, parentId, rename, versionRemark, userId);
    nextwikiMetrics.recordUpload();
    return BaseResponse.success(result);
  }

  /**
   * 创建目录。
   *
   * <p>在指定父目录下创建新目录节点。系统会校验目录名在同一父目录下唯一。
   *
   * @param request 创建目录请求（parentId / name）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为新创建的目录节点信息
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'createFolder'")
  @Idempotent(key = "ydsz:nextwiki:FileController:createFolder:lock", ttlSeconds = 5)
  @PostMapping("/folders")
  @Operation(summary = "创建目录")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FOLDER_CREATE)
  public BaseResponse<FileNodeVO> createFolder(
      @Valid @RequestBody NextwikiDTOs.CreateFolderRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileNodeVO result =
        fileApplicationService.createFolder(request.getParentId(), request.getName(), userId);
    return BaseResponse.success(result);
  }

  /**
   * 列出指定目录下的内容。
   *
   * <p>支持按字段排序（{@code sortBy} / {@code sortDir}）、类型过滤（{@code type}，如 file/folder/all） 和数据库分页（{@code
   * page} / {@code pageSize}），返回目录内容列表。
   *
   * @param parentId 父目录 ID（可空表示根目录）
   * @param sortBy 排序字段（name/size/createdAt 等）
   * @param sortDir 排序方向（asc/desc）
   * @param type 文件类型过滤（file/folder/all）
   * @param page 页码（从 1 开始）
   * @param pageSize 每页大小
   * @param userId 当前用户 ID（用于权限过滤）
   * @return 统一响应结果，data 为分页结果 {@link PageResponse}，含 {@link FileNodeVO} 列表
   */
  @GetMapping("/list")
  @Operation(summary = "列出目录内容", description = "支持排序、过滤、分页（数据库分页）")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_LIST)
  public PageResponse<List<FileNodeVO>> listFiles(
      @RequestParam(value = "parentId", required = false) String parentId,
      @RequestParam(value = "sortBy", required = false) String sortBy,
      @RequestParam(value = "sortDir", required = false) String sortDir,
      @RequestParam(value = "type", required = false) String type,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    return fileApplicationService.listFiles(
        parentId, userId, sortBy, sortDir, type, page, pageSize);
  }

  /**
   * 移动文件或文件夹到指定目录。
   *
   * <p>支持文件和文件夹两种类型；目标位置不能是当前节点的子孙（避免循环引用）。
   *
   * @param nodeId 源节点 ID
   * @param request 移动请求（targetParentId）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为移动后的节点信息
   */
  @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.UPDATE, content = "'move'")
  @Idempotent(key = "ydsz:nextwiki:FileController:move:lock", ttlSeconds = 5)
  @PutMapping("/{nodeId}/move")
  @Operation(summary = "移动文件/文件夹")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
  public BaseResponse<FileNodeVO> move(
      @PathVariable String nodeId,
      @Valid @RequestBody NextwikiDTOs.MoveRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileNodeVO result = fileApplicationService.move(nodeId, request.getTargetParentId(), userId);
    return BaseResponse.success(result);
  }

  /**
   * 重命名文件或文件夹。
   *
   * <p>同目录下重名会被拒绝；该操作不影响文件内容、不创建新版本。
   *
   * @param nodeId 节点 ID
   * @param request 重命名请求（newName）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为重命名后的节点信息
   */
  @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.UPDATE, content = "'rename'")
  @Idempotent(key = "ydsz:nextwiki:FileController:rename:lock", ttlSeconds = 5)
  @PutMapping("/{nodeId}/rename")
  @Operation(summary = "重命名文件/文件夹")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_RENAME)
  public BaseResponse<FileNodeVO> rename(
      @PathVariable String nodeId,
      @Valid @RequestBody NextwikiDTOs.RenameRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileNodeVO result = fileApplicationService.rename(nodeId, request.getNewName(), userId);
    return BaseResponse.success(result);
  }

  /**
   * 删除文件或文件夹（软删除，移入回收站）。
   *
   * <p>删除操作不会立即清理物理文件，而是将节点移入回收站（{@code ydsz_trash_item}）， 保留 30 天可由 {@code TrashController}
   * 恢复或彻底删除。文件夹下文件会被级联移入回收站。
   *
   * @param nodeId 节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.DELETE, content = "'delete'")
  @Idempotent(key = "ydsz:nextwiki:FileController:delete:lock", ttlSeconds = 5)
  @DeleteMapping("/{nodeId}")
  @Operation(summary = "删除文件/文件夹（移入回收站）")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
  public BaseResponse<Void> delete(
      @PathVariable String nodeId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    fileApplicationService.delete(nodeId, userId);
    nextwikiMetrics.recordDelete();
    return BaseResponse.success();
  }

  /**
   * 复制文件或文件夹到指定目录。
   *
   * <p>文件复制：生成全新的文件节点和首版本，与原文件共享同一存储对象（引用计数 +1）。
   *
   * <p>文件夹复制：递归复制目录下全部子节点（含子目录与文件），生成完整的目录树副本； 所有文件共享同一存储对象，不重复占用物理空间。
   *
   * <p>目标位置如有同名节点，文件会自动追加后缀（如 {@code file(1).txt}），文件夹保持原名。
   *
   * @param nodeId 源节点 ID（文件或文件夹）
   * @param targetParentId 目标父目录 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为新复制的节点信息（文件或文件夹根节点）
   */
  @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'copy'")
  @Idempotent(key = "ydsz:nextwiki:FileController:copy:lock", ttlSeconds = 5)
  @PostMapping("/{nodeId}/copy")
  @Operation(summary = "复制文件或文件夹", description = "自动识别节点类型，文件直接复制，文件夹递归复制全部子节点")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_COPY)
  public BaseResponse<FileNodeVO> copy(
      @PathVariable String nodeId,
      @RequestParam("targetParentId") String targetParentId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    FileNodeVO sourceNode = fileApplicationService.getFileInfo(nodeId);
    FileNodeVO result;
    if (sourceNode != null && "folder".equals(sourceNode.getNodeType())) {
      result = fileApplicationService.copyFolder(nodeId, targetParentId, userId);
    } else {
      result = fileApplicationService.copy(nodeId, targetParentId, userId);
    }
    return BaseResponse.success(result);
  }

  /**
   * 批量更新节点排序值（拖拽排序）。
   *
   * <p>前端拖拽完成后提交目标父目录下的完整排序列表，服务端在单个事务内批量更新 sort 字段。
   *
   * <p><b>请求体示例：</b>
   *
   * <pre>
   * {
   *   "parentId": "folder_123",
   *   "items": [
   *     {"nodeId": "node_1", "sort": 0},
   *     {"nodeId": "node_2", "sort": 1},
   *     {"nodeId": "node_3", "sort": 2}
   *   ]
   * }
   * </pre>
   *
   * @param request 批量排序请求（含父目录 ID + 排序条目列表）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为实际更新的节点数
   */
  @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.UPDATE, content = "'batchSort'")
  @Idempotent(key = "ydsz:nextwiki:FileController:batchSort:lock", ttlSeconds = 5)
  @PutMapping("/sort")
  @Operation(summary = "批量排序", description = "拖拽排序后批量更新节点排序值")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_RENAME)
  public BaseResponse<Integer> batchSort(
      @Valid @RequestBody NextwikiDTOs.BatchSortRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    int updated = fileApplicationService.batchSort(request.getParentId(), request.getItems(), userId);
    log.info("[FileController] 批量排序: parentId={}, count={}, userId={}", request.getParentId(), updated, userId);
    return BaseResponse.success(updated);
  }
}
