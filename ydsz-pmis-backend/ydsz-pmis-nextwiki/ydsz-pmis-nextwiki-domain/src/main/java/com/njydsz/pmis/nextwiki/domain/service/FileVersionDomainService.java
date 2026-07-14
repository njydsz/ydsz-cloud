package com.njydsz.pmis.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;
import com.njydsz.pmis.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.pmis.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.repository.FileVersionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件版本领域服务
 * <p>
 * 管理文件版本生命周期：创建版本、版本回滚、版本历史查询、超限清理。
 *
 * <p><b>版本保留策略：</b>
 * <ul>
 *   <li>默认保留最近 {@value #MAX_VERSIONS} 个版本</li>
 *   <li>超过限制时自动清理最旧版本</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileVersionDomainService {

    private final FileVersionRepository versionRepository;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 最大保留版本数 */
    private static final int MAX_VERSIONS = 20;

    /**
     * 创建新版本（文件上传/更新时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileVersion createVersion(String fileNodeId, String storageKey, Long size,
                                      String fileHash, String mimeType, String remark,
                                      String userId) {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null || !fileNode.isFile()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("fileNodeId", fileNodeId);
        }

        // 取当前最大版本号
        List<FileVersion> versions = versionRepository.findByFileNodeId(fileNodeId);
        int nextVersion = versions.stream()
                .mapToInt(v -> v.getVersionNumber() != null ? v.getVersionNumber() : 0)
                .max()
                .orElse(0) + 1;

        // 将旧版本标记为非活跃
        if (!versions.isEmpty()) {
            versionRepository.setActiveVersion(fileNodeId, -1);
        }

        FileVersion version = FileVersion.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .fileNodeId(fileNodeId)
                .versionNumber(nextVersion)
                .storageKey(storageKey)
                .size(size)
                .fileHash(fileHash)
                .mimeType(mimeType)
                .remark(remark)
                .changeType(nextVersion == 1 ? "create" : "update")
                .isActive(true)
                .revision(0)
                .deleted(0)
                .build();

        version.setCreatedBy(userId);
        version.setCreatedAt(LocalDateTime.now());
        version.setUpdatedBy(userId);
        version.setUpdatedAt(LocalDateTime.now());

        FileVersion saved = versionRepository.save(version);

        // 更新文件节点的当前版本信息
        fileNode.setCurrentVersion(nextVersion);
        fileNode.setStorageKey(storageKey);
        fileNode.setSize(size);
        fileNode.setFileHash(fileHash);
        fileNode.setMimeType(mimeType);
        fileNode.setUpdatedBy(userId);
        fileNode.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(fileNode);

        // 清理超限版本
        cleanupExcessVersions(fileNodeId);

        log.info("[FileVersionDomainService] 创建版本: fileNodeId={}, version={}", fileNodeId, nextVersion);
        return saved;
    }

    /**
     * 回滚到指定版本
     */
    @Transactional(rollbackFor = Exception.class)
    public FileVersion rollback(String fileNodeId, Integer targetVersion, String userId) {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null || !fileNode.isFile()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("fileNodeId", fileNodeId);
        }

        FileVersion target = versionRepository.findByFileNodeIdAndVersion(fileNodeId, targetVersion);
        if (target == null) {
            throw BusinessException.of(NextwikiExceptionCode.VERSION_NOT_FOUND).data("targetVersion", targetVersion);
        }

        // 将当前活跃版本标记为非活跃
        versionRepository.setActiveVersion(fileNodeId, -1);

        // 创建回滚版本（基于目标版本的数据）
        List<FileVersion> versions = versionRepository.findByFileNodeId(fileNodeId);
        int nextVersion = versions.stream()
                .mapToInt(v -> v.getVersionNumber() != null ? v.getVersionNumber() : 0)
                .max()
                .orElse(0) + 1;

        FileVersion rollbackVersion = FileVersion.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .fileNodeId(fileNodeId)
                .versionNumber(nextVersion)
                .storageKey(target.getStorageKey())
                .size(target.getSize())
                .fileHash(target.getFileHash())
                .mimeType(target.getMimeType())
                .remark("回滚到版本 " + targetVersion)
                .changeType("rollback")
                .isActive(true)
                .revision(0)
                .deleted(0)
                .build();

        rollbackVersion.setCreatedBy(userId);
        rollbackVersion.setCreatedAt(LocalDateTime.now());
        rollbackVersion.setUpdatedBy(userId);
        rollbackVersion.setUpdatedAt(LocalDateTime.now());

        FileVersion saved = versionRepository.save(rollbackVersion);

        // 更新文件节点
        fileNode.setCurrentVersion(nextVersion);
        fileNode.setStorageKey(target.getStorageKey());
        fileNode.setSize(target.getSize());
        fileNode.setFileHash(target.getFileHash());
        fileNode.setMimeType(target.getMimeType());
        fileNode.setUpdatedBy(userId);
        fileNode.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(fileNode);

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_VERSION_ROLLBACK)
                .fileNodeId(fileNodeId)
                .fileName(fileNode.getName())
                .nodeType(fileNode.getNodeType())
                .storageKey(target.getStorageKey())
                .bucketName(fileNode.getBucketName())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .extra("rollback to v" + targetVersion)
                .build());

        log.info("[FileVersionDomainService] 版本回滚: fileNodeId={}, targetVersion={}, newVersion={}",
                fileNodeId, targetVersion, nextVersion);
        return saved;
    }

    /**
     * 查询版本历史
     */
    public List<FileVersion> getVersionHistory(String fileNodeId) {
        return versionRepository.findByFileNodeId(fileNodeId);
    }

    /**
     * 获取当前活跃版本
     */
    public FileVersion getActiveVersion(String fileNodeId) {
        return versionRepository.findActiveVersion(fileNodeId);
    }

    // ==================== 私有方法 ====================

    /**
     * 清理超出保留数量的旧版本
     */
    @Transactional(rollbackFor = Exception.class)
    private void cleanupExcessVersions(String fileNodeId) {
        int count = versionRepository.countByFileNodeId(fileNodeId);
        if (count <= MAX_VERSIONS) {
            return;
        }

        int toDelete = count - MAX_VERSIONS;
        List<FileVersion> oldest = versionRepository.findOldestVersions(fileNodeId, toDelete);
        for (FileVersion v : oldest) {
            versionRepository.deleteById(v.getId());
            log.info("[FileVersionDomainService] 清理旧版本: fileNodeId={}, version={}",
                    fileNodeId, v.getVersionNumber());
        }
    }
}
