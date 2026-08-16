package com.njydsz.nextwiki.web.controller;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.service.ChunkUploadApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件分片上传 REST API Controller。
 *
 * <p>从原 {@code FileController} 拆分而来，专门承担大文件分片上传的完整生命周期管理。
 *
 * <h3>业务背景</h3>
 *
 * <p>网盘文件（NextWiki）模块在处理大文件（视频、安装包、设计稿等）上传时，单次 HTTP 请求 难以兼顾网络抖动、超时与内存占用。分片上传方案将大文件切分为多个小分片，逐个上传并支持
 * 断点续传，最终在服务端合并落库，显著提升大文件上传的稳定性与用户体验。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>初始化分片上传：分配全局唯一 uploadId，返回分片大小、已上传分片等元数据
 *   <li>上传单个分片：支持已上传分片覆盖式重传（内部幂等校验）
 *   <li>完成分片上传：校验完整性 → 合并 → 写入对象存储 → 创建文件节点 → 清理临时分片
 *   <li>取消分片上传：删除已上传分片和临时状态，uploadId 失效
 *   <li>查询已上传分片：返回已成功上传的分片序号集合，用于断点续传
 * </ul>
 *
 * <h3>典型使用流程</h3>
 *
 * <pre>
 *   1. 调用 initChunkUpload 拿到 uploadId
 *   2. 调用 getUploadedChunks 检查已上传分片（断点续传关键）
 *   3. 仅上传缺失的分片到 uploadChunk
 *   4. 全部上传完成后调用 completeChunkUpload 合并
 *   5. 如需放弃上传，调用 abortChunkUpload 清理
 * </pre>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（FILE 类型）
 *   <li>所有写操作均加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_FILE_UPLOAD）
 *   <li>取消分片上传为高频操作，加 {@link RateLimit} 限流（50 QPS）
 *   <li>用户身份通过 {@code X-User-Id} 请求头传递（由网关层注入）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST   /api/v1/nextwiki/files/chunk/init                       - 初始化分片上传
 *   POST   /api/v1/nextwiki/files/chunk/{uploadId}/{chunkNumber}   - 上传单个分片
 *   POST   /api/v1/nextwiki/files/chunk/{uploadId}/complete        - 完成分片上传（合并）
 *   DELETE /api/v1/nextwiki/files/chunk/{uploadId}                 - 取消分片上传
 *   GET    /api/v1/nextwiki/files/chunk/{uploadId}/uploaded-chunks - 已上传分片列表
 * </pre>
 *
 * <h3>相关拆分</h3>
 *
 * <ul>
 *   <li>{@link FileController} — 核心单文件操作（上传/目录/移动/重命名/删除/复制）
 *   <li>{@link FileBatchController} — 批量操作、版本管理、星标
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Validated
@Tag(name = "网盘文件分片上传", description = "大文件分片上传、断点续传、合并/取消/查询")
public class FileChunkController {

  /** 分片上传服务（封装大文件分片上传 + 断点续传） */
  private final ChunkUploadApplicationService chunkUploadService;

  /**
   * 初始化分片上传。
   *
   * <p>为待上传的大文件分配一个全局唯一的 uploadId，并返回分片大小、已上传分片等元数据。 客户端基于该 uploadId 后续可调用 {@link #uploadChunk}
   * 上传分片， 调用 {@link #getUploadedChunks} 实现断点续传。
   *
   * <p>典型使用流程：
   *
   * <pre>
   *   1. 调用本接口拿到 uploadId
   *   2. 调用 getUploadedChunks 检查已上传分片（断点续传关键）
   *   3. 仅上传缺失的分片到 uploadChunk
   *   4. 全部上传完成后调用 completeChunkUpload 合并
   * </pre>
   *
   * @param fileName 文件名（含扩展名）
   * @param fileSize 文件总大小（字节）
   * @param totalChunks 总分片数
   * @param parentId 父目录 ID（可空表示根目录）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link ChunkUploadApplicationService.ChunkUploadInit}（含 uploadId /
   *     chunkSize 等）
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'initChunkUpload'")
  @Idempotent(key = "ydsz:nextwiki:FileController:initChunkUpload:lock", ttlSeconds = 5)
  @PostMapping("/chunk/init")
  @Operation(summary = "初始化分片上传", description = "大文件分片上传，支持断点续传")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public BaseResponse<ChunkUploadApplicationService.ChunkUploadInit> initChunkUpload(
      @RequestParam("fileName") String fileName,
      @RequestParam("fileSize") long fileSize,
      @RequestParam("totalChunks") int totalChunks,
      @RequestParam(value = "parentId", required = false) String parentId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(
        chunkUploadService.initChunkUpload(fileName, fileSize, totalChunks, parentId, userId));
  }

  /**
   * 上传单个分片。
   *
   * <p>将第 {@code chunkNumber} 个分片上传到临时存储区。已上传的分片可重复上传（覆盖式）， 由 {@code ChunkUploadApplicationService}
   * 内部做幂等校验。
   *
   * @param uploadId 上传任务 ID
   * @param chunkNumber 分片序号（从 0 开始）
   * @param chunk 分片文件
   * @return 统一响应结果
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'uploadChunk'")
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
   * <p>校验所有分片已上传完成 → 合并为完整文件 → 写入对象存储 → 在文件节点表创建记录。 合并完成后会清理临时分片数据。
   *
   * @param uploadId 上传任务 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为合并后的文件节点信息
   */
  @Audit(
      module = "文件管理",
      type = AuditType.FILE,
      action = AuditAction.CREATE,
      content = "'completeChunkUpload'")
  @Idempotent(key = "ydsz:nextwiki:FileController:completeChunkUpload:lock", ttlSeconds = 5)
  @PostMapping("/chunk/{uploadId}/complete")
  @Operation(summary = "完成分片上传", description = "合并分片并上传到存储")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public BaseResponse<FileNodeVO> completeChunkUpload(
      @PathVariable String uploadId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return BaseResponse.success(chunkUploadService.completeChunkUpload(uploadId, userId));
  }

  /**
   * 取消分片上传。
   *
   * <p>删除已上传的分片和临时状态，用于客户端主动放弃上传或服务端超时清理。 取消后该 uploadId 即失效，不能再继续上传。
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
