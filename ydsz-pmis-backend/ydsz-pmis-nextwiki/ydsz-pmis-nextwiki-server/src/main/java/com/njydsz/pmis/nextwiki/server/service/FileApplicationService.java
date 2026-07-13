package com.njydsz.pmis.nextwiki.server.service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorage;
import com.njydsz.pmis.common.file.storage.IFileStorageProvider;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;
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
 *   <li>上传：安全校验 → 配额校验 → 秒传去重 → 存储上传 → 创建 FileNode → 创建版本 → 事件发布</li>
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

    @Value("${nextwiki.upload.max-file-size:524288000}")
    private long maxFileSize;

    @Value("${nextwiki.upload.allowed-types:}")
    private String allowedTypes;

    /** 禁止上传的文件扩展名（安全黑名单） */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "com", "msi", "dll", "scr", "vbs", "jar", "war"
    );

    /**
     * 上传文件（支持秒传去重）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO upload(MultipartFile file, String parentId, String rename,
                              String versionRemark, String userId) {
        // 1. 安全校验
        validateUpload(file);

        // 2. 配额校验
        quotaDomainService.checkQuota("user", userId, file.getSize());

        // 3. 解析父目录（直接查找，不再用 listChildren 判断）
        String originalFilename = file.getOriginalFilename();
        String fileName = (rename != null && !rename.isEmpty()) ? rename : originalFilename;
        String suffix = extractSuffix(fileName);

        FileNode parent = resolveParentNode(parentId, userId);
        String resolvedParentId = parent.getId();
        String parentPath = parent.getPath() != null ? parent.getPath() : "/";
        String path = parentPath.endsWith("/")
                ? parentPath + fileName + "/"
                : parentPath + "/" + fileName + "/";
        int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

        // 4. 计算文件哈希（用于秒传去重）
        String fileHash = null;
        try {
            fileHash = calculateSha256(file.getInputStream());
        } catch (Exception e) {
            log.warn("[FileApplicationService] SHA-256 计算失败: {}", e.getMessage());
        }

        // 5. 秒传去重：如果已存在相同哈希的文件，直接创建引用
        if (fileHash != null) {
            FileNode existing = fileNodeRepository.findByFileHash(fileHash);
            if (existing != null) {
                log.info("[FileApplicationService] 秒传命中: hash={}, existingNodeId={}",
                        fileHash, existing.getId());
                FileNode dedupedNode = buildDedupedFileNode(resolvedParentId, fileName, suffix,
                        existing, fileHash, path, level, userId);
                FileNode saved = fileNodeRepository.save(dedupedNode);
                versionDomainService.createVersion(saved.getId(), existing.getStorageKey(),
                        existing.getSize(), fileHash, existing.getMimeType(), "秒传", userId);
                quotaDomainService.addUsage("user", userId, existing.getSize(), 1);
                publishUploadEvent(saved, fileName, userId);
                return toVO(saved);
            }
        }

        // 6. 正常上传到存储
        IFileStorage storage = resolveStorage();
        if (storage == null) {
            throw BusinessException.builder().key("文件存储未配置").build();
        }
        String storageKey = generateStorageKey(userId, fileName);
        FileStorage uploaded = storage.upload(null, storageKey, file);

        // 7. 创建文件节点并持久化
        FileNode fileNode = buildFileNode(resolvedParentId, fileName, suffix, uploaded,
                storageKey, fileHash, path, level, userId);
        FileNode saved = fileNodeRepository.save(fileNode);

        // 8. 创建版本记录
        versionDomainService.createVersion(saved.getId(), storageKey, file.getSize(),
                fileHash, uploaded.getMimeType(), versionRemark, userId);

        // 9. 增加配额用量
        quotaDomainService.addUsage("user", userId, file.getSize(), 1);

        // 10. 发布上传事件
        publishUploadEvent(saved, fileName, userId);

        log.info("[FileApplicationService] 文件上传成功: name={}, size={}, userId={}",
                fileName, file.getSize(), userId);

        return toVO(saved);
    }

    /**
     * 创建目录
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO createFolder(String parentId, String name, String userId) {
        FileNode folder = folderDomainService.createFolder(parentId, name, userId);
        return toVO(folder);
    }

    /**
     * 列出目录（支持排序、过滤、分页）
     *
     * @param parentId 父目录ID
     * @param userId   用户ID
     * @param sortBy   排序字段：name / size / time（默认 time）
     * @param sortDir  排序方向：asc / desc（默认 desc）
     * @param type     过滤类型：all / file / folder（默认 all）
     * @param page     页码（从 1 开始，默认 1）
     * @param pageSize 每页大小（默认 50）
     */
    public List<FileNodeVO> listFiles(String parentId, String userId,
                                        String sortBy, String sortDir,
                                        String type, int page, int pageSize) {
        List<FileNode> nodes = folderDomainService.listChildren(parentId, userId);

        // 类型过滤
        if (type != null && !type.isEmpty() && !"all".equals(type)) {
            nodes = nodes.stream()
                    .filter(n -> {
                        if ("file".equals(type)) return n.isFile();
                        if ("folder".equals(type)) return n.isFolder();
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        // 排序
        Comparator<FileNode> comparator = buildComparator(sortBy);
        if ("asc".equalsIgnoreCase(sortDir)) {
            // asc 方向不需要反转
        } else {
            comparator = comparator.reversed();
        }
        nodes.sort(comparator);

        // 分页
        int total = nodes.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        if (fromIndex >= total) {
            return List.of();
        }

        return nodes.subList(fromIndex, toIndex).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 移动文件
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO move(String nodeId, String targetParentId, String userId) {
        FileNode node = folderDomainService.move(nodeId, targetParentId, userId);
        return toVO(node);
    }

    /**
     * 重命名
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO rename(String nodeId, String newName, String userId) {
        FileNode node = folderDomainService.rename(nodeId, newName, userId);
        return toVO(node);
    }

    /**
     * 删除（移入回收站）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String nodeId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.builder().key("文件节点不存在: " + nodeId).build();
        }

        folderDomainService.softDelete(nodeId, userId);
        trashDomainService.moveToTrash(node, userId);

        if (node.isFile() && node.getSize() != null) {
            quotaDomainService.subtractUsage("user", userId, node.getSize(), 1);
        }

        log.info("[FileApplicationService] 删除文件: nodeId={}, name={}", nodeId, node.getName());
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<String> nodeIds, String userId) {
        int success = 0;
        for (String nodeId : nodeIds) {
            try {
                delete(nodeId, userId);
                success++;
            } catch (Exception e) {
                log.error("[FileApplicationService] 批量删除失败: nodeId={}", nodeId, e);
            }
        }
        log.info("[FileApplicationService] 批量删除: total={}, success={}", nodeIds.size(), success);
        return success;
    }

    /**
     * 批量移动
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchMove(List<String> nodeIds, String targetParentId, String userId) {
        int success = 0;
        for (String nodeId : nodeIds) {
            try {
                move(nodeId, targetParentId, userId);
                success++;
            } catch (Exception e) {
                log.error("[FileApplicationService] 批量移动失败: nodeId={}", nodeId, e);
            }
        }
        log.info("[FileApplicationService] 批量移动: total={}, success={}", nodeIds.size(), success);
        return success;
    }

    /**
     * 复制文件（创建副本，引用同一存储对象）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO copy(String nodeId, String targetParentId, String userId) {
        FileNode source = fileNodeRepository.findById(nodeId);
        if (source == null) {
            throw BusinessException.builder().key("文件节点不存在: " + nodeId).build();
        }

        FileNode parent = resolveParentNode(targetParentId, userId);
        String resolvedParentId = parent.getId();
        String parentPath = parent.getPath() != null ? parent.getPath() : "/";
        String newName = source.getName();
        String path = parentPath.endsWith("/")
                ? parentPath + newName + "/"
                : parentPath + "/" + newName + "/";
        int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

        // 配额校验（仅文件需要）
        if (source.isFile() && source.getSize() != null) {
            quotaDomainService.checkQuota("user", userId, source.getSize());
        }

        FileNode copyNode = FileNode.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .parentId(resolvedParentId)
                .name(newName)
                .nodeType(source.getNodeType())
                .suffix(source.getSuffix())
                .size(source.getSize())
                .storageKey(source.getStorageKey())
                .bucketName(source.getBucketName())
                .mimeType(source.getMimeType())
                .path(path)
                .level(level)
                .sort(fileNodeRepository.findChildren(resolvedParentId).size())
                .currentVersion(source.getCurrentVersion())
                .fileHash(source.getFileHash())
                .thumbnailKey(source.getThumbnailKey())
                .previewReady(source.getPreviewReady())
                .starred(false)
                .shareStatus("private")
                .status("active")
                .deleted(0)
                .revision(0)
                .build();

        copyNode.setCreatedBy(userId);
        copyNode.setCreatedAt(LocalDateTime.now());
        copyNode.setUpdatedBy(userId);
        copyNode.setUpdatedAt(LocalDateTime.now());

        FileNode saved = fileNodeRepository.save(copyNode);

        // 文件创建版本引用
        if (source.isFile()) {
            versionDomainService.createVersion(saved.getId(), source.getStorageKey(),
                    source.getSize(), source.getFileHash(), source.getMimeType(), "复制", userId);
            if (source.getSize() != null) {
                quotaDomainService.addUsage("user", userId, source.getSize(), 1);
            }
        }

        publishUploadEvent(saved, newName, userId);
        log.info("[FileApplicationService] 复制文件: sourceId={}, targetId={}", nodeId, saved.getId());
        return toVO(saved);
    }

    /**
     * 版本回滚
     */
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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

    /**
     * 上传安全校验：文件大小 + 扩展名黑名单
     */
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.builder().key("上传文件为空").build();
        }
        if (file.getSize() > maxFileSize) {
            throw BusinessException.builder().key(
                    "文件大小超过限制: " + maxFileSize / 1024 / 1024 + "MB").build();
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw BusinessException.builder().key("文件名为空").build();
        }
        String suffix = extractSuffix(filename);
        if (BLOCKED_EXTENSIONS.contains(suffix)) {
            throw BusinessException.builder().key("不允许上传此类型文件: ." + suffix).build();
        }
        // 白名单校验（如果配置了）
        if (allowedTypes != null && !allowedTypes.isEmpty()) {
            Set<String> allowed = Set.of(allowedTypes.toLowerCase().split(","));
            if (!allowed.contains(suffix)) {
                throw BusinessException.builder().key(
                        "文件类型不在允许列表: ." + suffix).build();
            }
        }
    }

    /**
     * 解析父目录节点（直接 findById，不再通过 listChildren 间接判断）
     */
    private FileNode resolveParentNode(String parentId, String userId) {
        if (parentId == null || parentId.isEmpty() || "0".equals(parentId)) {
            return fileNodeRepository.findOrCreateRoot(userId);
        }
        FileNode parent = fileNodeRepository.findById(parentId);
        if (parent == null) {
            throw BusinessException.builder().key("父目录不存在: " + parentId).build();
        }
        if (!parent.isFolder()) {
            throw BusinessException.builder().key("目标节点不是目录: " + parentId).build();
        }
        return parent;
    }

    private void publishUploadEvent(FileNode saved, String fileName, String userId) {
        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_UPLOAD)
                .fileNodeId(saved.getId())
                .fileName(fileName)
                .nodeType(saved.getNodeType())
                .storageKey(saved.getStorageKey())
                .bucketName(saved.getBucketName())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .build());
    }

    private Comparator<FileNode> buildComparator(String sortBy) {
        if (sortBy == null) sortBy = "time";
        return switch (sortBy) {
            case "name" -> Comparator.comparing(n -> n.getName() != null ? n.getName() : "",
                    String.CASE_INSENSITIVE_ORDER);
            case "size" -> Comparator.comparing(n -> n.getSize() != null ? n.getSize() : 0L);
            default -> Comparator.comparing(n -> n.getUpdatedAt() != null
                    ? n.getUpdatedAt() : LocalDateTime.MIN);
        };
    }

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

    /**
     * 构建秒传去重的文件节点（引用已有存储对象，跳过上传）
     */
    private FileNode buildDedupedFileNode(String parentId, String name, String suffix,
                                            FileNode existing, String fileHash,
                                            String path, int level, String userId) {
        FileNode node = FileNode.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .parentId(parentId)
                .name(name)
                .nodeType(FileNode.TYPE_FILE)
                .suffix(suffix)
                .size(existing.getSize())
                .storageKey(existing.getStorageKey())
                .bucketName(existing.getBucketName())
                .mimeType(existing.getMimeType())
                .path(path)
                .level(level)
                .sort(0)
                .currentVersion(0)
                .fileHash(fileHash)
                .thumbnailKey(existing.getThumbnailKey())
                .previewReady(existing.getPreviewReady())
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
                .thumbnailUrl(node.getThumbnailKey())
                .createdBy(node.getCreatedBy())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }
}
