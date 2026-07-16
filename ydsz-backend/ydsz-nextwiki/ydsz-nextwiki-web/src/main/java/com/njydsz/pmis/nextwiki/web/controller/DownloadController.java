package com.njydsz.nextwiki.web.controller;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/download")
@RequiredArgsConstructor
@Tag(name = "文件下载", description = "文件下载、签名URL生成、限流防盗链")
public class DownloadController {

    private final DownloadApplicationService downloadApplicationService;
    private final NextwikiHealthIndicator healthIndicator;

    /**
     * 下载文件
     */
    @PostMapping("/{nodeId}")
    @Operation(summary = "下载文件", description = "下载前校验限流和防盗链")
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
        setDownloadHeaders(response, fileNode.getName(), fileNode.getMimeType());
        storage.download(fileNode.getBucketName(), fileNode.getStorageKey(), response);

        healthIndicator.recordDownload();
        log.info("[DownloadController] 文件下载: nodeId={}, userId={}, ip={}",
                nodeId, userId, ip);
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
        return BaseResponse.ok(signedUrl);
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

    private String getClientIp(HttpServletRequest request) {
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
