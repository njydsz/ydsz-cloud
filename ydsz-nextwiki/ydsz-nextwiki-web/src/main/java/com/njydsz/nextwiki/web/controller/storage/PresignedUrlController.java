package com.njydsz.nextwiki.web.controller.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.nextwiki.domain.dto.NextwikiDto;

/**
 * 存储直传（Presigned URL）REST API Controller。
 *
 * <p>提供文件上传/下载的预签名 URL 生成能力，前端可凭此 URL 直接与云存储交互，无需经过服务端中转。
 *
 * <ul>
 *   <li>{@code POST /storage/presigned-upload} - 生成上传预签名 URL
 *   <li>{@code POST /storage/presigned-download} - 生成下载预签名 URL
 * </ul>
 *
 * <p>使用场景：
 *
 * <ul>
 *   <li>大文件直传：减轻服务端带宽压力
 *   <li>客户端上传：移动端/Web 端直接上传到对象存储
 *   <li>临时分享：生成临时下载链接
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ApiVersion("v1")
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/storage")
@RequiredArgsConstructor
@Tag(name = "存储直传", description = "Presigned URL 生成、直传凭证")
public class PresignedUrlController {

  /** 文件存储实现 */
  private final IFileStorage fileStorage;

  /**
   * 生成上传预签名 URL。
   *
   * <p>前端可凭此 PUT URL 直接上传文件到对象存储，上传完成后回调服务端确认。
   *
   * @param request 预签名请求（objectKey / expireSeconds）
   * @param userId 当前用户 ID
   * @return 预签名上传 URL
   */
  @PostMapping("/presigned-upload")
  @Operation(summary = "生成上传预签名 URL")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
  public YdszResponse<PresignedUrlResponse> generateUploadUrl(
      @Valid @RequestBody NextwikiDto.PresignedUrlRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String url =
        fileStorage.generatePresignedUploadUrl(
            null,
            request.getObjectKey(),
            Duration.ofSeconds(
                request.getExpireSeconds() != null ? request.getExpireSeconds() : 3600));

    PresignedUrlResponse response = new PresignedUrlResponse();
    response.setPresignedUrl(url);
    response.setObjectKey(request.getObjectKey());
    response.setExpireSeconds(
        request.getExpireSeconds() != null ? request.getExpireSeconds() : 3600);
    response.setMethod("PUT");

    return YdszResponse.success(response);
  }

  /**
   * 生成下载预签名 URL。
   *
   * <p>前端可凭此 GET URL 直接从对象存储下载文件，无需服务端中转。
   *
   * @param request 预签名请求（objectKey / expireSeconds）
   * @param userId 当前用户 ID
   * @return 预签名下载 URL
   */
  @PostMapping("/presigned-download")
  @Operation(summary = "生成下载预签名 URL")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DOWNLOAD)
  public YdszResponse<PresignedUrlResponse> generateDownloadUrl(
      @Valid @RequestBody NextwikiDto.PresignedUrlRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String url =
        fileStorage.generatePresignedUrl(
            request.getObjectKey(),
            request.getExpireSeconds() != null ? request.getExpireSeconds() : 3600);

    PresignedUrlResponse response = new PresignedUrlResponse();
    response.setPresignedUrl(url);
    response.setObjectKey(request.getObjectKey());
    response.setExpireSeconds(
        request.getExpireSeconds() != null ? request.getExpireSeconds() : 3600);
    response.setMethod("GET");

    return YdszResponse.success(response);
  }

  /** 预签名 URL 响应。 */
  @lombok.Data
  public static class PresignedUrlResponse {
    /** 预签名 URL */
    private String presignedUrl;

    /** 对象存储键 */
    private String objectKey;

    /** 过期时间（秒） */
    private Integer expireSeconds;

    /** HTTP 方法（PUT/GET） */
    private String method;
  }
}
