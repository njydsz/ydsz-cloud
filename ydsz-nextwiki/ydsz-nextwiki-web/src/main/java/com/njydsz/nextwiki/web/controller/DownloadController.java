package com.njydsz.nextwiki.web.controller;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;
import com.njydsz.nextwiki.server.service.DownloadApplicationService;
import com.njydsz.nextwiki.server.service.DownloadApplicationService.DownloadContext;
import com.njydsz.nextwiki.server.service.DownloadApplicationService.SignedDownloadContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件下载 REST API Controller。
 *
 * <p>提供文件下载能力，集成下载限流、防盗链验证、断点续传、签名 URL 等关键能力：
 *
 * <ul>
 *   <li>单文件下载：{@code POST /download/{nodeId}} - 支持 HTTP Range 断点续传
 *   <li>文件夹打包下载：{@code POST /download/folder/{folderId}} - 递归打包为 ZIP
 *   <li>签名 URL 生成：{@code POST /download/{nodeId}/signed-url} - 生成带时效/IP 绑定的下载链接
 *   <li>签名 URL 下载：{@code GET /download/signed/{sign}?expires=...} - 通过签名链接下载
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>限流防盗链：每个 IP 维度的下载频率限制，超额后拒绝
 *   <li>断点续传：支持 HTTP {@code Range} 请求头，实现大文件分片下载/断点续传
 *   <li>签名 URL：生成包含 HMAC 签名的临时下载链接，可指定过期时间和绑定 IP
 *   <li>ZIP 打包：递归遍历文件夹并将所有文件打包为 ZIP 流式输出
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有接口均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有接口均加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_DOWNLOAD）
 *   <li>下载限流：基于 {@link DownloadApplicationService} 实现 IP 维度的限流
 *   <li>防盗链：校验 {@code Referer} 头，拒绝外部站点直接引用
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST /api/v1/nextwiki/download/{nodeId}                  - 单文件下载
 *   POST /api/v1/nextwiki/download/folder/{folderId}         - 文件夹 ZIP 打包下载
 *   POST /api/v1/nextwiki/download/{nodeId}/signed-url       - 生成签名 URL
 *   GET  /api/v1/nextwiki/download/signed/{sign}?expires=... - 签名 URL 下载
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.DownloadApplicationService
 *                                            ↓
 *                                   ydsz-common-safe (ClientIpResolver / 限流)
 *                                   ydsz-common-file (IFileStorage 抽象)
 *                                            ↓
 *                                   ydsz-nextwiki-infra Mapper
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/download")
@RequiredArgsConstructor
@Tag(name = "文件下载", description = "文件下载、签名URL生成、限流防盗链、Range 断点续传")
public class DownloadController {

  /** 下载应用服务（封装下载上下文准备、签名 URL 生成、限流等） */
  private final DownloadApplicationService downloadApplicationService;

  /** Micrometer 指标采集（记录下载次数） */
  private final NextwikiMetrics nextwikiMetrics;

  /** 文件节点仓储（用于文件夹子节点递归查询） */
  private final FileNodeRepository fileNodeRepository;

  /**
   * 将指定文件夹递归打包为 ZIP 流式下载。
   *
   * <p>遍历文件夹下所有文件和子文件夹，通过 {@link ZipOutputStream} 实时打包并写入 HTTP 响应流。
   * 单个文件读取/写入失败不会中断整个打包流程，仅跳过该文件并记录日志。
   *
   * @param folderId 文件夹节点 ID
   * @param userId 当前用户 ID
   * @param response HTTP 响应对象（用于流式写入 ZIP 内容）
   */
  @Idempotent(key = "ydsz:nextwiki:DownloadController:downloadFolder:lock", ttlSeconds = 5)
  @PostMapping("/folder/{folderId}")
  @Operation(summary = "打包下载文件夹", description = "将整个文件夹打包为 ZIP 下载")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
  public void downloadFolder(
      @PathVariable String folderId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      HttpServletResponse response) {

    FileNode folder = fileNodeRepository.findById(folderId);
    if (folder == null || !folder.isFolder()) {
      throw new BusinessException(NextwikiExceptionCode.FILE_NOT_FOUND);
    }

    String zipName = folder.getName() + ".zip";
    setDownloadHeaders(response, zipName, "application/zip");

    try (ZipOutputStream zos =
        new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
      downloadFolderRecursive(folder, zos, userId, "");
      zos.finish();
      zos.flush();
    } catch (Exception e) {
      log.error("[DownloadController] 文件夹打包下载失败: folderId={}", folderId, e);
      throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
    }

    nextwikiMetrics.recordDownload();
    log.info("[DownloadController] 文件夹打包下载: folderId={}, userId={}", folderId, userId);
  }

