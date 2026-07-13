package com.njydsz.pmis.nextwiki.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.core.response.Result;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorageProvider;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.server.service.DownloadRateLimitService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件下载 REST API
 * <p>
 * 提供文件下载接口，集成下载限流与防盗链验证。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/download")
@RequiredArgsConstructor
@Tag(name = "文件下载", description = "文件下载、签名URL生成、限流防盗链")
public class DownloadController {

    private final FileNodeRepository fileNodeRepository;
    private final DownloadRateLimitService rateLimitService;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 下载文件
     */
    @PostMapping("/{nodeId}")
    @Operation(summary = "下载文件", description = "下载前校验限流和防盗链")
    public void download(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request,
            HttpServletResponse response) {

        FileNode fileNode = fileNodeRepository.findById(nodeId);
        if (fileNode == null || !fileNode.isFile()) {
            throw BusinessException.builder().key("文件节点不存在或不是文件: " + nodeId).build();
        }

        String ip = getClientIp(request);

        DownloadRateLimitService.RateLimitResult rateResult =
                rateLimitService.checkRateLimit(userId, ip, nodeId);
        if (!rateResult.isAllowed()) {
            throw BusinessException.builder().key(rateResult.getMessage()).build();
        }

        IFileStorage storage = resolveStorage();
        if (storage == null) {
            throw BusinessException.builder().key("文件存储未配置").build();
        }

        storage.download(fileNode.getBucketName(), fileNode.getStorageKey(), response);

        log.info("[DownloadController] 文件下载: nodeId={}, userId={}, ip={}",
                nodeId, userId, ip);
    }

    /**
     * 生成签名下载 URL
     */
    @PostMapping("/{nodeId}/signed-url")
    @Operation(summary = "生成签名下载URL", description = "生成带时效性和IP绑定的签名下载链接")
    public Result<String> generateSignedUrl(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request) {

        FileNode fileNode = fileNodeRepository.findById(nodeId);
        if (fileNode == null || !fileNode.isFile()) {
            throw BusinessException.builder().key("文件节点不存在或不是文件: " + nodeId).build();
        }

        String ip = getClientIp(request);
        String signedUrl = rateLimitService.generateSignedDownloadUrl(
                fileNode.getStorageKey(), userId, ip);

        log.info("[DownloadController] 生成签名URL: nodeId={}, userId={}", nodeId, userId);
        return Result.ok(signedUrl);
    }

    /**
     * 通过签名 URL 下载文件
     */
    @GetMapping("/{sign}")
    @Operation(summary = "通过签名URL下载文件")
    public void downloadBySignedUrl(
            @PathVariable String sign,
            @RequestParam("expires") long expireTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        String storageKey = rateLimitService.verifySignedUrl(sign, expireTime);
        if (storageKey == null) {
            throw BusinessException.builder().key("签名URL无效或已过期").build();
        }

        IFileStorage storage = resolveStorage();
        if (storage == null) {
            throw BusinessException.builder().key("文件存储未配置").build();
        }

        storage.download(null, storageKey, response);
        log.info("[DownloadController] 签名URL下载: sign={}", sign);
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

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
}
