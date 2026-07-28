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
 * 文件管理 REST API Controller。
 *
 * <p>网盘文件（NextWiki）模块的核心 REST 接口，提供文件的完整生命周期管理能力：
 * <ul>
 *   <li>基础操作：上传 / 下载 / 移动 / 重命名 / 复制 / 删除（移入回收站）</li>
 *   <li>目录管理：创建目录、列出目录内容（支持排序/过滤/分页）</li>
 *   <li>批量操作：批量删除 / 批量移动</li>
 *   <li>版本管理：版本历史查询、回滚到指定版本</li>
 *   <li>分片上传：大文件分片上传（支持断点续传 + 取消 + 查询已上传分片）</li>
 *   <li>个性化：切换星标</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>单文件上传：基于 {@code MultipartFile} 的标准上传，自动创建首版本</li>
 *   <li>分片上传：基于 {@link ChunkUploadApplicationService} 的断点续传方案，支持大文件秒传</li>
 *   <li>版本管理：每次上传同名文件会创建新版本，支持回滚到任意历史版本</li>
 *   <li>回收站：删除操作实际为"软删除"，文件移入回收站，可由 {@code TrashController} 恢复</li>
 *   <li>目录树：基于 parentId 自引用构建无限层级目录树</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（FILE 类型）</li>
 *   <li>所有写操作均加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_FILE_*）</li>
 *   <li>高频写操作（取消分片）加 {@link RateLimit} 限流（50 QPS）</li>
 *   <li>用户身份通过 {@code X-User-Id} 请求头传递（由网关层注入）</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   POST   /api/v1/nextwiki/files/upload                 - 上传文件
 *   POST   /api/v1/nextwiki/files/folders                - 创建目录
 *   GET    /api/v1/nextwiki/files/list                   - 列出目录内容
 *   PUT    /api/v1/nextwiki/files/{nodeId}/move          - 移动文件/文件夹
 *   PUT    /api/v1/nextwiki/files/{nodeId}/rename        - 重命名
 *   DELETE /api/v1/nextwiki/files/{nodeId}               - 删除（移入回收站）
 *   POST   /api/v1/nextwiki/files/batch/delete           - 批量删除
 *   POST   /api/v1/nextwiki/files/batch/move             - 批量移动
 *   POST   /api/v1/nextwiki/files/{nodeId}/copy          - 复制
 *   GET    /api/v1/nextwiki/files/{nodeId}/versions      - 版本历史
 *   POST   /api/v1/nextwiki/files/{nodeId}/versions/{ver}/rollback - 版本回滚
 *   PUT    /api/v1/nextwiki/files/{nodeId}/star          - 切换星标
 *   POST   /api/v1/nextwiki/files/chunk/init             - 初始化分片上传
 *   POST   /api/v1/nextwiki/files/chunk/{id}/{num}       - 上传分片
 *   POST   /api/v1/nextwiki/files/chunk/{id}/complete    - 合并分片
 *   DELETE /api/v1/nextwiki/files/chunk/{id}             - 取消分片上传
 *   GET    /api/v1/nextwiki/files/chunk/{id}/uploaded-chunks - 已上传分片
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                    ydsz-nextwiki-server.FileApplicationService
 *                                       ├── ChunkUploadApplicationService (分片)
 *                                       └── NextwikiHealthIndicator (指标)
 *                                            ↓
 *                                    ydsz-nextwiki-infra Mapper
 *                                            ↓
 *                                    ydsz_file_node / ydsz_file_version / ydsz_file_chunk
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Validated
@Tag(name = "网盘文件管理", description = "文件上传、下载、移动、重命名、删除、分片上传、版本管理")
public class FileController {

    /** 文件应用服务（封装上传/移动/重命名/复制/删除/版本等业务编排） */
    private final FileApplicationService fileApplicationService;
    /** 健康指标采集器（记录上传/删除次数等关键指标） */
    private final NextwikiHealthIndicator healthIndicator;
    /** 分片上传服务（封装大文件分片上传 + 断点续传） */
    private final ChunkUploadApplicationService chunkUploadService;

