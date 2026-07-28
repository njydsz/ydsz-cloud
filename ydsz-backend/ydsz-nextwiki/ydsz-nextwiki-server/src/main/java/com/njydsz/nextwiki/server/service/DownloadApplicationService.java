package com.njydsz.nextwiki.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件下载服务。
 * <p>处理单文件/批量/断点续传下载。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadApplicationService {

    private final FileNodeRepository fileNodeRepository;
    private final DownloadRateLimitService rateLimitService;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 准备下载：校验文件存在性、限流、解析存储
     *
     * @param nodeId 文件节点ID
     * @param userId 用户ID
     * @param ip     客户端IP
     * @return 下载上下文（FileNode + Storage）
     */
    public DownloadContext prepareDownload(String nodeId, String userId, String ip) {
        FileNode fileNode = fileNodeRepository.findById(nodeId);
        if (fileNode == null || !fileNode.isFile()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        DownloadRateLimitService.RateLimitResult rateResult =
                rateLimitService.checkRateLimit(userId, ip, nodeId);
        if (!rateResult.isAllowed()) {
            throw BusinessException.of(NextwikiExceptionCode.RATE_LIMIT_EXCEEDED)
                    .data("message", rateResult.getMessage());
        }

        IFileStorage storage = resolveStorage();
        return DownloadContext.builder()
                .fileNode(fileNode)
                .storage(storage)
                .build();
    }

    /**
     * 生成签名下载URL
     *
     * @param nodeId 文件节点ID
     * @param userId 用户ID
     * @param ip     客户端IP
     * @return 签名URL
     */
    public String generateSignedUrl(String nodeId, String userId, String ip) {
        FileNode fileNode = fileNodeRepository.findById(nodeId);
        if (fileNode == null || !fileNode.isFile()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }
        return rateLimitService.generateSignedDownloadUrl(
                fileNode.getStorageKey(), userId, ip);
    }

    /**
     * 通过签名URL解析下载信息
     *
     * @param sign       签名
     * @param expireTime 过期时间
     * @return 签名下载上下文（storageKey + Storage）
     */
    public SignedDownloadContext resolveSignedDownload(String sign, long expireTime) {
        String storageKey = rateLimitService.verifySignedUrl(sign, expireTime);
        if (storageKey == null) {
            throw new BusinessException(NextwikiExceptionCode.SIGN_URL_EXPIRED);
        }
        IFileStorage storage = resolveStorage();
        return SignedDownloadContext.builder()
                .storageKey(storageKey)
                .storage(storage)
                .build();
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

    /**
     * 供 Controller 直接调用获取存储实例（文件夹打包下载用）
     */
    public IFileStorage resolveStorageForDownload() {
        return resolveStorage();
    }

    /**
     * 下载上下文
     */
    @Data
    @Builder
    public static class DownloadContext {
        private FileNode fileNode;
        private IFileStorage storage;
    }

    /**
     * 签名下载上下文
     */
    @Data
    @Builder
    public static class SignedDownloadContext {
        private String storageKey;
        private IFileStorage storage;
    }
}