  /**
   * 递归将文件夹内容写入 ZIP 流。
   *
   * <p>深度优先遍历文件夹：先写入空目录条目（{@code xxx/}），再递归处理子节点。 对每个文件，从 {@link IFileStorage} 读取输入流并写入 ZIP。
   *
   * @param folder 当前处理的文件夹节点
   * @param zos ZIP 输出流
   * @param userId 当前用户 ID
   * @param basePath 当前 ZIP 条目的父路径（递归累加）
   */
  private void downloadFolderRecursive(
      FileNode folder, ZipOutputStream zos, String userId, String basePath) {
    List<FileNode> children = fileNodeRepository.findChildren(folder.getId());
    if (children == null) return;

    IFileStorage storage = downloadApplicationService.resolveStorageForDownload();

    for (FileNode child : children) {
      String entryPath = basePath.isEmpty() ? child.getName() : basePath + "/" + child.getName();
      if (child.isFolder()) {
        try {
          zos.putNextEntry(new ZipEntry(entryPath + "/"));
          zos.closeEntry();
        } catch (Exception e) {
          log.warn("[DownloadController] 添加目录条目失败: {}", entryPath, e);
        }
        downloadFolderRecursive(child, zos, userId, entryPath);
      } else {
        try {
          zos.putNextEntry(new ZipEntry(entryPath));
          if (storage != null && child.getStorageKey() != null) {
            try (InputStream is =
                storage.downloadAsStream(child.getBucketName(), child.getStorageKey())) {
              is.transferTo(zos);
            }
          }
          zos.closeEntry();
        } catch (Exception e) {
          log.warn("[DownloadController] 添加文件条目失败: {}", entryPath, e);
        }
      }
    }
  }

