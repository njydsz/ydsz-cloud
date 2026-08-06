package com.remisoft.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.remisoft.common.util.id.SnowflakeIdGenerator;
import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.nextwiki.domain.entity.FileNode;
import com.remisoft.nextwiki.domain.enums.NextwikiExceptionCode;
import com.remisoft.nextwiki.domain.event.FileOperatedEvent;
import com.remisoft.nextwiki.domain.repository.FileNodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 目录树领域服务
 * <p>
 * 封装目录树操作的核心业务逻辑：创建目录、移动、重命名、递归删除、路径计算。
 * 继承 {@code common-domain} 的 {@code TreeNode} 抽象，但持久化到数据库。
 *
 * <p><b>路径规则：</b>
 * <ul>
 *   <li>根目录路径为 "/"，ID 约定为 "0"</li>
 *   <li>子目录路径为 "/父路径/目录名/"，如 "/docs/contract/"</li>
 *   <li>路径末尾始终以 "/" 结尾，便于前缀查询</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderDomainService {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建目录
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNode createFolder(String parentId, String name, String userId) {
        FileNode parent = resolveParent(parentId, userId);

        // 检查同名目录
        List<FileNode> children = fileNodeRepository.findChildren(parent.getId());
        boolean nameExists = children.stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name) && c.isFolder());
        if (nameExists) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_ALREADY_EXISTS).data("name", name);
        }

        String path = buildPath(parent.getPath(), name);
        int level = parent.getLevel() + 1;

        FileNode folder = FileNode.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
                .parentId(parent.getId())
                .name(name)
                .nodeType(FileNode.TYPE_FOLDER)
                .size(0L)
                .path(path)
                .level(level)
                .sort(getNextSort(parent.getId()))
                .currentVersion(0)
                .previewReady(false)
                .starred(false)
                .shareStatus("private")
                .status("active")
                .deleted(0)
                .revision(0)
                .build();

        folder.setCreatedBy(userId);
        folder.setUpdatedBy(userId);

        FileNode saved = fileNodeRepository.save(folder);

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_UPLOAD)
                .fileNodeId(saved.getId())
                .fileName(name)
                .nodeType(FileNode.TYPE_FOLDER)
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .build());

        log.info("[FolderDomainService] 创建目录: name={}, path={}, userId={}", name, path, userId);
        return saved;
    }

    /**
     * 移动文件/文件夹到目标目录
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNode move(String nodeId, String targetParentId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        // 防止将目录移动到自身或其子目录下
        if (node.isFolder() && isAncestorOrSelf(nodeId, targetParentId)) {
            throw new BusinessException(NextwikiExceptionCode.FILE_MOVE_TO_SELF);
        }

        FileNode targetParent = fileNodeRepository.findById(targetParentId);
        if (targetParent == null || !targetParent.isFolder()) {
            throw new BusinessException(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER);
        }

        String oldPath = node.getPath();
        String newPath = buildPath(targetParent.getPath(), node.getName());
        int newLevel = targetParent.getLevel() + 1;

        node.setParentId(targetParentId);
        node.setPath(newPath);
        node.setLevel(newLevel);
        node.setSort(getNextSort(targetParentId));
        node.setUpdatedBy(userId);
        fileNodeRepository.update(node);

        // 如果是目录，递归更新子节点路径
        if (node.isFolder()) {
            updateChildrenPaths(nodeId, oldPath, newPath, newLevel, userId);
        }

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_MOVE)
                .fileNodeId(nodeId)
                .fileName(node.getName())
                .nodeType(node.getNodeType())
                .storageKey(node.getStorageKey())
                .bucketName(node.getBucketName())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .extra(oldPath + "->" + newPath)
                .build());

        log.info("[FolderDomainService] 移动: nodeId={}, oldPath={}, newPath={}", nodeId, oldPath, newPath);
        return node;
    }

    /**
     * 重命名文件/文件夹
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNode rename(String nodeId, String newName, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        String oldName = node.getName();
        FileNode parent = fileNodeRepository.findById(node.getParentId());
        String newPath = parent != null ? buildPath(parent.getPath(), newName) : "/" + newName + "/";

        node.setName(newName);
        node.setPath(newPath);
        node.setUpdatedBy(userId);
        fileNodeRepository.update(node);

        // 如果是目录，递归更新子节点路径
        if (node.isFolder()) {
            String oldPath = buildPath(parent.getPath(), oldName);
            updateChildrenPaths(nodeId, oldPath, newPath, node.getLevel(), userId);
        }

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_RENAME)
                .fileNodeId(nodeId)
                .fileName(newName)
                .nodeType(node.getNodeType())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .extra(oldName + "->" + newName)
                .build());

        log.info("[FolderDomainService] 重命名: nodeId={}, oldName={}, newName={}", nodeId, oldName, newName);
        return node;
    }

    /**
     * 逻辑删除（移入回收站）
     */
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(String nodeId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        // 记录原始路径
        fileNodeRepository.softDelete(nodeId, node.getPath());

        // 如果是目录，批量逻辑删除子节点（避免逐个 update 的 N+1 问题）
        if (node.isFolder()) {
            int affected = fileNodeRepository.batchSoftDeleteByPathPrefix(node.getPath(), nodeId);
            log.info("[FolderDomainService] 批量逻辑删除子节点: nodeId={}, affected={}", nodeId, affected);
        }

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_DELETE)
                .fileNodeId(nodeId)
                .fileName(node.getName())
                .nodeType(node.getNodeType())
                .storageKey(node.getStorageKey())
                .bucketName(node.getBucketName())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .build());

        log.info("[FolderDomainService] 逻辑删除: nodeId={}, name={}", nodeId, node.getName());
    }

    /**
     * 列出目录子节点
     */
    public List<FileNode> listChildren(String parentId, String userId) {
        FileNode parent = resolveParent(parentId, userId);
        return fileNodeRepository.findChildren(parent.getId());
    }

    /**
     * 获取目录统计信息
     */
    public FolderStats getStats(String folderId) {
        FileNode folder = fileNodeRepository.findById(folderId);
        if (folder == null || !folder.isFolder()) {
            return new FolderStats(0, 0, 0);
        }

        List<FileNode> descendants = fileNodeRepository.findByPathPrefix(folder.getPath());
        int fileCount = 0;
        int folderCount = 0;
        long totalSize = 0;

        for (FileNode desc : descendants) {
            if (desc.getId().equals(folderId)) {
                continue;
            }
            if (desc.isFile()) {
                fileCount++;
                totalSize += desc.getSize() != null ? desc.getSize() : 0;
            } else {
                folderCount++;
            }
        }

        return new FolderStats(fileCount, folderCount, totalSize);
    }

    // ==================== 私有方法 ====================

    private FileNode resolveParent(String parentId, String userId) {
        if (parentId == null || parentId.isEmpty() || "0".equals(parentId)) {
            return fileNodeRepository.findOrCreateRoot(userId);
        }
        FileNode parent = fileNodeRepository.findById(parentId);
        if (parent == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_FOLDER_NOT_FOUND).data("parentId", parentId);
        }
        return parent;
    }

    private String buildPath(String parentPath, String name) {
        if (parentPath == null || parentPath.isEmpty()) {
            return "/" + name + "/";
        }
        if (!parentPath.endsWith("/")) {
            parentPath = parentPath + "/";
        }
        return parentPath + name + "/";
    }

    private boolean isAncestorOrSelf(String nodeId, String targetId) {
        if (nodeId.equals(targetId)) {
            return true;
        }
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            return false;
        }
        // 检查 target 是否在 node 的子树中
        FileNode target = fileNodeRepository.findById(targetId);
        if (target == null) {
            return false;
        }
        return target.getPath() != null && node.getPath() != null
                && target.getPath().startsWith(node.getPath());
    }

    @Transactional(rollbackFor = Exception.class)
    private void updateChildrenPaths(String parentId, String oldPathPrefix,
                                      String newPathPrefix, int newLevel, String userId) {
        // 批量 UPDATE 子节点路径（避免逐个 update 的 N+1 问题）
        // oldLevel = 旧前缀对应的层级深度，newLevel = 新前缀对应的层级深度
        // levelDelta = newLevel - oldLevel，应用到所有子节点
        int oldLevel = countPathSegments(oldPathPrefix);
        int levelDelta = newLevel - oldLevel;
        int affected = fileNodeRepository.batchUpdatePathPrefix(
                oldPathPrefix, newPathPrefix, levelDelta, parentId);
        log.info("[FolderDomainService] 批量更新子节点路径: parentId={}, affected={}", parentId, affected);
    }

    private int countPathSegments(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        String trimmed = path.replaceAll("^/+|/+$", "");
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("/").length;
    }

    private Integer getNextSort(String parentId) {
        // 使用 COUNT 查询避免全量加载子节点
        return fileNodeRepository.countChildren(parentId);
    }

    /**
     * 目录统计信息
     */
    public record FolderStats(int fileCount, int folderCount, long totalSize) {
    }
}