    /**
     * 上传文件（单文件模式）。
     *
     * <p>将单个文件上传到指定目录，自动创建首版本记录（version=1）。如目标位置已存在同名文件，
     * 会按版本管理策略递增版本号。同一文件短时间内重复上传会被 {@link Idempotent} 拦截。
     *
     * @param file          源文件（{@code multipart/form-data}）
     * @param parentId      父目录 ID（{@code ydsz_file_node.id}，可空表示根目录）
     * @param rename        重命名（可选；为空则保留原文件名）
     * @param versionRemark 版本备注（可选，描述本次上传的变更点）
     * @param userId        当前用户 ID（从 {@code X-User-Id} 头获取）
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
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.upload(file, parentId, rename, versionRemark, userId);
        healthIndicator.recordUpload();
        return BaseResponse.success(result);
    }

    /**
     * 创建目录。
     *
     * <p>在指定父目录下创建新目录节点。系统会校验目录名在同一父目录下唯一。
     *
     * @param request  创建目录请求（parentId / name）
     * @param userId   当前用户 ID
     * @return 统一响应结果，data 为新创建的目录节点信息
     */
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

    /**
     * 列出指定目录下的内容。
     *
     * <p>支持按字段排序（{@code sortBy} / {@code sortDir}）、类型过滤（{@code type}，如 file/folder/all）
     * 和数据库分页（{@code page} / {@code pageSize}），返回目录内容列表。
     *
     * @param parentId 父目录 ID（可空表示根目录）
     * @param sortBy   排序字段（name/size/createdAt 等）
     * @param sortDir  排序方向（asc/desc）
     * @param type     文件类型过滤（file/folder/all）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @param userId   当前用户 ID（用于权限过滤）
     * @return 统一响应结果，data 为分页结果 {@link PageResult}，含 {@link FileNodeVO} 列表
     */
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

    /**
     * 移动文件或文件夹到指定目录。
     *
     * <p>支持文件和文件夹两种类型；目标位置不能是当前节点的子孙（避免循环引用）。
     *
     * @param nodeId     源节点 ID
     * @param request    移动请求（targetParentId）
     * @param userId     当前用户 ID
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
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.move(nodeId, request.getTargetParentId(), userId);
        return BaseResponse.success(result);
    }

    /**
     * 重命名文件或文件夹。
     *
     * <p>同目录下重名会被拒绝；该操作不影响文件内容、不创建新版本。
     *
     * @param nodeId   节点 ID
     * @param request  重命名请求（newName）
     * @param userId   当前用户 ID
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
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rename(nodeId, request.getNewName(), userId);
        return BaseResponse.success(result);
    }

    /**
     * 删除文件或文件夹（软删除，移入回收站）。
     *
     * <p>删除操作不会立即清理物理文件，而是将节点移入回收站（{@code ydsz_trash_item}），
     * 保留 30 天可由 {@code TrashController} 恢复或彻底删除。文件夹下文件会被级联移入回收站。
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
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.delete(nodeId, userId);
        healthIndicator.recordDelete();
        return BaseResponse.success();
    }

    /**
     * 批量删除文件/文件夹（移入回收站）。
     *
     * <p>返回每条的处理结果（成功/失败原因），由前端根据 {@link FileApplicationService.BatchResult} 展示。
     *
     * @param nodeIds 节点 ID 列表
     * @param userId  当前用户 ID
     * @return 统一响应结果，data 为批量处理结果（successCount / failCount / failures）
     */
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

    /**
     * 批量移动文件/文件夹到指定目录。
     *
     * <p>所有节点必须属于同一用户；目标位置不能是任一节点的子孙。
     *
     * @param request 批量移动请求（nodeIds / targetParentId）
     * @param userId  当前用户 ID
     * @return 统一响应结果，data 为批量处理结果
     */
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

    /**
     * 复制文件到指定目录。
     *
     * <p>复制会生成全新的文件节点和首版本，原文件不受影响。目标位置如有同名文件，
     * 系统会自动追加后缀（如 {@code file(1).txt}）。注意：本接口仅支持文件复制，文件夹复制请使用分批调用 {@link #copy} 或
     * 后续规划接口。
     *
     * @param nodeId         源文件节点 ID
     * @param targetParentId 目标父目录 ID
     * @param userId         当前用户 ID
     * @return 统一响应结果，data 为新复制的文件节点信息
     */
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

