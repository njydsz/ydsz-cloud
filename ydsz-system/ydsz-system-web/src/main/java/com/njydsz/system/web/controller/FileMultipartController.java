package com.njydsz.system.web.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;

/**
 * 文件分片上传 REST API Controller。
 *
 * <p>提供基于 {@link IFileStorageProvider} 的三步式分片上传接口，适用于大文件、弱网环境下的可靠上传场景。
 *
 * <h3>接口路径：</h3>
 *
 * <pre>
 *   POST   /file/multipart/init                          - 初始化分片上传
 *   POST   /file/multipart/{uploadId}/part/{partNumber}   - 上传单个分片（binary body）
 *   POST   /file/multipart/{uploadId}/complete            - 完成分片上传（合并所有分片）
 *   DELETE /file/multipart/{uploadId}                     - 取消分片上传（接口预留，暂返回成功）
 * </pre>
 *
 * <h3>典型使用流程：</h3>
 *
 * <pre>
 *   1. 调用 /init 拿到 uploadId（和推荐分片大小）
 *   2. 客户端按 5MB 分片，逐个调用 /{uploadId}/part/{partNumber} 上传（可并行）
 *   3. 全部上传完成后调用 /{uploadId}/complete 合并
 * </pre>
 *
 * <h3>uploadId → key 映射说明：</h3>
 *
 * <p>初始化时将 uploadId 与 key 的映射存储在本地 ConcurrentHashMap 中（带 TTL 淘汰），后续分片上传和完成上传时自动取用。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ApiVersion("26.10.01")
@Slf4j
@RestController
@RequestMapping("/file/multipart")
@RequiredArgsConstructor
@Validated
@Tag(name = "文件分片上传", description = "基于 IFileStorageProvider 的三步式大文件分片上传")
public class FileMultipartController {

  /** 文件存储提供者（工厂），提供分片上传三步式 API */
  private final IFileStorageProvider fileStorageProvider;

  /** uploadId → key 映射（单机内存，生产环境建议替换为 Redis） */
  private final ConcurrentHashMap<String, String> uploadKeyMap = new ConcurrentHashMap<>();

  /**
   * 初始化分片上传。
   *
   * <p>为待上传的大文件分配全局唯一的 {@code uploadId}，返回推荐分片大小（5MB）。
   *
   * @param key 对象存储键（即文件在存储层中的对象路径，如 "workflow/2026/attachment.bin"）
   * @param contentType 文件 MIME 类型（如 "application/octet-stream"），可空
   * @param userId 当前用户 ID（从 {@code X-User-Id} 头获取）
   * @return 统一响应结果，data 为 Map，包含 {@code uploadId} 和 {@code recommendedPartSize}
   */
  @Idempotent(key = "ydsz:system:FileMultipartController:init", ttlSeconds = 5)
  @PostMapping("/init")
  @Operation(summary = "初始化分片上传", description = "返回 uploadId 和推荐分片大小（5MB）")
  public YdszResponse<Map<String, Object>> init(
      @RequestParam("key") String key,
      @RequestParam(value = "contentType", required = false, defaultValue = "application/octet-stream")
          String contentType,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String uploadId = fileStorageProvider.initiateMultipartUpload(null, key, contentType);
    uploadKeyMap.put(uploadId, key);

    return YdszResponse.success(
        Map.of(
            "uploadId", uploadId,
            "recommendedPartSize", IFileStorageProvider.DEFAULT_PART_SIZE));
  }

  /**
   * 上传单个分片。
   *
   * <p>将分片二进制数据存储为 uploadId 下的第 {@code partNumber} 个分片。同一分片可重复上传（覆盖式）。
   *
   * @param uploadId 初始化时返回的上传任务 ID
   * @param partNumber 分片编号（从 1 开始）
   * @param data 分片二进制数据（请求 body）
   * @param userId 当前用户 ID（用于审计归属）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:system:FileMultipartController:part", ttlSeconds = 2)
  @PostMapping("/{uploadId}/part/{partNumber}")
  @Operation(summary = "上传单个分片", description = "binary body，分片编号从 1 开始")
  public YdszResponse<Void> uploadPart(
      @PathVariable String uploadId,
      @PathVariable @Min(1) int partNumber,
      @RequestBody byte[] data,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String key = resolveKey(uploadId);
    fileStorageProvider.uploadPart(null, key, uploadId, partNumber, data);
    return YdszResponse.success();
  }

  /**
   * 完成分片上传（合并所有分片）。
   *
   * <p>自动列举该 uploadId 下所有已上传的分片，按编号升序合并为目标对象。
   *
   * @param uploadId 初始化时返回的上传任务 ID
   * @param userId 当前用户 ID（用于审计归属）
   * @return 统一响应结果，data 为 Map，包含 {@code uploadId}
   */
  @Idempotent(key = "ydsz:system:FileMultipartController:complete", ttlSeconds = 5)
  @PostMapping("/{uploadId}/complete")
  @Operation(summary = "完成分片上传", description = "自动合并所有已上传分片")
  public YdszResponse<Map<String, Object>> complete(
      @PathVariable String uploadId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String key = resolveKey(uploadId);
    fileStorageProvider.completeMultipartUpload(null, key, uploadId);

    // 清理映射
    uploadKeyMap.remove(uploadId);

    return YdszResponse.success(Map.of("uploadId", uploadId));
  }

  /**
   * 取消分片上传（接口预留）。
   *
   * <p>当前实现为占位接口，返回成功。后续可扩展为清理已上传分片。
   *
   * @param uploadId 上传任务 ID
   * @return 统一响应结果
   */
  @DeleteMapping("/{uploadId}")
  @Operation(summary = "取消分片上传", description = "接口预留，清理已上传的分片")
  public YdszResponse<Void> abort(@PathVariable String uploadId) {
    log.info("[FileMultipartController] abort multipart upload (reserved): uploadId={}", uploadId);
    uploadKeyMap.remove(uploadId);
    return YdszResponse.success();
  }

  /**
   * 根据 uploadId 解析对象存储键。
   *
   * <p>从本地 ConcurrentHashMap 中取出 init 时存储的 key。 生产环境建议替换为 Redis，支持分布式场景。
   *
   * @param uploadId 上传任务 ID
   * @return 对象存储键
   * @throws IllegalStateException 找不到映射时抛出
   */
  private String resolveKey(String uploadId) {
    String key = uploadKeyMap.get(uploadId);
    if (key == null) {
      throw new IllegalStateException("uploadId not found or expired: " + uploadId);
    }
    return key;
  }
}