  /**
   * 下载文件（支持 HTTP Range 断点续传）。
   *
   * <p>根据请求是否携带 {@code Range} 头走两条路径：
   *
   * <ul>
   *   <li>有 Range 头 → 调用 {@link #handleRangeDownload} 返回 206 Partial Content
   *   <li>无 Range 头 → 走全量下载路径
   * </ul>
   *
   * <p>下载前会通过 {@link DownloadApplicationService#prepareDownload} 校验限流与权限。
   *
   * @param nodeId 文件节点 ID
   * @param userId 当前用户 ID
   * @param request HTTP 请求
   * @param response HTTP 响应（用于写入文件流）
   */
  @Idempotent(key = "ydsz:nextwiki:DownloadController:download:lock", ttlSeconds = 5)
  @PostMapping("/{nodeId}")
  @Operation(summary = "下载文件", description = "支持断点续传（Range 请求），下载前校验限流和防盗链")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
  public void download(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      HttpServletRequest request,
      HttpServletResponse response) {

    String ip = getClientIp(request);

    DownloadContext context = downloadApplicationService.prepareDownload(nodeId, userId, ip);
    IFileStorage storage = context.getStorage();
    if (storage == null) {
      throw new BusinessException(NextwikiExceptionCode.FILE_STORAGE_NOT_CONFIGURED);
    }

    FileNode fileNode = context.getFileNode();

    // P0-3: 支持 HTTP Range 断点续传
    String rangeHeader = request.getHeader("Range");
    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
      handleRangeDownload(storage, fileNode, rangeHeader, response);
    } else {
      setDownloadHeaders(response, fileNode.getName(), fileNode.getMimeType());
      if (fileNode.getSize() != null) {
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileNode.getSize()));
      }
      response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
      storage.download(fileNode.getBucketName(), fileNode.getStorageKey(), response);
    }

    nextwikiMetrics.recordDownload();
    log.info(
        "[DownloadController] 文件下载: nodeId={}, userId={}, ip={}, range={}",
        nodeId,
        userId,
        ip,
        rangeHeader);
  }

  /**
   * 处理 HTTP Range 请求，实现断点续传 / 分片下载。
   *
   * <p>解析 {@code bytes=start-end} 格式的 Range 头，将文件指定区间的内容写入响应。 异常情况（如 start > end / start 越界）返回 416
   * Requested Range Not Satisfiable。
   *
   * @param storage 文件存储抽象
   * @param fileNode 文件节点（含 size / storageKey / mimeType 等）
   * @param rangeHeader HTTP Range 头（含 {@code bytes=} 前缀）
   * @param response HTTP 响应
   */
  private void handleRangeDownload(
      IFileStorage storage, FileNode fileNode, String rangeHeader, HttpServletResponse response) {
    long fileSize = fileNode.getSize() != null ? fileNode.getSize() : 0;
    String rangeValue = rangeHeader.substring(6); // strip "bytes="
    String[] parts = rangeValue.split("-");
    long start = 0;
    long end = fileSize - 1;
    try {
      start = Long.parseLong(parts[0].trim());
      if (parts.length > 1 && !parts[1].trim().isEmpty()) {
        end = Long.parseLong(parts[1].trim());
      }
    } catch (NumberFormatException e) {
      response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      return;
    }
    if (start > end || start >= fileSize) {
      response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
      response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      return;
    }
    if (end >= fileSize) {
      end = fileSize - 1;
    }
    long contentLength = end - start + 1;
    response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize);
    response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
    setDownloadHeaders(response, fileNode.getName(), fileNode.getMimeType());
    try (InputStream is =
        storage.downloadAsStream(fileNode.getBucketName(), fileNode.getStorageKey())) {
      long skipped = is.skip(start);
      if (skipped < start) {
        log.warn("[DownloadController] skip 不足: start={}, skipped={}", start, skipped);
      }
      long remaining = contentLength;
      byte[] buffer = new byte[8192];
      while (remaining > 0) {
        int toRead = (int) Math.min(buffer.length, remaining);
        int read = is.read(buffer, 0, toRead);
        if (read == -1) {
          break;
        }
        response.getOutputStream().write(buffer, 0, read);
        remaining -= read;
      }
      response.getOutputStream().flush();
    } catch (Exception e) {
      log.error("[DownloadController] Range 下载失败: nodeId={}", fileNode.getId(), e);
      throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
    }
  }

  /**
   * 生成带时效和 IP 绑定的签名下载 URL。
   *
   * <p>URL 包含 HMAC 签名，访问时校验：
   *
   * <ul>
   *   <li>签名是否合法（防篡改）
   *   <li>是否在有效期内（{@code expires}）
   *   <li>客户端 IP 是否匹配（防链接外传）
   * </ul>
   *
   * @param nodeId 文件节点 ID
   * @param userId 当前用户 ID
   * @param request HTTP 请求（用于获取客户端 IP）
   * @return 统一响应结果，data 为签名 URL 字符串
   */
  @Idempotent(key = "ydsz:nextwiki:DownloadController:generateSignedUrl:lock", ttlSeconds = 5)
  @PostMapping("/{nodeId}/signed-url")
  @Operation(summary = "生成签名下载URL", description = "生成带时效性和IP绑定的签名下载链接")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
  public BaseResponse<String> generateSignedUrl(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      HttpServletRequest request) {

    String ip = getClientIp(request);
    String signedUrl = downloadApplicationService.generateSignedUrl(nodeId, userId, ip);

    log.info("[DownloadController] 生成签名URL: nodeId={}, userId={}", nodeId, userId);
    return BaseResponse.success(signedUrl);
  }

  /**
   * 通过签名 URL 下载文件（公开接口，无需登录）。
   *
   * <p>校验签名的合法性、有效期、IP 绑定后，将文件流式写入 HTTP 响应。 用于对接外部系统（IM 机器人、邮件附件链接等）安全分发文件。
   *
   * @param sign 签名片段（URL 路径段）
   * @param expireTime 过期时间戳（毫秒）
   * @param request HTTP 请求
   * @param response HTTP 响应
   */
  @GetMapping("/signed/{sign}")
  @Operation(summary = "通过签名URL下载文件")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
  public void downloadBySignedUrl(
      @PathVariable String sign,
      @RequestParam("expires") long expireTime,
      HttpServletRequest request,
      HttpServletResponse response) {

    SignedDownloadContext context =
        downloadApplicationService.resolveSignedDownload(sign, expireTime);
    IFileStorage storage = context.getStorage();
    if (storage == null) {
      throw new BusinessException(NextwikiExceptionCode.FILE_STORAGE_NOT_CONFIGURED);
    }

    String storageKey = context.getStorageKey();
    String fileName = extractFileNameFromStorageKey(storageKey);
    setDownloadHeaders(response, fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE);

    try (InputStream is = storage.downloadAsStream(null, storageKey)) {
      is.transferTo(response.getOutputStream());
      response.getOutputStream().flush();
    } catch (Exception e) {
      log.error("[DownloadController] 签名URL下载失败: sign={}", sign, e);
      throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
    }

    log.info("[DownloadController] 签名URL下载: sign={}", sign);
  }

  // ==================== 私有方法（HTTP 层处理） ====================

  /**
   * 解析客户端真实 IP。
   *
   * <p>统一使用 common-safe 模块的 {@link ClientIpResolver} 解析。
   *
   * @param request HTTP 请求
   * @return 客户端 IP
   */
  private String getClientIp(HttpServletRequest request) {
    return ClientIpResolver.getClientIp(request);
  }

  /**
   * 设置下载响应头（{@code Content-Disposition} + {@code Content-Type}）。
   *
   * <p>采用双形式文件名（普通 + RFC 5987 {@code filename*}）以兼容主流浏览器。
   *
   * @param response HTTP 响应
   * @param fileName 文件名（用于 {@code Content-Disposition}，可包含中文）
   * @param mimeType MIME 类型（可空，空时使用 {@code application/octet-stream}）
   */
  private void setDownloadHeaders(HttpServletResponse response, String fileName, String mimeType) {
    String encodedName =
        URLEncoder.encode(fileName != null ? fileName : "download", StandardCharsets.UTF_8)
            .replace("+", "%20");
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
    response.setContentType(mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }

  /**
   * 从 storageKey 中提取文件名。
   *
   * <p>取路径最后一段；空字符串或 null 时返回默认 {@code download}。
   *
   * @param storageKey 对象存储的 key 路径
   * @return 文件名
   */
  private String extractFileNameFromStorageKey(String storageKey) {
    if (storageKey == null || storageKey.isEmpty()) {
      return "download";
    }
    int lastSlash = storageKey.lastIndexOf('/');
    String name = lastSlash >= 0 ? storageKey.substring(lastSlash + 1) : storageKey;
    return name.isEmpty() ? "download" : name;
  }
}
