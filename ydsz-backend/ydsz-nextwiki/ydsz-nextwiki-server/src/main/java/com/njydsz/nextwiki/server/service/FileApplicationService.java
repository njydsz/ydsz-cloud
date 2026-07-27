package com.njydsz.nextwiki.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.nextwiki.domain.service.FolderDomainService;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.service.TrashDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

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
 * @author ydsz-team
 * @since 1.0.0
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
    private final FilePermissionService permissionService;
    private final LockStrategy lockStrategy;
    private final VirusScanApplicationService virusScanApplicationService;

    private static final String LOCK_PREFIX = "nextwiki:lock:folder:";
    private static final long LOCK_LEASE_MS = 30_000L;
    private static final long LOCK_WAIT_MS = 3_000L;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    @Value("${nextwiki.upload.max-file-size:524288000}")
    private long maxFileSize;

    @Value("${nextwiki.upload.allowed-types:}")
    private String allowedTypes;

    /** 同名冲突策略：OVERWRITE(覆盖) / KEEP_BOTH(保留两者) / SKIP(跳过) */
    @Value("${nextwiki.upload.conflict-strategy:KEEP_BOTH}")
    private String conflictStrategy;

    /** 是否启用病毒扫描 */
    @Value("${nextwiki.virus-scan.enabled:false}")
    private boolean virusScanEnabled;

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
        String rawName = (rename != null && !rename.isEmpty()) ? rename : originalFilename;
        String fileName = sanitizeFileName(rawName);
        String suffix = extractSuffix(fileName);

        FileNode parent = resolveParentNode(parentId, userId);
        String resolvedParentId = parent.getId();
        String parentPath = parent.getPath() != null ? parent.getPath() : "/";

        // P0-2: 同名冲突处理策略
        List<FileNode> existingNodes = fileNodeRepository.findByNameAndParent(
                fileName, resolvedParentId, userId);
        if (existingNodes != null && !existingNodes.isEmpty()) {
            String strategy = conflictStrategy != null ? conflictStrategy.toUpperCase() : "KEEP_BOTH";
            switch (strategy) {
                case "SKIP" -> {
                    log.info("[FileApplicationService] 同名文件已存在，跳过上传: name={}", fileName);
                    return toVO(existingNodes.get(0));
                }
                case "OVERWRITE" -> {
                    FileNode existing = existingNodes.get(0);
                    log.info("[FileApplicationService] 同名文件已存在，覆盖: name={}, existingId={}",
                            fileName, existing.getId());
                    if (existing.getStorageKey() != null) {
                        IFileStorage storage = resolveStorage();
                        if (storage != null) {
                            storage.delete(existing.getBucketName(), existing.getStorageKey());
                        }
                    }
                    if (existing.getSize() != null) {
                        quotaDomainService.subtractUsage("user", userId, existing.getSize(), 1);
                    }
                    fileNodeRepository.physicalDelete(existing.getId());
                }
                default -> {
                    fileName = resolveUniqueName(fileName, resolvedParentId, userId);
                }
            }
        }

        String path = parentPath.endsWith("/")
                ? parentPath + fileName + "/"
                : parentPath + "/" + fileName + "/";
        int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

        // 4. 计算文件哈希（用于秒传去重）—— try-with-resources 确保 InputStream 关闭
        String fileHash = null;
        try (InputStream hashStream = file.getInputStream()) {
            fileHash = calculateSha256(hashStream);
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
            throw new BusinessException(NextwikiExceptionCode.FILE_STORAGE_NOT_CONFIGURED);
        }
        String storageKey = generateStorageKey(userId, fileName);
        FileStorage uploaded = storage.upload(null, storageKey, file);

        // 病毒扫描（如果启用）—— try-with-resources 确保 InputStream 关闭
        if (virusScanEnabled) {
            try (InputStream scanStream = file.getInputStream()) {
                var scanResult = virusScanApplicationService.scan(
                        scanStream, file.getSize());
                if (scanResult.isInfected()) {
                    storage.delete(null, storageKey);
                    throw BusinessException.of(NextwikiExceptionCode.FILE_VIRUS_DETECTED)
                            .data("message", scanResult.getMessage());
                }
                if (scanResult.isError()) {
                    log.warn("[FileApplicationService] 病毒扫描出错，跳过: {}", scanResult.getMessage());
                }
            } catch (IOException e) {
                log.warn("[FileApplicationService] 病毒扫描失败，跳过: {}", e.getMessage());
            }
        }

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
        String sanitizedName = sanitizeFileName(name);
        FileNode folder = folderDomainService.createFolder(parentId, sanitizedName, userId);
        return toVO(folder);
    }

    /**
     * 列出目录（支持排序、过滤、分页，使用数据库分页避免全量加载）
     *
     * @param parentId 父目录ID
     * @param userId   用户ID
     * @param sortBy   排序字段：name / size / time（默认 time）
     * @param sortDir  排序方向：asc / desc（默认 desc）
     * @param type     过滤类型：all / file / folder（默认 all）
     * @param page     页码（从 1 开始，默认 1）
     * @param pageSize 每页大小（默认 50）
     * @return 分页结果（含 total/pageCount）
     */
    public PageResult<FileNodeVO> listFiles(String parentId, String userId,
                                              String sortBy, String sortDir,
                                              String type, int page, int pageSize) {
        // 解析父目录ID（与原 listChildren 保持一致：根目录自动解析）
        FileNode parent = resolveParentNode(parentId, userId);
        String resolvedParentId = parent.getId();

        // 数据库分页查询（含类型过滤与排序）
        PageResult<FileNode> pageResult = fileNodeRepository.findPageChildren(
                resolvedParentId, type, normalizeSortBy(sortBy), normalizeSortDir(sortDir),
                page, pageSize);

        // DO → VO 转换
        return pageResult.convert(this::toVO);
    }

    /**
     * 移动文件
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO move(String nodeId, String targetParentId, String userId) {
        permissionService.checkWrite(nodeId, userId);
        String lockKey = LOCK_PREFIX + nodeId;
        DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue = acquireLock(locker, lockKey);
        try {
            FileNode node = folderDomainService.move(nodeId, targetParentId, userId);
            return toVO(node);
        } finally {
            locker.unlock(lockKey, lockValue);
        }
    }

    /**
     * 重命名
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO rename(String nodeId, String newName, String userId) {
        permissionService.checkWrite(nodeId, userId);
        String lockKey = LOCK_PREFIX + nodeId;
        DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue = acquireLock(locker, lockKey);
        try {
            FileNode node = folderDomainService.rename(nodeId, newName, userId);
            return toVO(node);
        } finally {
            locker.unlock(lockKey, lockValue);
        }
    }

    /**
     * 删除（移入回收站）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String nodeId, String userId) {
        permissionService.checkDelete(nodeId, userId);
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        String lockKey = LOCK_PREFIX + nodeId;
        DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue = acquireLock(locker, lockKey);
        try {
            folderDomainService.softDelete(nodeId, userId);
            trashDomainService.moveToTrash(node, userId);

            if (node.isFile() && node.getSize() != null) {
                quotaDomainService.subtractUsage("user", userId, node.getSize(), 1);
            }
        } finally {
            locker.unlock(lockKey, lockValue);
        }

        log.info("[FileApplicationService] 删除文件: nodeId={}, name={}", nodeId, node.getName());
    }

    /**
     * 批量删除（允许部分成功，不使用整体事务）
     */
    public BatchResult batchDelete(List<String> nodeIds, String userId) {
        int success = 0;
        List<BatchResult.FailedItem> failedItems = new ArrayList<>();
        for (String nodeId : nodeIds) {
            try {
                delete(nodeId, userId);
                success++;
            } catch (Exception e) {
                log.error("[FileApplicationService] 批量删除失败: nodeId={}", nodeId, e);
                failedItems.add(new BatchResult.FailedItem(nodeId, e.getMessage()));
            }
        }
        log.info("[FileApplicationService] 批量删除: total={}, success={}", nodeIds.size(), success);
        return new BatchResult(success, failedItems);
    }

    /**
     * 批量移动（允许部分成功，不使用整体事务）
     */
    public BatchResult batchMove(List<String> nodeIds, String targetParentId, String userId) {
        int success = 0;
        List<BatchResult.FailedItem> failedItems = new ArrayList<>();
        for (String nodeId : nodeIds) {
            try {
                move(nodeId, targetParentId, userId);
                success++;
            } catch (Exception e) {
                log.error("[FileApplicationService] 批量移动失败: nodeId={}", nodeId, e);
                failedItems.add(new BatchResult.FailedItem(nodeId, e.getMessage()));
            }
        }
        log.info("[FileApplicationService] 批量移动: total={}, success={}", nodeIds.size(), success);
        return new BatchResult(success, failedItems);
    }

    /**
     * 复制文件（创建副本，引用同一存储对象）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO copy(String nodeId, String targetParentId, String userId) {
        permissionService.checkRead(nodeId, userId);
        FileNode source = fileNodeRepository.findById(nodeId);
        if (source == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
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
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
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
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
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
            throw new BusinessException(NextwikiExceptionCode.FILE_UPLOAD_EMPTY);
        }
        if (file.getSize() > maxFileSize) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_TOO_LARGE)
                    .data("maxSize", maxFileSize / 1024 / 1024 + "MB");
        }
        String filename = sanitizeFileName(file.getOriginalFilename());
        if (filename == null || filename.isEmpty()) {
            throw new BusinessException(NextwikiExceptionCode.FILE_NAME_EMPTY);
        }
        String suffix = extractSuffix(filename);
        if (BLOCKED_EXTENSIONS.contains(suffix)) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_TYPE_NOT_ALLOWED)
                    .data("suffix", suffix);
        }
        // 白名单校验（如果配置了）
        if (allowedTypes != null && !allowedTypes.isEmpty()) {
            Set<String> allowed = Set.of(allowedTypes.toLowerCase().split(","));
            if (!allowed.contains(suffix)) {
                throw BusinessException.of(NextwikiExceptionCode.FILE_TYPE_NOT_ALLOWED)
                        .data("suffix", suffix);
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
            throw BusinessException.of(NextwikiExceptionCode.FILE_FOLDER_NOT_FOUND).data("parentId", parentId);
        }
        if (!parent.isFolder()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER).data("parentId", parentId);
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

    /**
     * 规范化排序字段：name / size / time（默认 time）
     */
    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "time";
        }
        return switch (sortBy) {
            case "name", "size", "time" -> sortBy;
            default -> "time";
        };
    }

    /**
     * 规范化排序方向：asc / desc（默认 desc）
     */
    private String normalizeSortDir(String sortDir) {
        if ("asc".equalsIgnoreCase(sortDir)) {
            return "asc";
        }
        return "desc";
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

    /**
     * 生成唯一文件名（用于 KEEP_BOTH 策略）
     */
    private String resolveUniqueName(String fileName, String parentId, String userId) {
        String baseName = fileName;
        String suffix = extractSuffix(fileName);
        if (!suffix.isEmpty()) {
            baseName = fileName.substring(0, fileName.length() - suffix.length() - 1);
        }
        int counter = 1;
        String candidate = fileName;
        while (true) {
            List<FileNode> existing = fileNodeRepository.findByNameAndParent(
                    candidate, parentId, userId);
            if (existing == null || existing.isEmpty()) {
                return candidate;
            }
            candidate = suffix.isEmpty()
                    ? baseName + " (" + counter + ")"
                    : baseName + " (" + counter + ")." + suffix;
            counter++;
        }
    }

    private String generateStorageKey(String userId, String originalFilename) {
        String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String suffix = extractSuffix(originalFilename);
        return "wiki/" + userId + "/" + datePath + "/" + uuid + (suffix.isEmpty() ? "" : "." + suffix);
    }

    /**
     * 净化文件名：去除路径穿越字符、特殊字符、超长名称
     */
    private String sanitizeFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        // 仅取文件名部分（去除路径分隔符）
        String name = filename;
        // 统一替换正反斜杠为下划线，防止路径穿越
        name = name.replace("/", "_").replace("\\", "_");
        // 去除 ../ 和 ..\
        name = name.replace("..", "_");
        // 去除特殊字符（保留中文、字母、数字、点、下划线、短横线、空格、括号）
        name = name.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9._\\- ()（）]", "_");
        // 限制文件名长度（含扩展名，最大 255 字符）
        if (name.length() > 255) {
            String suffix = extractSuffix(name);
            String baseName = suffix.isEmpty() ? name : name.substring(0, name.length() - suffix.length() - 1);
            name = baseName.substring(0, 255 - suffix.length() - 1) + "." + suffix;
        }
        return name;
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

    /**
     * 获取分布式锁（阻塞式，获取不到则抛异常）。
     *
     * <p>委托 {@link LockStrategy} 获取可重入锁，等待 {@value #LOCK_WAIT_MS}ms，
     * 锁自动过期时间 {@value #LOCK_LEASE_MS}ms。
     *
     * @param locker  分布式锁实例
     * @param lockKey 锁键
     * @return lockValue（用于释放锁时校验持有者）
     * @throws BusinessException 获取锁失败时抛出
     */
    private String acquireLock(DistributedLocker locker, String lockKey) {
        String lockValue;
        try {
            lockValue = locker.tryLock(lockKey, LOCK_WAIT_MS, LOCK_LEASE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.of(NextwikiExceptionCode.LOCK_BUSY).data("lockKey", lockKey);
        }
        if (lockValue == null) {
            throw BusinessException.of(NextwikiExceptionCode.LOCK_BUSY).data("lockKey", lockKey);
        }
        return lockValue;
    }

    /**
     * 批量操作结果
     */
    public record BatchResult(int successCount, List<FailedItem> failedItems) {

        /**
         * 失败项明细
         *
         * @param itemId 失败项ID
         * @param reason 失败原因
         */
        public record FailedItem(String itemId, String reason) {
        }
    }
}
