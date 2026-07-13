package com.njydsz.pmis.nextwiki.server.service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorageProvider;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;
import com.njydsz.pmis.nextwiki.domain.entity.TrashItem;
import com.njydsz.pmis.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.pmis.nextwiki.domain.service.FolderDomainService;
import com.njydsz.pmis.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.pmis.nextwiki.domain.service.TrashDomainService;
import com.njydsz.pmis.nextwiki.domain.vo.FileNodeVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件应用服务
 * <p>
 * 编排文件上传、下载、移动、重命名、删除等操作，协调领域服务与底层存储。
 *
 * <p><b>核心流程：</b>
 * <ul>
 *   <li>上传：配额校验 → 存储上传 → 创建 FileNode（持久化）→ 创建版本 → 事件发布</li>
 *   <li>删除：逻辑删除 FileNode → 移入回收站 → 释放配额 → 删除索引</li>
 *   <li>移动/重命名：更新 FileNode → 递归更新子节点路径 → 事件通知</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileApplicationService {

    private final FolderDomainService folderDomainService;
    private final FileVersionDomainService versionDomainService;
    private final QuotaDomainService quotaDomainService;
    private final TrashDomainService trashDomainService;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 上传文件
     */
    public FileNodeVO upload(MultipartFile file, String parentId, String rename,
                              String versionRemark, String userId) {
        // 1. 配额校验
        quotaDomainService.checkQuota("user", userId, file.getSize());

        // 2. 获取存储实例
        IFileStorage storage = resolveStorage();
        if (storage == null) {
            throw BusinessException.builder().key("文件存储未配置").build();
        }

        // 3. 生成存储键
        String originalFilename = file.getOriginalFilename();
        String suffix = extractSuffix(originalFilename);
        String storageKey = generateStorageKey(userId, originalFilename);

        // 4. 上传到存储
        FileStorage uploaded = storage.upload(null, storageKey, file);

        // 5. 计算 SHA-256
        String fileHash = null;
        try (InputStream is = storage.downloadAsStream(null, storageKey)) {
            fileHash = calculateSha256(is);
        } catch (Exception e) {
            log.warn("[FileApplicationService] SHA-256 计算失败: {}", e.getMessage());
        }

        // 6. 解析父目录
        FileNode parent = folderDomainService.listChildren(parentId, userId)
                .isEmpty() ? null : fileNodeRepository.findById(parentId);
        String resolvedParentId = parent != null ? parent.getId() : "0";
        String path = parent != null
                ? (parent.getPath() != null ? parent.getPath() : "/") + (rename != null && !rename.isEmpty() ? rename : originalFilename) + "/"
                : "/" + (rename != null && !rename.isEmpty() ? rename : originalFilename) + "/";
        int level = parent != null && parent.getLevel() != null ? parent.getLevel() + 1 : 1;

        // 7. 创建文件节点并持久化
        String fileName = rename != null && !rename.isEmpty() ? rename : originalFilename;
        FileNode fileNode = buildFileNode(resolvedParentId, fileName, suffix, uploaded,
                storageKey, fileHash, path, level, userId);
        FileNode saved = fileNodeRepository.save(fileNode);

        // 8. 创建版本记录
        versionDomainService.createVersion(saved.getId(), storageKey, file.getSize(),
                fileHash, uploaded.getMimeType(), versionRemark, userId);

        // 9. 增加配额用量
        quotaDomainService.addUsage("user", userId, file.getSize(), 1);

        // 10. 发布上传事件
        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_UPLOAD)
                .fileNodeId(saved.getId())
                .fileName(fileName)
                .nodeType(FileNode.TYPE_FILE)
                .storageKey(storageKey)
                .bucketName(uploaded.getUuidName())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .build());

        log.info("[FileApplicationService] 文件上传成功: name={}, size={}, userId={}",
                fileName, file.getSize(), userId);

        return toVO(saved);
    }

    /**
     * 创建目录
     */
    public FileNodeVO createFolder(String parentId, String name, String userId) {
        FileNode folder = folderDomainService.createFolder(parentId, name, userId);
        return toVO(folder);
    }

    /**
     * 列出目录
     */
    public List<FileNodeVO> listFiles(String parentId, String userId) {
        List<FileNode> nodes = folderDomainService.listChildren(parentId, userId);
        return nodes.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 移动文件
     */
    public FileNodeVO move(String nodeId, String targetParentId, String userId) {
        FileNode node = folderDomainService.move(nodeId, targetParentId, userId);
        return toVO(node);
    }

    /**
     * 重命名
     */
    public FileNodeVO rename(String nodeId, String newName, String userId) {
        FileNode node = folderDomainService.rename(nodeId, newName, userId);
        return toVO(node);
    }

    /**
     * 删除（移入回收站）
     */
    public void delete(String nodeId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.builder().key("文件节点不存在: " + nodeId).build();
        }

        // 逻辑删除 FileNode
        folderDomainService.softDelete(nodeId, userId);

        // 移入回收站
        trashDomainService.moveToTrash(node, userId);

        // 释放配额
        if (node.isFile() && node.getSize() != null) {
            quotaDomainService.subtractUsage("user", userId, node.getSize(), 1);
        }

        log.info("[FileApplicationService] 删除文件: nodeId={}, name={}", nodeId, node.getName());
    }

    /**
     * 版本回滚
     */
    public FileNodeVO rollbackVersion(String nodeId, Integer targetVersion, String userId) {
        versionDomainService.rollback(nodeId, targetVersion, userId);
        FileNode updated = fileNodeRepository.findById(nodeId);
        return toVO(updated);
    }

    /**
     * 获取版本历史
     */
    public List<FileVersion> getVersionHistory(String nodeId) {
        return versionDomainService.getVersionHistory(nodeId);
    }

    /**
     * 获取文件详情
     */
    public FileNodeVO getFileInfo(String nodeId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.builder().key("文件节点不存在: " + nodeId).build();
        }
        return toVO(node);
    }

    /**
     * 星标/取消星标
     */
    public void toggleStar(String nodeId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.builder().key("文件节点不存在: " + nodeId).build();
        }
        node.setStarred(node.getStarred() == null || !node.getStarred());
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(node);
        log.info("[FileApplicationService] 切换星标: nodeId={}, starred={}, userId={}",
                nodeId, node.getStarred(), userId);
    }

    // ==================== 私有方法 ====================

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

    private FileNode buildFileNode(String parentId, String name, String suffix,
                                     FileStorage uploaded, String storageKey,
                                     String fileHash, String path, int level,
                                     String userId) {
        FileNode node = FileNode.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .parentId(parentId)
                .name(name)
                .nodeType(FileNode.TYPE_FILE)
                .suffix(suffix)
                .size(uploaded.getSize())
                .storageKey(storageKey)
                .bucketName(uploaded.getUuidName())
                .mimeType(uploaded.getMimeType())
                .path(path)
                .level(level)
                .sort(0)
                .currentVersion(0)
                .fileHash(fileHash)
                .previewReady(false)
                .starred(false)
                .shareStatus("private")
                .status("active")
                .deleted(0)
                .revision(0)
                .build();

        node.setCreatedBy(userId);
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());

        return node;
    }

    private String generateStorageKey(String userId, String originalFilename) {
        String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String suffix = extractSuffix(originalFilename);
        return "wiki/" + userId + "/" + datePath + "/" + uuid + (suffix.isEmpty() ? "" : "." + suffix);
    }

    private String extractSuffix(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String calculateSha256(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, len);
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private FileNodeVO toVO(FileNode node) {
        return FileNodeVO.builder()
                .id(node.getId())
                .parentId(node.getParentId())
                .name(node.getName())
                .nodeType(node.getNodeType())
                .suffix(node.getSuffix())
                .size(node.getSize())
                .mimeType(node.getMimeType())
                .level(node.getLevel())
                .sort(node.getSort())
                .currentVersion(node.getCurrentVersion())
                .starred(node.getStarred())
                .shareStatus(node.getShareStatus())
                .previewReady(node.getPreviewReady())
                .createdBy(node.getCreatedBy())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }
}
