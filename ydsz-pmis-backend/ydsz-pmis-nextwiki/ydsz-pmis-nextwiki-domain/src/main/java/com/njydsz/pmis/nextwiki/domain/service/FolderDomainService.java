package com.njydsz.pmis.nextwiki.domain.service;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.enums.NextwikiEnums;
import com.njydsz.pmis.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderDomainService {

    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建目录
     */
    public FileNode createFolder(String parentId, String name, String userId) {
        FileNode parent = resolveParent(parentId, userId);

        // 检查同名目录
        List<FileNode> children = fileNodeRepository.findChildren(parent.getId());
        boolean nameExists = children.stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name) && c.isFolder());
        if (nameExists) {
            throw new BusinessException("NW-FOLDER-001", "同名目录已存在: " + name);
        }

        String path = buildPath(parent.getPath(), name);
        int level = parent.getLevel() + 1;

        FileNode folder = FileNode.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
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
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedBy(userId);
        folder.setUpdatedAt(LocalDateTime.now());

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
    public FileNode move(String nodeId, String targetParentId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw new BusinessException("NW-FOLDER-002", "文件节点不存在: " + nodeId);
        }

        // 防止将目录移动到自身或其子目录下
        if (node.isFolder() && isAncestorOrSelf(nodeId, targetParentId)) {
            throw new BusinessException("NW-FOLDER-003", "不能将目录移动到自身或其子目录下");
        }

        FileNode targetParent = fileNodeRepository.findById(targetParentId);
        if (targetParent == null || !targetParent.isFolder()) {
            throw new BusinessException("NW-FOLDER-004", "目标父目录不存在或不是目录");
        }

        String oldPath = node.getPath();
        String newPath = buildPath(targetParent.getPath(), node.getName());
        int newLevel = targetParent.getLevel() + 1;

        node.setParentId(targetParentId);
        node.setPath(newPath);
        node.setLevel(newLevel);
        node.setSort(getNextSort(targetParentId));
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());
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
    public FileNode rename(String nodeId, String newName, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw new BusinessException("NW-FOLDER-002", "文件节点不存在: " + nodeId);
        }

        String oldName = node.getName();
        FileNode parent = fileNodeRepository.findById(node.getParentId());
        String newPath = parent != null ? buildPath(parent.getPath(), newName) : "/" + newName + "/";

        node.setName(newName);
        node.setPath(newPath);
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());
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
    public void softDelete(String nodeId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw new BusinessException("NW-FOLDER-002", "文件节点不存在: " + nodeId);
        }

        // 记录原始路径
        fileNodeRepository.softDelete(nodeId, node.getPath());

        // 如果是目录，递归逻辑删除子节点
        if (node.isFolder()) {
            List<FileNode> descendants = fileNodeRepository.findByPathPrefix(node.getPath());
            for (FileNode desc : descendants) {
                if (!desc.getId().equals(nodeId)) {
                    fileNodeRepository.softDelete(desc.getId(), desc.getPath());
                }
            }
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
            throw new BusinessException("NW-FOLDER-005", "父目录不存在: " + parentId);
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

    private void updateChildrenPaths(String parentId, String oldPathPrefix,
                                      String newPathPrefix, int newLevel, String userId) {
        List<FileNode> children = fileNodeRepository.findByPathPrefix(oldPathPrefix);
        for (FileNode child : children) {
            if (child.getId().equals(parentId)) {
                continue;
            }
            String childPath = child.getPath();
            if (childPath != null && childPath.startsWith(oldPathPrefix)) {
                String newPath = newPathPrefix + childPath.substring(oldPathPrefix.length());
                child.setPath(newPath);
                child.setLevel(newLevel + (child.getLevel() != null
                        ? child.getLevel() - countPathSegments(oldPathPrefix)
                        : 1));
                child.setUpdatedBy(userId);
                child.setUpdatedAt(LocalDateTime.now());
                fileNodeRepository.update(child);
            }
        }
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
        List<FileNode> children = fileNodeRepository.findChildren(parentId);
        return children.size();
    }

    /**
     * 目录统计信息
     */
    public record FolderStats(int fileCount, int folderCount, long totalSize) {
    }
}
