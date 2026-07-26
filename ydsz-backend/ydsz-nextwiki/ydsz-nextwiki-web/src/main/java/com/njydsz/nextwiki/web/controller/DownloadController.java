package com.njydsz.nextwiki.web.controller;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.server.health.NextwikiHealthIndicator;
import com.njydsz.nextwiki.server.service.DownloadApplicationService;
import com.njydsz.nextwiki.server.service.DownloadApplicationService.DownloadContext;
import com.njydsz.nextwiki.server.service.DownloadApplicationService.SignedDownloadContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件下载 REST API
 * <p>
 * 提供文件下载接口，集成下载限流与防盗链验证。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/download")
@RequiredArgsConstructor
@Tag(name = "文件下载", description = "文件下载、签名URL生成、限流防盗链")
public class DownloadController {

    private final DownloadApplicationService downloadApplicationService;
    private final NextwikiHealthIndicator healthIndicator;
    private final com.njydsz.nextwiki.domain.repository.FileNodeRepository fileNodeRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.njydsz.common.safe.util.ClientIpResolver clientIpResolver;

    /**
     * P1-3: 文件夹打包下载为 ZIP
     */
    @PostMapping("/folder/{folderId}")
    @Operation(summary = "打包下载文件夹", description = "将整个文件夹打包为 ZIP 下载")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
    public void downloadFolder(
            @PathVariable String folderId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletResponse response) {

        com.njydsz.nextwiki.domain.entity.FileNode folder = fileNodeRepository.findById(folderId);
        if (folder == null || !folder.isFolder()) {
            throw new BusinessException(NextwikiExceptionCode.FILE_NOT_FOUND);
        }

        String zipName = folder.getName() + ".zip";
        setDownloadHeaders(response, zipName, "application/zip");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            downloadFolderRecursive(folder, zos, userId, "");
            zos.finish();
            zos.flush();
        } catch (Exception e) {
            log.error("[DownloadController] 文件夹打包下载失败: folderId={}", folderId, e);
            throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
        }

        healthIndicator.recordDownload();
        log.info("[DownloadController] 文件夹打包下载: folderId={}, userId={}", folderId, userId);
    }

    /**
     * 递归打包文件夹内容到 ZipOutputStream
     */
    private void downloadFolderRecursive(com.njydsz.nextwiki.domain.entity.FileNode folder,
                                           ZipOutputStream zos, String userId, String basePath) {
        List<com.njydsz.nextwiki.domain.entity.FileNode> children =
                fileNodeRepository.findChildren(folder.getId());
        if (children == null) return;

        IFileStorage storage = downloadApplicationService.resolveStorageForDownload();

        for (com.njydsz.nextwiki.domain.entity.FileNode child : children) {
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
                        try (InputStream is = storage.downloadAsStream(
                                child.getBucketName(), child.getStorageKey())) {
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
     * 下载文件（支持 HTTP Range 断点续传）
     */
    @PostMapping("/{nodeId}")
    @Operation(summary = "下载文件", description = "支持断点续传（Range 请求），下载前校验限流和防盗链")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
    public void download(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId,
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

        healthIndicator.recordDownload();
        log.info("[DownloadController] 文件下载: nodeId={}, userId={}, ip={}, range={}",
                nodeId, userId, ip, rangeHeader);
    }

    /**
     * 处理 Range 请求（断点续传）
     */
    private void handleRangeDownload(IFileStorage storage, FileNode fileNode,
                                       String rangeHeader, HttpServletResponse response) {
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
        try (InputStream is = storage.downloadAsStream(fileNode.getBucketName(), fileNode.getStorageKey())) {
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
     * 生成签名下载 URL
     */
    @PostMapping("/{nodeId}/signed-url")
    @Operation(summary = "生成签名下载URL", description = "生成带时效性和IP绑定的签名下载链接")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_DOWNLOAD)
    public BaseResponse<String> generateSignedUrl(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request) {

        String ip = getClientIp(request);
        String signedUrl = downloadApplicationService.generateSignedUrl(nodeId, userId, ip);

        log.info("[DownloadController] 生成签名URL: nodeId={}, userId={}", nodeId, userId);
        return BaseResponse.success(signedUrl);
    }

    /**
     * 通过签名 URL 下载文件
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
     * P2-R3: 委托 ClientIpResolver 解析客户端 IP（可用时委托，不可用降级）
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先使用 common-safe ClientIpResolver（如果 Bean 可用）
        if (clientIpResolver != null) {
            return com.njydsz.common.safe.util.ClientIpResolver.getClientIp(request);
        }
        // 降级到本地解析
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }

    /**
     * 设置下载响应头（Content-Disposition + Content-Type）
     */
    private void setDownloadHeaders(HttpServletResponse response, String fileName, String mimeType) {
        String encodedName = URLEncoder.encode(fileName != null ? fileName : "download",
                StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
        response.setContentType(mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * 从 storageKey 中提取文件名
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
