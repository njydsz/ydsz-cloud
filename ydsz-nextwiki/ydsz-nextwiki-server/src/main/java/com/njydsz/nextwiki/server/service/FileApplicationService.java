package com.njydsz.nextwiki.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
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
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
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

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final FolderDomainService folderDomainService;
    private final FileVersionDomainService versionDomainService;
    private final QuotaDomainService quotaDomainService;
    private final TrashDomainService trashDomainService;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FilePermissionService permissionService;
    private final LockStrategy lockStrategy;
    private final VirusScanApplicationService virusScanApplicationService;
    private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;
    /** Outbox 事件服务（可选依赖，发布文件上传/删除领域事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    private static final String LOCK_PREFIX = "nextwiki:lock:folder:";
    private static final long LOCK_LEASE_MS = 30_000L;
    private static final long LOCK_WAIT_MS = 3_000L;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /** NextWiki 全局配置 */
    private final NextwikiProperties properties;

    /** 禁止上传的文件扩展名（安全黑名单） */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "com", "msi", "dll", "scr", "vbs", "jar", "war"
    );

    /**
     * 上传文件（安全校验 → 配额 → 同名策略 → SHA-256 秒传去重 → 存储上传 → 建节点 → 版本 → 事件）。
     * <p>启用病毒扫描时，上传后会先扫描，命中病毒立即删除已上传对象并抛 {@code FILE_VIRUS_DETECTED}。
     * 同名冲突按 {@code nextwiki.upload.conflict-strategy}（KEEP_BOTH/SKIP/OVERWRITE）处理。
     *
     * @param file         上传文件（含原始文件名与内容）
     * @param parentId     目标父目录节点 ID（{@code null}/"0" 视为用户根目录）
     * @param rename       自定义文件名（覆盖原始名），可为 {@code null}
     * @param versionRemark 版本备注（写入版本记录），可为 {@code null}
     * @param userId       操作人 ID（所有者/配额/审计归属）
     * @return 上传后的文件节点视图 {@link FileNodeVO}（秒传命中时返回去重节点）
     * @throws BusinessException 文件为空/超限/类型禁用（FILE_*）、存储未配置、病毒命中、配额不足时抛出
     * @transaction 整个方法 {@code @Transactional(rollbackFor = Exception.class)}，节点/版本/配额/索引同事务
     * @complexity 正常路径 O(1)（一次存储上传）；秒传路径 O(1)（一次按哈希查询）；扫描为额外 IO
     * @concurrency 同一文件并发上传受存储幂等影响小；同名 OVERWRITE 由调用方保证串行
     * @note 计算 SHA-256 与病毒扫描均通过 try-with-resources 关闭 InputStream，避免流泄漏
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
            String conflictStrategy = properties.getUpload().getConflictStrategy();
            String strategy = conflictStrategy != null ? conflictStrategy.toUpperCase() : "KEEP_BOTH";
            switch (strategy) {
                case "SKIP" -> {
                    log.info("[FileApplicationService] 同名文件已存在，跳过上传: name={}", fileName);
                    return NextwikiConverter.INSTANT.entityToVO(existingNodes.get(0));
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
        indexUpsert(saved);
                versionDomainService.createVersion(saved.getId(), existing.getStorageKey(),
                        existing.getSize(), fileHash, existing.getMimeType(), "秒传", userId);
                quotaDomainService.addUsage("user", userId, existing.getSize(), 1);
                publishUploadEvent(saved, fileName, userId);
                return NextwikiConverter.INSTANT.entityToVO(saved);
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
        if (properties.getVirusScan().isEnabled()) {
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
        indexUpsert(saved);

        // 8. 创建版本记录
        versionDomainService.createVersion(saved.getId(), storageKey, file.getSize(),
                fileHash, uploaded.getMimeType(), versionRemark, userId);

        // 9. 增加配额用量
        quotaDomainService.addUsage("user", userId, file.getSize(), 1);

        // 10. 发布上传事件
        publishUploadEvent(saved, fileName, userId);

        log.info("[FileApplicationService] 文件上传成功: name={}, size={}, userId={}",
                fileName, file.getSize(), userId);

        return NextwikiConverter.INSTANT.entityToVO(saved);
    }

    /**
     * 创建目录（文件夹名经净化处理，防路径穿越/特殊字符）。
     *
     * @param parentId 父目录节点 ID（{@code null}/"0" 视为根目录）
     * @param name     新目录名（会经 {@link #sanitizeFileName} 净化）
     * @param userId   操作人 ID
     * @return 新建目录节点视图 {@link FileNodeVO}
     * @throws BusinessException 父目录不存在/非目录、名称非法时抛出
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}
     * @complexity O(1)（一次父目录解析 + 一次节点写入）
     * @note 线程安全（无共享可变状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO createFolder(String parentId, String name, String userId) {
        String sanitizedName = sanitizeFileName(name);
        FileNode folder = folderDomainService.createFolder(parentId, sanitizedName, userId);
        return NextwikiConverter.INSTANT.entityToVO(folder);
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
    /**
     * 列出目录下子节点（支持排序/类型过滤/数据库分页，避免全量加载）。
     *
     * @param parentId 父目录节点 ID
     * @param userId   用户 ID（用于根目录解析与权限上下文）
     * @param sortBy   排序字段：name / size / time（默认 time）
     * @param sortDir  排序方向：asc / desc（默认 desc）
     * @param type     过滤类型：all / file / folder（默认 all）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果 {@link PageResult}，元素为 {@link FileNodeVO}
     * @throws BusinessException 父目录不存在/非目录时抛出
     * @complexity O(query)（一次数据库分页查询 + 结果映射）
     * @note 只读、无事务边界；分页由 DB 完成，不存在内存爆量风险
     */
    public BaseResponse<List<FileNodeVO>> listFiles(String parentId, String userId,
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
        return pageResult.convert(node -> NextwikiConverter.INSTANT.entityToVO(node));
    }

    /**
     * 移动文件/目录到新父目录（带写权限校验 + 分布式锁防并发竞争）。
     *
     * @param nodeId         待移动节点 ID
     * @param targetParentId 目标父目录 ID
     * @param userId         操作人 ID
     * @return 移动后的节点视图 {@link FileNodeVO}
     * @throws BusinessException 无写权限（PERMISSION_DENIED）、节点/父目录不存在、锁忙（LOCK_BUSY）时抛出
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}，移动与索引更新同事务
     * @concurrency 加 {@code nextwiki:lock:folder:{nodeId}} 可重入分布式锁，确保移动/重命名/删除互斥
     * @complexity O(1)（一次领域服务移动 + 一次 VO 转换）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO move(String nodeId, String targetParentId, String userId) {
        permissionService.checkWrite(nodeId, userId);
        String lockKey = LOCK_PREFIX + nodeId;
        DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue = acquireLock(locker, lockKey);
        try {
            FileNode node = folderDomainService.move(nodeId, targetParentId, userId);
            return NextwikiConverter.INSTANT.entityToVO(node);
        } finally {
            locker.unlock(lockKey, lockValue);
        }
    }

    /**
     * 重命名文件/目录（带写权限校验 + 分布式锁）。
     * <p>新名经净化处理；若与目标目录内已有同名冲突，由底层领域服务决定是否覆盖或抛错。
     *
     * @param nodeId   待重命名节点 ID
     * @param newName  新名称（会经 {@link #sanitizeFileName} 净化）
     * @param userId   操作人 ID
     * @return 重命名后的节点视图 {@link FileNodeVO}
     * @throws BusinessException 无写权限、节点不存在、锁忙时抛出
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}
     * @concurrency 同 {@link #move} 的分布式锁保护，避免与移动/删除并发竞争
     * @complexity O(1)
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO rename(String nodeId, String newName, String userId) {
        permissionService.checkWrite(nodeId, userId);
        String lockKey = LOCK_PREFIX + nodeId;
        DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue = acquireLock(locker, lockKey);
        try {
            FileNode node = folderDomainService.rename(nodeId, newName, userId);
            return NextwikiConverter.INSTANT.entityToVO(node);
        } finally {
            locker.unlock(lockKey, lockValue);
        }
    }

    /**
     * 删除节点（逻辑删除 → 移入回收站 → 释放配额 → 删索引）。
     * <p>文件节点在删除后保留于回收站，可由 {@link TrashApplicationService} 恢复；物理存储对象延迟清理。
     *
     * @param nodeId 待删除节点 ID
     * @param userId 操作人 ID
     * @return 无返回值
     * @throws BusinessException 无删除权限、节点不存在、锁忙时抛出
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}，软删+入回收站+配额同事务
     * @concurrency 加 {@code nextwiki:lock:folder:{nodeId}} 分布式锁，防止与移动/重命名并发
     * @complexity O(1)（一次软删 + 一次回收站写入 + 一次配额扣减 + 一次索引删除）
     * @note 解锁在 {@code finally} 中执行，确保异常也释放锁；索引删除在事务外（最终一致）
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
        indexDelete(nodeId);
    }

    /**
     * 批量删除（逐条调用 {@link #delete}，允许部分成功，无整体事务）。
     * <p>单条失败被捕获并记录到 {@code failedItems}，不中断其余节点；适用于前端多选删除场景。
     *
     * @param nodeIds 待删除节点 ID 列表
     * @param userId  操作人 ID
     * @return 批量结果 {@link BatchResult}，含成功数与失败明细
     * @complexity O(nodeIds.size())（逐条删除，串行）
     * @note 无整体事务边界，部分成功部分失败属正常；单条失败不影响其他项
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
     * 批量移动（逐条调用 {@link #move}，允许部分成功，无整体事务）。
     *
     * @param nodeIds         待移动节点 ID 列表
     * @param targetParentId  目标父目录 ID
     * @param userId          操作人 ID
     * @return 批量结果 {@link BatchResult}，含成功数与失败明细
     * @complexity O(nodeIds.size())（逐条移动，串行）
     * @note 单条失败被捕获并记入失败明细，不影响其余项
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
     * 复制节点（新建节点，文件复用同一存储对象，不重复占用物理空间）。
     * <p>仅对文件做配额校验与配额+1（共享同一 storageKey，不计重复存储但计文件数）；
     * 复制后发布上传事件以便索引/缩略图等后续处理。
     *
     * @param nodeId         源节点 ID
     * @param targetParentId 目标父目录 ID
     * @param userId         操作人 ID
     * @return 复制出的新节点视图 {@link FileNodeVO}
     * @throws BusinessException 无读权限、源节点不存在、配额不足、父目录非法时抛出
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}，节点写入+版本+配额同事务
     * @complexity O(1)（一次复制写入 + 一次版本引用 + 一次配额调整）
     * @note 复制不复制目录递归子树（仅单节点）；存储对象共享，删除源不影响副本
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
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
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
        copyNode.setUpdatedBy(userId);

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
        return NextwikiConverter.INSTANT.entityToVO(saved);
    }

    /**
     * 版本回滚：将文件恢复至指定历史版本。
     * <p>由领域服务完成版本指针与内容键回退，再返回最新节点视图。
     *
     * @param nodeId        文件节点 ID
     * @param targetVersion 目标回滚版本号（非 {@code null}）
     * @param userId        操作人 ID
     * @return 回滚后的节点视图 {@link FileNodeVO}
     * @throws BusinessException 节点不存在、版本不存在/无权限时抛出
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}
     * @complexity O(1)（一次领域回滚 + 一次查询）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO rollbackVersion(String nodeId, Integer targetVersion, String userId) {
        versionDomainService.rollback(nodeId, targetVersion, userId);
        FileNode updated = fileNodeRepository.findById(nodeId);
        return NextwikiConverter.INSTANT.entityToVO(updated);
    }

    /**
     * 获取文件版本历史列表（按版本号升序）。
     *
     * @param nodeId 文件节点 ID
     * @return 版本记录列表 {@link FileVersion}（可能为空，非 {@code null}）
     * @complexity O(1)（一次按节点 ID 查询）
     * @note 只读，无事务边界
     */
    public List<FileVersion> getVersionHistory(String nodeId) {
        return versionDomainService.getVersionHistory(nodeId);
    }

    /**
     * 获取文件详情（按节点 ID 查询并转为视图对象）。
     *
     * @param nodeId 文件节点 ID
     * @return 文件节点视图 {@link FileNodeVO}
     * @throws BusinessException 节点不存在时抛出 FILE_NOT_FOUND
     * @complexity O(1)（一次按 ID 查询）
     * @note 只读，无事务边界；不在此做权限校验，调用方可按需叠加 {@code permissionService}
     */
    public FileNodeVO getFileInfo(String nodeId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }
        return NextwikiConverter.INSTANT.entityToVO(node);
    }

    /**
     * 切换星标状态（已星标→取消，未星标→星标）。
     *
     * @param nodeId 文件节点 ID
     * @param userId 操作人 ID（同时记为最后更新人）
     * @return 无返回值
     * @throws BusinessException 节点不存在时抛出 FILE_NOT_FOUND
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}（一次字段更新）
     * @complexity O(1)（一次查询 + 一次更新）
     * @note 幂等：重复调用在两种状态间来回切换
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStar(String nodeId, String userId) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }
        node.setStarred(node.getStarred() == null || !node.getStarred());
        node.setUpdatedBy(userId);
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
        long maxFileSize = properties.getUpload().getMaxFileSize();
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
        String allowedTypes = properties.getUpload().getAllowedTypes();
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
        // 同步发布到 Outbox（跨模块事件驱动）
        publishOutboxEvent(StandardEventTypes.FILE_UPLOADED, saved.getId(), saved);
    }

    /**
     * 发布领域事件到 Outbox（可选依赖，不存在时安全降级）
     */
    private void publishOutboxEvent(String eventType, String aggregateId, Object payload) {
        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService == null) {
            return;
        }
        try {
            outboxService.appendToOutbox("FileNode", aggregateId, eventType,
                    YdszJson.toJson(payload));
        } catch (Exception e) {
            log.warn("Failed to publish outbox event: type={}, id={}, error={}",
                    eventType, aggregateId, e.getMessage());
        }
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

    private void indexUpsert(FileNode entity) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexUpsert("wiki", entity);
        }
    }

    private void indexDelete(String id) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexDelete("wiki", id);
        }
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
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
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
        node.setUpdatedBy(userId);

        return node;
    }

    /**
     * 构建秒传去重的文件节点（引用已有存储对象，跳过上传）
     */
    private FileNode buildDedupedFileNode(String parentId, String name, String suffix,
                                            FileNode existing, String fileHash,
                                            String path, int level, String userId) {
        FileNode node = FileNode.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
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
        node.setUpdatedBy(userId);

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
        String uuid = String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
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

    private String calculateSha256(InputStream inputStream) throws IOException {
        return DigestUtils.sha256Hex(inputStream);
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
            lockValue = locker.tryLock(lockKey, LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
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
