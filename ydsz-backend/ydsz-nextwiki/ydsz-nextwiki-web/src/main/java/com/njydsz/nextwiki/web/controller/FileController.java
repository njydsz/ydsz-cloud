package com.njydsz.nextwiki.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

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

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.health.NextwikiHealthIndicator;
import com.njydsz.nextwiki.server.service.FileApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

import com.njydsz.nextwiki.server.service.ChunkUploadApplicationService;
import java.util.Set;
/**
 * 文件管理 REST API
 *
 * <p>提供文件上传、下载、移动、重命名、删除等操作，
 * 支持单文件上传、分片上传、版本管理、目录创建。
 *
 * <p><b>接口路径：</b>{@code /api/v1/nextwiki/files}
 *
 * <p><b>安全特性：</b>写接口启用 {@link Idempotent} 防重复、{@link Audit} 审计日志、
 * {@link AuthApiPermission} 权限码校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Validated
@Tag(name = "网盘文件管理", description = "文件上传、下载、移动、重命名、删除等操作")
public class FileController {

    private final FileApplicationService fileApplicationService;
    private final NextwikiHealthIndicator healthIndicator;
    private final ChunkUploadApplicationService chunkUploadService;

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
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.upload(file, parentId, rename, versionRemark, userId);
        healthIndicator.recordUpload();
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'createFolder'")
    @Idempotent(key = "ydsz:nextwiki:FileController:createFolder:lock", ttlSeconds = 5)
    @PostMapping("/folders")
    @Operation(summary = "创建目录")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FOLDER_CREATE)
    public BaseResponse<FileNodeVO> createFolder(
            @Valid @RequestBody NextwikiDTOs.CreateFolderRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.createFolder(
                request.getParentId(), request.getName(), userId);
        return BaseResponse.success(result);
    }

    @GetMapping("/list")
    @Operation(summary = "列出目录内容", description = "支持排序、过滤、分页（数据库分页）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_LIST)
    public BaseResponse<PageResult<FileNodeVO>> listFiles(
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false) String sortDir,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
            @RequestHeader("X-User-Id") String userId) {

        PageResult<FileNodeVO> result = fileApplicationService.listFiles(
                parentId, userId, sortBy, sortDir, type, page, pageSize);
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.UPDATE, content = "'move'")
    @Idempotent(key = "ydsz:nextwiki:FileController:move:lock", ttlSeconds = 5)
    @PutMapping("/{nodeId}/move")
    @Operation(summary = "移动文件/文件夹")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
    public BaseResponse<FileNodeVO> move(
            @PathVariable String nodeId,
            @Valid @RequestBody NextwikiDTOs.MoveRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.move(nodeId, request.getTargetParentId(), userId);
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.UPDATE, content = "'rename'")
    @Idempotent(key = "ydsz:nextwiki:FileController:rename:lock", ttlSeconds = 5)
    @PutMapping("/{nodeId}/rename")
    @Operation(summary = "重命名文件/文件夹")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_RENAME)
    public BaseResponse<FileNodeVO> rename(
            @PathVariable String nodeId,
            @Valid @RequestBody NextwikiDTOs.RenameRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rename(nodeId, request.getNewName(), userId);
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.DELETE, content = "'delete'")
    @Idempotent(key = "ydsz:nextwiki:FileController:delete:lock", ttlSeconds = 5)
    @DeleteMapping("/{nodeId}")
    @Operation(summary = "删除文件/文件夹（移入回收站）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
    public BaseResponse<Void> delete(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.delete(nodeId, userId);
        healthIndicator.recordDelete();
        return BaseResponse.success();
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'batchDelete'")
    @Idempotent(key = "ydsz:nextwiki:FileController:batchDelete:lock", ttlSeconds = 5)
    @PostMapping("/batch/delete")
    @Operation(summary = "批量删除")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
    public BaseResponse<FileApplicationService.BatchResult> batchDelete(
            @RequestBody List<String> nodeIds,
            @RequestHeader("X-User-Id") String userId) {

        FileApplicationService.BatchResult result = fileApplicationService.batchDelete(nodeIds, userId);
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'batchMove'")
    @Idempotent(key = "ydsz:nextwiki:FileController:batchMove:lock", ttlSeconds = 5)
    @PostMapping("/batch/move")
    @Operation(summary = "批量移动")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
    public BaseResponse<FileApplicationService.BatchResult> batchMove(
            @Valid @RequestBody NextwikiDTOs.BatchMoveRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileApplicationService.BatchResult result = fileApplicationService.batchMove(
                request.getNodeIds(), request.getTargetParentId(), userId);
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'copy'")
    @Idempotent(key = "ydsz:nextwiki:FileController:copy:lock", ttlSeconds = 5)
    @PostMapping("/{nodeId}/copy")
    @Operation(summary = "复制文件")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_COPY)
    public BaseResponse<FileNodeVO> copy(
            @PathVariable String nodeId,
            @RequestParam("targetParentId") String targetParentId,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.copy(nodeId, targetParentId, userId);
        return BaseResponse.success(result);
    }

    @GetMapping("/{nodeId}/versions")
    @Operation(summary = "获取版本历史")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_VIEW)
    public BaseResponse<List<FileVersion>> getVersionHistory(@PathVariable String nodeId) {
        return BaseResponse.success(fileApplicationService.getVersionHistory(nodeId));
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'rollbackVersion'")
    @Idempotent(key = "ydsz:nextwiki:FileController:rollbackVersion:lock", ttlSeconds = 5)
    @PostMapping("/{nodeId}/versions/{version}/rollback")
    @Operation(summary = "回滚到指定版本")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_ROLLBACK)
    public BaseResponse<FileNodeVO> rollbackVersion(
            @PathVariable String nodeId,
            @PathVariable Integer version,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rollbackVersion(nodeId, version, userId);
        return BaseResponse.success(result);
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.UPDATE, content = "'toggleStar'")
    @Idempotent(key = "ydsz:nextwiki:FileController:toggleStar:lock", ttlSeconds = 5)
    @PutMapping("/{nodeId}/star")
    @Operation(summary = "切换星标状态")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_STAR)
    public BaseResponse<Void> toggleStar(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.toggleStar(nodeId, userId);
        return BaseResponse.success();
    }

    // ==================== P1-1: 分片上传 ====================

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'initChunkUpload'")
    @Idempotent(key = "ydsz:nextwiki:FileController:initChunkUpload:lock", ttlSeconds = 5)
    @PostMapping("/chunk/init")
    @Operation(summary = "初始化分片上传", description = "大文件分片上传，支持断点续传")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<ChunkUploadApplicationService.ChunkUploadInit> initChunkUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") long fileSize,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(chunkUploadService.initChunkUpload(
                fileName, fileSize, totalChunks, parentId, userId));
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'uploadChunk'")
    @Idempotent(key = "ydsz:nextwiki:FileController:uploadChunk:lock", ttlSeconds = 5)
    @PostMapping("/chunk/{uploadId}/{chunkNumber}")
    @Operation(summary = "上传单个分片")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkNumber,
            @RequestParam("chunk") MultipartFile chunk) {
        chunkUploadService.uploadChunk(uploadId, chunkNumber, chunk);
        return BaseResponse.success();
    }

    @Audit(module = "文件管理", type = AuditType.FILE, action = AuditAction.CREATE, content = "'completeChunkUpload'")
    @Idempotent(key = "ydsz:nextwiki:FileController:completeChunkUpload:lock", ttlSeconds = 5)
    @PostMapping("/chunk/{uploadId}/complete")
    @Operation(summary = "完成分片上传", description = "合并分片并上传到存储")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<FileNodeVO> completeChunkUpload(
            @PathVariable String uploadId,
            @RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(chunkUploadService.completeChunkUpload(uploadId, userId));
    }

    @RateLimit(resource = "nextwiki.file.abortChunkUpload", threshold = 50)
    @Idempotent(key = "ydsz:nextwiki:FileController:abortChunkUpload:lock", ttlSeconds = 5)
    @DeleteMapping("/chunk/{uploadId}")
    @Operation(summary = "取消分片上传")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> abortChunkUpload(@PathVariable String uploadId) {
        chunkUploadService.abortChunkUpload(uploadId);
        return BaseResponse.success();
    }

    @GetMapping("/chunk/{uploadId}/uploaded-chunks")
    @Operation(summary = "查询已上传分片列表", description = "用于断点续传")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Set<Integer>> getUploadedChunks(@PathVariable String uploadId) {
        return BaseResponse.success(chunkUploadService.getUploadedChunks(uploadId));
    }
}