    /**
     * 获取文件的版本历史。
     *
     * <p>按版本号倒序返回全部历史版本，每条记录包含版本号、大小、上传人、上传时间、备注等。
     * 当前最新版本固定在列表首位。
     *
     * @param nodeId 文件节点 ID
     * @return 统一响应结果，data 为 {@link FileVersion} 列表
     */
    @GetMapping("/{nodeId}/versions")
    @Operation(summary = "获取版本历史")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_VIEW)
    public BaseResponse<List<FileVersion>> getVersionHistory(@PathVariable String nodeId) {
        return BaseResponse.success(fileApplicationService.getVersionHistory(nodeId));
    }

    /**
     * 回滚文件到指定版本。
     *
     * <p>将当前版本替换为指定历史版本的内容，同时在版本历史中新增一条"回滚"记录。
     * 不会删除任何历史版本，可在版本历史中再次切回。
     *
     * @param nodeId   文件节点 ID
     * @param version  目标版本号
     * @param userId   当前用户 ID
     * @return 统一响应结果，data 为回滚后的文件节点信息（含新版本号）
     */
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

    /**
     * 切换文件星标状态。
     *
     * <p>星标文件会在前端"我的星标"视图中聚合显示，便于快速访问。
     *
     * @param nodeId 节点 ID
     * @param userId 当前用户 ID
     * @return 统一响应结果
     */
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

    /**
     * 初始化分片上传。
     *
     * <p>为待上传的大文件分配一个全局唯一的 uploadId，并返回分片大小、已上传分片等元数据。
     * 客户端基于该 uploadId 后续可调用 {@link #uploadChunk} 上传分片，
     * 调用 {@link #getUploadedChunks} 实现断点续传。
     *
     * <p>典型使用流程：
     * <pre>
     *   1. 调用本接口拿到 uploadId
     *   2. 调用 getUploadedChunks 检查已上传分片（断点续传关键）
     *   3. 仅上传缺失的分片到 uploadChunk
     *   4. 全部上传完成后调用 completeChunkUpload 合并
     * </pre>
     *
     * @param fileName    文件名（含扩展名）
     * @param fileSize    文件总大小（字节）
     * @param totalChunks 总分片数
     * @param parentId    父目录 ID（可空表示根目录）
     * @param userId      当前用户 ID
     * @return 统一响应结果，data 为 {@link ChunkUploadApplicationService.ChunkUploadInit}（含 uploadId / chunkSize 等）
     */
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

    /**
     * 上传单个分片。
     *
     * <p>将第 {@code chunkNumber} 个分片上传到临时存储区。已上传的分片可重复上传（覆盖式），
     * 由 {@code ChunkUploadApplicationService} 内部做幂等校验。
     *
     * @param uploadId    上传任务 ID
     * @param chunkNumber 分片序号（从 0 开始）
     * @param chunk       分片文件
     * @return 统一响应结果
     */
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

    /**
     * 完成分片上传（合并并落库）。
     *
     * <p>校验所有分片已上传完成 → 合并为完整文件 → 写入对象存储 → 在文件节点表创建记录。
     * 合并完成后会清理临时分片数据。
     *
     * @param uploadId 上传任务 ID
     * @param userId   当前用户 ID
     * @return 统一响应结果，data 为合并后的文件节点信息
     */
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

    /**
     * 取消分片上传。
     *
     * <p>删除已上传的分片和临时状态，用于客户端主动放弃上传或服务端超时清理。
     * 取消后该 uploadId 即失效，不能再继续上传。
     *
     * @param uploadId 上传任务 ID
     * @return 统一响应结果
     */
    @RateLimit(resource = "nextwiki.file.abortChunkUpload", threshold = 50)
    @Idempotent(key = "ydsz:nextwiki:FileController:abortChunkUpload:lock", ttlSeconds = 5)
    @DeleteMapping("/chunk/{uploadId}")
    @Operation(summary = "取消分片上传")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> abortChunkUpload(@PathVariable String uploadId) {
        chunkUploadService.abortChunkUpload(uploadId);
        return BaseResponse.success();
    }

    /**
     * 查询已上传的分片列表。
     *
     * <p>返回指定 uploadId 下已成功上传的分片序号集合，客户端据此可跳过已上传分片实现断点续传。
     *
     * @param uploadId 上传任务 ID
     * @return 统一响应结果，data 为已上传分片序号集合
     */
    @GetMapping("/chunk/{uploadId}/uploaded-chunks")
    @Operation(summary = "查询已上传分片列表", description = "用于断点续传")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Set<Integer>> getUploadedChunks(@PathVariable String uploadId) {
        return BaseResponse.success(chunkUploadService.getUploadedChunks(uploadId));
    }
}
