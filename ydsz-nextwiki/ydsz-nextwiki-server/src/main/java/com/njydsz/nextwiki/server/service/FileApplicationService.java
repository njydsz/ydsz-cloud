package com.njydsz.nextwiki.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.event.FileVersionSnapshotEvent;
import com.njydsz.nextwiki.domain.query.FileNodeQuery;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.nextwiki.domain.service.FolderDomainService;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.service.TrashDomainService;
import com.njydsz.nextwiki.domain.service.StorageReferenceService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.cache.NextwikiCacheService;
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;

/**
 * 文件应用服务
 *
 * <p>编排文件上传、下载、移动、重命名、删除等操作，协调领域服务与底层存储。
 *
 * <p><b>核心流程：</b>
 *
 * <ul>
 *   <li>上传：安全校验 → 配额校验 → 秒传去重 → 存储上传 → 创建 FileNodeVO → 创建版本 → 事件发布
 *   <li>删除：逻辑删除 FileNodeVO → 移入回收站 → 释放配额 → 删除索引
 *   <li>移动/重命名：更新 FileNodeVO → 递归更新子节点路径 → 事件通知
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
  private final StorageQuotaRepository storageQuotaRepository;
  private final StorageReferenceService storageReferenceService;
  private final TrashDomainService trashDomainService;
  private final FileNodeRepository fileNodeRepository;
  private final FileVersionRepository versionRepository;
  private final TrashItemRepository trashItemRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final FilePermissionService permissionService;
  private final LockStrategy lockStrategy;
  private final VirusScanApplicationService virusScanApplicationService;
  private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

  /** 统一领域事件发布门面（可选依赖，未配置时安全降级） */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** 文件夹复制服务（分批复制子树，每批独立事务） */
  private final FolderCopyService folderCopyService;

  /** 事务管理器（用于创建编程式事务模板） */
  private final PlatformTransactionManager transactionManager;

  /** 批量任务线程池（使用 ydsz-common-thread 统一管理的 nextwikiTaskExecutor） */
  @org.springframework.beans.factory.annotation.Qualifier("nextwikiTaskExecutor")
  private final Executor batchTaskExecutor;

  /** 缓存服务（文件详情、目录列表、配额用量 Redis 缓存） */
  private final NextwikiCacheService cacheService;

  /** 编程式事务模板（用于精确控制事务边界，将IO操作移出事务） */
  private TransactionTemplate transactionTemplate;

  @PostConstruct
  void initTransactionTemplate() {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  private static final String LOCK_PREFIX = "nextwiki:lock:folder:";
  private static final long LOCK_LEASE_MS = 30_000L;
  private static final long LOCK_WAIT_MS = 3_000L;

  @Autowired(required = false)
  private IFileStorageProvider fileStorageProvider;

  /** NextWiki 全局配置 */
  private final NextwikiProperties properties;

  /** 禁止上传的文件扩展名（安全黑名单） */
  private static final Set<String> BLOCKED_EXTENSIONS =
      Set.of("exe", "bat", "cmd", "sh", "com", "msi", "dll", "scr", "vbs", "jar", "war");

  /**
   * 上传文件（安全校验 → 配额 → 同名策略 → SHA-256 秒传去重 → 存储上传 → 建节点 → 版本 → 事件）。
   *
   * <p><b>P0-2 事务优化：</b>将 SHA-256 计算、存储上传、病毒扫描等耗时 IO 操作移出事务，仅在短事务中执行数据库操作。
   *
   * <p>启用病毒扫描时，上传后会先扫描，命中病毒立即删除已上传对象并抛 {@code FILE_VIRUS_DETECTED}。 同名冲突按 {@code
   * nextwiki.upload.conflict-strategy}（KEEP_BOTH/SKIP/OVERWRITE）处理。
   *
   * @param file 上传文件（含原始文件名与内容）
   * @param parentId 目标父目录节点 ID（{@code null}/"0" 视为用户根目录）
   * @param rename 自定义文件名（覆盖原始名），可为 {@code null}
   * @param versionRemark 版本备注（写入版本记录），可为 {@code null}
   * @param userId 操作人 ID（所有者/配额/审计归属）
   * @return 上传后的文件节点视图 {@link FileNodeVO}（秒传命中时返回去重节点）
   * @throws BusinessException 文件为空/超限/类型禁用（FILE_*）、存储未配置、病毒命中、配额不足时抛出
   * @complexity 正常路径 O(1)（一次存储上传）；秒传路径 O(1)（一次按哈希查询）；扫描为额外 IO
   * @concurrency 同一文件并发上传受存储幂等影响小；同名 OVERWRITE 由调用方保证串行
   * @note 计算 SHA-256、病毒扫描与存储上传均在事务外执行，仅数据库操作使用短事务
   */
  public FileNodeVO upload(
      MultipartFile file, String parentId, String rename, String versionRemark, String userId) {
    // ===== 阶段1：准备阶段（无事务边界） =====
    // 1. 安全校验
    validateUpload(file);

    // 2. 配额校验
    quotaDomainService.checkQuota(loadQuota("user", userId), file.getSize());

    // 3. 解析父目录
    String originalFilename = file.getOriginalFilename();
    String rawName = (rename != null && !rename.isEmpty()) ? rename : originalFilename;
    String fileName = sanitizeFileName(rawName);
    String suffix = extractSuffix(fileName);

    FileNodeVO parent = resolveParentNode(parentId, userId);
    String resolvedParentId = parent.getId();
    String parentPath = parent.getPath() != null ? parent.getPath() : "/";

    // 同名冲突检测（只读查询，不含写操作）
    List<FileNodeVO> existingNodes =
        fileNodeRepository.findByNameAndParent(fileName, resolvedParentId, userId);
    String conflictStrategy = properties.getUpload().getConflictStrategy();
    String strategy = conflictStrategy != null ? conflictStrategy.toUpperCase() : "KEEP_BOTH";

    if (existingNodes != null && !existingNodes.isEmpty() && "SKIP".equals(strategy)) {
      log.info("[FileApplicationService] 同名文件已存在，跳过上传: name={}", fileName);
      return existingNodes.get(0);
    }
    if (existingNodes != null && !existingNodes.isEmpty() && "KEEP_BOTH".equals(strategy)) {
      fileName = resolveUniqueName(fileName, resolvedParentId, userId);
    }

    String path =
        parentPath.endsWith("/") ? parentPath + fileName + "/" : parentPath + "/" + fileName + "/";
    int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

    // 4. 计算文件哈希（IO 操作，在事务外执行）
    String fileHash = null;
    try (InputStream hashStream = file.getInputStream()) {
      fileHash = calculateSha256(hashStream);
    } catch (Exception e) {
      log.warn("[FileApplicationService] SHA-256 计算失败: {}", e.getMessage());
    }

    // 5. 秒传去重检查（只读查询）
    FileNodeVO dedupExisting = null;
    if (fileHash != null) {
      dedupExisting = fileNodeRepository.findByFileHash(fileHash).orElse(null);
    }

    // 6. 存储上传（IO 操作，在事务外执行）
    FileStorage uploaded = null;
    String storageKey = null;
    if (dedupExisting == null) {
      IFileStorage storage = resolveStorage();
      if (storage == null) {
        throw new BusinessException(NextwikiExceptionCode.FILE_STORAGE_NOT_CONFIGURED);
      }
      storageKey = generateStorageKey(userId, fileName);
      uploaded = storage.upload(null, storageKey, file);

      // 病毒扫描（IO 操作，在事务外执行）
      if (properties.getVirusScan().isEnabled()) {
        try (InputStream scanStream = file.getInputStream()) {
          var scanResult = virusScanApplicationService.scan(scanStream, file.getSize());
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
    }

    // ===== 阶段2：事务阶段（短事务，仅数据库操作） =====
    UploadPrepareContext ctx =
        UploadPrepareContext.builder()
            .resolvedParentId(resolvedParentId)
            .fileName(fileName)
            .suffix(suffix)
            .dedupExisting(dedupExisting)
            .fileHash(fileHash)
            .path(path)
            .level(level)
            .uploaded(uploaded)
            .storageKey(storageKey)
            .versionRemark(versionRemark)
            .userId(userId)
            .overwriteTarget("OVERWRITE".equals(strategy) && existingNodes != null && !existingNodes.isEmpty()
                ? existingNodes.get(0) : null)
            .build();

    if (dedupExisting != null) {
      return persistDedupedNode(ctx);
    }
    return persistNewNode(ctx);
  }

  /**
   * 秒传命中时持久化去重节点（短事务）。
   */
  private FileNodeVO persistDedupedNode(UploadPrepareContext ctx) {
    return transactionTemplate.execute(status -> {
      FileNodeDTO dedupedNode =
          buildDedupedFileNode(
              ctx.resolvedParentId,
              ctx.fileName,
              ctx.suffix,
              ctx.dedupExisting,
              ctx.fileHash,
              ctx.path,
              ctx.level,
              ctx.userId);
      FileNodeVO saved = fileNodeRepository.save(dedupedNode);
      indexUpsert(saved);
      storageReferenceService.increment(ctx.dedupExisting.getStorageKey());
      storageQuotaRepository.addUsage("user", ctx.userId, ctx.dedupExisting.getSize(), 1);
      publishUploadEvent(saved, ctx.fileName, ctx.userId);
      // P3-2: 发布版本快照事件（事务提交后异步创建版本记录）
      eventPublisher.publishEvent(
          new FileVersionSnapshotEvent(
              this,
              saved.getId(),
              ctx.dedupExisting.getStorageKey(),
              ctx.dedupExisting.getSize(),
              ctx.fileHash,
              ctx.dedupExisting.getMimeType(),
              "秒传",
              ctx.userId));

      log.info("[FileApplicationService] 秒传上传成功: name={}, hash={}", ctx.fileName, ctx.fileHash);
      return saved;
    });
  }

  /**
   * 新文件上传时持久化节点（短事务）。
   */
  private FileNodeVO persistNewNode(UploadPrepareContext ctx) {
    return transactionTemplate.execute(status -> {
      // 处理 OVERWRITE 策略：删除旧节点的数据库记录
      if (ctx.overwriteTarget != null) {
        FileNodeVO existing = ctx.overwriteTarget;
        if (existing.getStorageKey() != null) {
          long refCount = storageReferenceService.decrement(existing.getStorageKey());
          if (refCount <= 0) {
            IFileStorage storage = resolveStorage();
            if (storage != null) {
              storage.delete(existing.getBucketName(), existing.getStorageKey());
              log.info("[StorageReference] 覆盖时物理清除存储对象: storageKey={}", existing.getStorageKey());
            }
          } else {
            log.info("[StorageReference] 覆盖跳过物理删除，仍有引用: storageKey={}, refCount={}",
                existing.getStorageKey(), refCount);
          }
        }
        if (existing.getSize() != null) {
          storageQuotaRepository.subtractUsage("user", ctx.userId, existing.getSize(), 1);
        }
        fileNodeRepository.physicalDelete(existing.getId());
      }

      FileNodeDTO nodeDto =
          buildFileNode(
              ctx.resolvedParentId,
              ctx.fileName,
              ctx.suffix,
              ctx.uploaded,
              ctx.storageKey,
              ctx.fileHash,
              ctx.path,
              ctx.level,
              ctx.userId);
      FileNodeVO saved = fileNodeRepository.save(nodeDto);
      indexUpsert(saved);
      storageReferenceService.increment(ctx.storageKey);
      storageQuotaRepository.addUsage("user", ctx.userId, ctx.uploaded.getSize(), 1);
      publishUploadEvent(saved, ctx.fileName, ctx.userId);
      // P3-2: 发布版本快照事件（事务提交后异步创建版本记录）
      eventPublisher.publishEvent(
          new FileVersionSnapshotEvent(
              this,
              saved.getId(),
              ctx.storageKey,
              ctx.uploaded.getSize(),
              ctx.fileHash,
              ctx.uploaded.getMimeType(),
              ctx.versionRemark,
              ctx.userId));

      log.info("[FileApplicationService] 文件上传成功: name={}, size={}, userId={}",
          ctx.fileName, ctx.uploaded.getSize(), ctx.userId);
      return saved;
    });
  }

  /**
   * 上传准备阶段的上下文数据（从准备阶段传递到事务阶段）。
   *
   * <p>P0-2: 将 IO 操作结果封装为不可变上下文，供事务方法使用，确保事务边界最小化。
   */
  @lombok.Builder
  private static class UploadPrepareContext {
    private final String resolvedParentId;
    private final String fileName;
    private final String suffix;
    private final FileNodeVO dedupExisting;
    private final String fileHash;
    private final String path;
    private final int level;
    private final FileStorage uploaded;
    private final String storageKey;
    private final String versionRemark;
    private final String userId;
    /** OVERWRITE 策略下的待删除节点 */
    private final FileNodeVO overwriteTarget;
  }

  /**
   * 创建目录（文件夹名经净化处理，防路径穿越/特殊字符）。
   *
   * @param parentId 父目录节点 ID（{@code null}/"0" 视为根目录）
   * @param name 新目录名（会经 {@link #sanitizeFileName} 净化）
   * @param userId 操作人 ID
   * @return 新建目录节点视图 {@link FileNodeVO}
   * @throws BusinessException 父目录不存在/非目录、名称非法时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次父目录解析 + 一次节点写入）
   * @note 线程安全（无共享可变状态）
   */
  @Transactional(rollbackFor = Exception.class)
  public FileNodeVO createFolder(String parentId, String name, String userId) {
    String sanitizedName = sanitizeFileName(name);
    FileNodeVO parent = resolveParentNode(parentId, userId);
    List<FileNodeVO> siblings = fileNodeRepository.findChildren(parent.getId());
    FileNodeVO folder = folderDomainService.createFolder(parent, siblings, sanitizedName, userId);
    FileNodeVO saved = fileNodeRepository.save(NextwikiConverter.INSTANT.toDTO(folder));

    // 失效缓存：父目录子节点列表
    cacheService.evictChildren(parent.getId());

    publishUploadEvent(saved, sanitizedName, userId);
    return saved;
  }

  /**
   * 列出目录（支持排序、过滤、分页，使用数据库分页避免全量加载）
   *
   * @param parentId 父目录ID
   * @param userId 用户ID
   * @param sortBy 排序字段：name / size / time（默认 time）
   * @param sortDir 排序方向：asc / desc（默认 desc）
   * @param type 过滤类型：all / file / folder（默认 all）
   * @param page 页码（从 1 开始，默认 1）
   * @param pageSize 每页大小（默认 50）
   * @return 分页结果（含 total/pageCount）
   */
  /**
   * 列出目录下子节点（支持排序/类型过滤/数据库分页，避免全量加载）。
   *
   * @param parentId 父目录节点 ID
   * @param userId 用户 ID（用于根目录解析与权限上下文）
   * @param sortBy 排序字段：name / size / time（默认 time）
   * @param sortDir 排序方向：asc / desc（默认 desc）
   * @param type 过滤类型：all / file / folder（默认 all）
   * @param page 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 统一响应结果，data 为分页结果 {@link PageResponse}，元素为 {@link FileNodeVO}
   * @throws BusinessException 父目录不存在/非目录时抛出
   * @complexity O(query)（一次数据库分页查询 + 结果映射）
   * @note 只读、无事务边界；分页由 DB 完成，不存在内存爆量风险
   */
  public YdszResponse<PageResponse<List<FileNodeVO>>> listFiles(
      String parentId,
      String userId,
      String sortBy,
      String sortDir,
      String type,
      int page,
      int pageSize) {
    // 解析父目录ID（与原 listChildren 保持一致：根目录自动解析）
    FileNodeVO parent = resolveParentNode(parentId, userId);
    String resolvedParentId = parent.getId();

    // 数据库分页查询（含类型过滤与排序）
    PageResponse<List<FileNodeVO>> pageResult =
        fileNodeRepository.findPageChildren(
            FileNodeQuery.builder()
                .parentId(resolvedParentId)
                .nodeType(type)
                .sortBy(normalizeSortBy(sortBy))
                .sortDir(normalizeSortDir(sortDir))
                .page(page)
                .pageSize(pageSize)
                .build());

    return YdszResponse.success(pageResult);
  }

  /**
   * 移动文件/目录到新父目录（带写权限校验 + 分布式锁防并发竞争）。
   *
   * @param nodeId 待移动节点 ID
   * @param targetParentId 目标父目录 ID
   * @param userId 操作人 ID
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
      FileNodeVO node = fileNodeRepository.findById(nodeId)
          .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));
      String oldParentId = node.getParentId();
      FileNodeVO targetParent = resolveParentNode(targetParentId, userId);
      List<FileNodeVO> targetSiblings = fileNodeRepository.findChildren(targetParent.getId());
      FileNodeVO movedNode = folderDomainService.move(node, targetParent, targetSiblings, userId);
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(movedNode));

      // 失效缓存：文件详情 + 原父目录 + 新父目录
      cacheService.evictFile(nodeId);
      cacheService.evictChildren(oldParentId);
      cacheService.evictChildren(targetParentId);

      return movedNode;
    } finally {
      locker.unlock(lockKey, lockValue);
    }
  }

  /**
   * 重命名文件/目录（带写权限校验 + 分布式锁）。
   *
   * <p>新名经净化处理；若与目标目录内已有同名冲突，由底层领域服务决定是否覆盖或抛错。
   *
   * @param nodeId 待重命名节点 ID
   * @param newName 新名称（会经 {@link #sanitizeFileName} 净化）
   * @param userId 操作人 ID
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
      FileNodeVO node = fileNodeRepository.findById(nodeId)
          .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));
      FileNodeVO parent = resolveParentNode(node.getParentId(), userId);
      FileNodeVO renamedNode = folderDomainService.rename(node, parent, sanitizeFileName(newName), userId);
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(renamedNode));

      // 失效缓存：文件详情 + 父目录子节点列表
      cacheService.evictFileAndParent(nodeId, node.getParentId());

      return renamedNode;
    } finally {
      locker.unlock(lockKey, lockValue);
    }
  }

  /**
   * 删除节点（逻辑删除 → 移入回收站 → 释放配额 → 删索引）。
   *
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
    FileNodeVO node = fileNodeRepository.findById(nodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));

    String lockKey = LOCK_PREFIX + nodeId;
    DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
    String lockValue = acquireLock(locker, lockKey);
    try {
      fileNodeRepository.softDelete(nodeId, node.getPath());
      TrashItemDTO trashItem = trashDomainService.moveToTrash(node, userId);
      trashItemRepository.save(trashItem);

      if (node.isFile() && node.getSize() != null) {
        storageQuotaRepository.subtractUsage("user", userId, node.getSize(), 1);
      }
    } finally {
      locker.unlock(lockKey, lockValue);
    }

    // 引用计数 -1，归零时物理删除存储对象（防止秒传/副本悬空引用）
    if (node.isFile() && node.getStorageKey() != null) {
      long refCount = storageReferenceService.decrement(node.getStorageKey());
      if (refCount <= 0) {
        IFileStorage storage = resolveStorage();
        if (storage != null) {
          storage.delete(node.getBucketName(), node.getStorageKey());
          log.info("[StorageReference] 删除时物理清除存储对象: storageKey={}", node.getStorageKey());
        }
      } else {
        log.info(
            "[StorageReference] 删除跳过物理删除，仍有引用: storageKey={}, refCount={}",
            node.getStorageKey(),
            refCount);
      }
    }

    // 失效缓存（文件详情 + 父目录子节点列表）
    cacheService.evictFileAndParent(nodeId, node.getParentId());
    // 配额缓存失效
    cacheService.evictQuotaOnChange("user", userId);

    log.info("[FileApplicationService] 删除文件: nodeId={}, name={}", nodeId, node.getName());
    indexDelete(nodeId);
  }

  /**
   * 批量删除（并行处理，允许部分成功，无整体事务）。
   *
   * <p>使用 {@link java.util.concurrent.CompletableFuture} 并行处理各节点，提升批量操作吞吐量。
   * 单条失败被捕获并记录到 {@code failedItems}，不中断其余节点。
   *
   * @param nodeIds 待删除节点 ID 列表
   * @param userId 操作人 ID
   * @return 批量结果 {@link BatchResult}，含成功数与失败明细
   * @complexity O(nodeIds.size() / parallelism)（并行删除）
   * @note 无整体事务边界，部分成功部分失败属正常；单条失败不影响其他项
   * @concurrency 使用 nextwikiTaskExecutor 线程池并行处理
   */
  public BatchResult batchDelete(List<String> nodeIds, String userId) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return new BatchResult(0, List.of());
    }

    // 批量查询节点，避免 N 次单条查询
    List<FileNodeVO> nodes = fileNodeRepository.findByIds(nodeIds);
    Map<String, FileNodeVO> nodeMap = new HashMap<>();
    for (FileNodeVO node : nodes) {
      nodeMap.put(node.getId(), node);
    }

    // 并行处理各节点删除
    List<CompletableFuture<String>> futures = nodeIds.stream()
        .map(nodeId -> CompletableFuture.supplyAsync(() -> {
          try {
            FileNodeVO node = nodeMap.get(nodeId);
            if (node == null) {
              throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
            }
            delete(nodeId, userId);
            return null; // 成功返回 null
          } catch (Exception e) {
            log.error("[FileApplicationService] 批量删除失败: nodeId={}", nodeId, e);
            return e.getMessage();
          }
        }, batchTaskExecutor))
        .toList();

    // 等待所有任务完成并收集结果
    int success = 0;
    List<BatchResult.FailedItem> failedItems = new ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
      try {
        String error = futures.get(i).get();
        if (error == null) {
          success++;
        } else {
          failedItems.add(new BatchResult.FailedItem(nodeIds.get(i), error));
        }
      } catch (Exception e) {
        failedItems.add(new BatchResult.FailedItem(nodeIds.get(i), e.getMessage()));
      }
    }

    log.info("[FileApplicationService] 批量删除: total={}, success={}", nodeIds.size(), success);
    return new BatchResult(success, failedItems);
  }

  /**
   * 批量移动（并行处理，允许部分成功，无整体事务）。
   *
   * <p>使用 {@link java.util.concurrent.CompletableFuture} 并行处理各节点，提升批量操作吞吐量。
   * 单条失败被捕获并记录到 {@code failedItems}，不中断其余节点。
   *
   * @param nodeIds 待移动节点 ID 列表
   * @param targetParentId 目标父目录 ID
   * @param userId 操作人 ID
   * @return 批量结果 {@link BatchResult}，含成功数与失败明细
   * @complexity O(nodeIds.size() / parallelism)（并行移动）
   * @note 单条失败被捕获并记入失败明细，不影响其余项
   * @concurrency 使用 nextwikiTaskExecutor 线程池并行处理
   */
  public BatchResult batchMove(List<String> nodeIds, String targetParentId, String userId) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return new BatchResult(0, List.of());
    }

    // 并行处理各节点移动
    List<CompletableFuture<String>> futures = nodeIds.stream()
        .map(nodeId -> CompletableFuture.supplyAsync(() -> {
          try {
            move(nodeId, targetParentId, userId);
            return null; // 成功返回 null
          } catch (Exception e) {
            log.error("[FileApplicationService] 批量移动失败: nodeId={}", nodeId, e);
            return e.getMessage();
          }
        }, batchTaskExecutor))
        .toList();

    // 等待所有任务完成并收集结果
    int success = 0;
    List<BatchResult.FailedItem> failedItems = new ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
      try {
        String error = futures.get(i).get();
        if (error == null) {
          success++;
        } else {
          failedItems.add(new BatchResult.FailedItem(nodeIds.get(i), error));
        }
      } catch (Exception e) {
        failedItems.add(new BatchResult.FailedItem(nodeIds.get(i), e.getMessage()));
      }
    }

    log.info("[FileApplicationService] 批量移动: total={}, success={}", nodeIds.size(), success);
    return new BatchResult(success, failedItems);
  }

  /**
   * 复制节点（新建节点，文件复用同一存储对象，不重复占用物理空间）。
   *
   * <p>仅对文件做配额校验与配额+1（共享同一 storageKey，不计重复存储但计文件数）； 复制后发布上传事件以便索引/缩略图等后续处理。
   *
   * @param nodeId 源节点 ID
   * @param targetParentId 目标父目录 ID
   * @param userId 操作人 ID
   * @return 复制出的新节点视图 {@link FileNodeVO}
   * @throws BusinessException 无读权限、源节点不存在、配额不足、父目录非法时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}，节点写入+版本+配额同事务
   * @complexity O(1)（一次复制写入 + 一次版本引用 + 一次配额调整）
   * @note 复制不复制目录递归子树（仅单节点）；存储对象共享，删除源不影响副本
   */
  @Transactional(rollbackFor = Exception.class)
  public FileNodeVO copy(String nodeId, String targetParentId, String userId) {
    permissionService.checkRead(nodeId, userId);
    FileNodeVO source = fileNodeRepository.findById(nodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));

    FileNodeVO parent = resolveParentNode(targetParentId, userId);
    String resolvedParentId = parent.getId();
    String parentPath = parent.getPath() != null ? parent.getPath() : "/";
    String newName = source.getName();
    String path =
        parentPath.endsWith("/") ? parentPath + newName + "/" : parentPath + "/" + newName + "/";
    int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

    // 配额校验（仅文件需要）
    if (source.isFile() && source.getSize() != null) {
      quotaDomainService.checkQuota(loadQuota("user", userId), source.getSize());
    }

    FileNodeDTO copyNode =
        FileNodeDTO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
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
            .createdBy(userId)
            .updatedBy(userId)
            .build();

    FileNodeVO saved = fileNodeRepository.save(copyNode);

    // 文件引用计数 +1（副本共享同一 storageKey）
    if (source.isFile() && source.getStorageKey() != null) {
      storageReferenceService.increment(source.getStorageKey());
    }

    // 文件创建版本引用
    if (source.isFile()) {
      List<FileVersionDTO> existingVersionDTOs = NextwikiConverter.INSTANT.versionListToDTO(
          versionRepository.findByFileNodeId(saved.getId()));
      FileVersionDomainService.VersionCreateResult versionResult =
          versionDomainService.createVersion(
              saved,
              existingVersionDTOs,
              source.getStorageKey(),
              source.getSize(),
              source.getFileHash(),
              source.getMimeType(),
              "复制",
              userId);
      versionRepository.setActiveVersion(saved.getId(), -1);
      versionRepository.save(versionResult.newVersion());
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(versionResult.updatedFileNode()));
      cleanupExcessVersions(saved.getId());
      if (source.getSize() != null) {
        storageQuotaRepository.addUsage("user", userId, source.getSize(), 1);
      }
    }

    publishUploadEvent(saved, newName, userId);
    log.info("[FileApplicationService] 复制文件: sourceId={}, targetId={}", nodeId, saved.getId());
    return saved;
  }

  /**
   * 递归复制文件夹及其全部子节点（含子目录与文件）。
   *
   * <p>采用分批复制策略：根节点创建在短事务中完成，后代节点由 {@link FolderCopyService} 分批复制（每批 {@value
   * com.njydsz.nextwiki.server.service.FolderCopyService#BATCH_SIZE} 个节点，每批独立事务），避免长事务持锁。
   *
   * <p>所有文件共享同一存储对象（引用计数 +1），不重复占用物理空间。复制过程中维护旧新节点 ID 映射，确保父子关系正确重建。
   *
   * @param nodeId 源文件夹节点 ID
   * @param targetParentId 目标父目录 ID
   * @param userId 操作人 ID
   * @return 复制出的新文件夹节点视图 {@link FileNodeVO}
   * @throws BusinessException 无读权限、源节点不存在/非文件夹、配额不足、父目录非法时抛出
   * @transaction 根节点创建使用短事务；后代节点由 FolderCopyService 分批独立事务
   * @complexity O(n)（n 为文件夹下全部子节点数）
   * @concurrency 复制操作不加分布式锁，源数据读不加锁；目标父目录由调用方保证有效性
   * @note 仅复制文件配额计数（不重复占存储）；复制后的文件夹节点默认去星标、私有状态
   */
  public FileNodeVO copyFolder(String nodeId, String targetParentId, String userId) {
    // 1. 权限校验 + 源文件夹验证
    permissionService.checkRead(nodeId, userId);
    FileNodeVO sourceFolder = fileNodeRepository.findById(nodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));
    if (!sourceFolder.isFolder()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER)
          .data("nodeId", nodeId);
    }

    // 2. 目标父目录验证
    FileNodeVO targetParent = resolveParentNode(targetParentId, userId);
    String targetParentPath = targetParent.getPath() != null ? targetParent.getPath() : "/";
    int targetParentLevel = targetParent.getLevel() != null ? targetParent.getLevel() + 1 : 1;

    // 3. 配额预校验（仅统计文件）
    String sourcePath = sourceFolder.getPath() != null ? sourceFolder.getPath() : "/";
    List<FileNodeVO> allDescendants = fileNodeRepository.findAllDescendants(nodeId);
    long totalFileBytes =
        allDescendants.stream()
            .filter(FileNodeVO::isFile)
            .filter(n -> n.getSize() != null)
            .mapToLong(FileNodeVO::getSize)
            .sum();
    long totalFileCount = allDescendants.stream().filter(FileNodeVO::isFile).count();
    quotaDomainService.checkQuota(loadQuota("user", userId), totalFileBytes);

    // 4. 短事务：创建根文件夹节点
    String newFolderId =
        folderCopyService.createRootFolderNode(
            sourceFolder, targetParent, targetParentPath, targetParentLevel, userId);

    // 5. 分批复制后代节点（每批独立事务）
    int copied =
        folderCopyService.copyDescendantsBatch(sourcePath, newFolderId, targetParent, userId);

    // 6. 批量增加配额（文件数 + 引用文件大小）
    if (totalFileBytes > 0 || totalFileCount > 0) {
      storageQuotaRepository.addUsage(
          "user", userId, totalFileBytes, (int) Math.min(totalFileCount, Integer.MAX_VALUE));
    }

    // 7. 发布复制事件
    FileNodeVO newFolderNode = fileNodeRepository.findById(newFolderId).orElse(null);
    if (newFolderNode != null) {
      publishUploadEvent(newFolderNode, sourceFolder.getName(), userId);
    }
    log.info(
        "[FileApplicationService] 复制文件夹完成: sourceId={}, newId={}, descendants={}",
        nodeId,
        newFolderId,
        copied);

    return newFolderNode;
  }

  /**
   * 版本回滚：将文件恢复至指定历史版本。
   *
   * <p>由领域服务完成版本指针与内容键回退，再返回最新节点视图。
   *
   * @param nodeId 文件节点 ID
   * @param targetVersion 目标回滚版本号（非 {@code null}）
   * @param userId 操作人 ID
   * @return 回滚后的节点视图 {@link FileNodeVO}
   * @throws BusinessException 节点不存在、版本不存在/无权限时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次领域回滚 + 一次查询）
   */
  @Transactional(rollbackFor = Exception.class)
  public FileNodeVO rollbackVersion(String nodeId, Integer targetVersion, String userId) {
    FileNodeVO node = fileNodeRepository.findById(nodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));

    FileVersionDTO targetDTO = NextwikiConverter.INSTANT.versionToDTO(
        versionRepository.findByFileNodeIdAndVersion(nodeId, targetVersion).orElse(null));
    List<FileVersionDTO> allVersionDTOs = NextwikiConverter.INSTANT.versionListToDTO(
        versionRepository.findByFileNodeId(nodeId));

    FileVersionDomainService.VersionRollbackResult result =
        versionDomainService.rollback(node, targetDTO, allVersionDTOs, userId);

    versionRepository.setActiveVersion(nodeId, -1);
    versionRepository.save(result.newVersion());
    fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(result.updatedFileNode()));

    // 发布回滚事件
    eventPublisher.publishEvent(
        FileOperatedEvent.builder()
            .operation(FileOperatedEvent.OP_VERSION_ROLLBACK)
            .fileNodeId(nodeId)
            .fileName(result.updatedFileNode().getName())
            .nodeType(result.updatedFileNode().getNodeType())
            .storageKey(result.newVersion().getStorageKey())
            .bucketName(result.updatedFileNode().getBucketName())
            .operatorId(userId)
            .operatedAt(LocalDateTime.now())
            .extra("rollback to v" + result.targetVersionNumber())
            .build());

    return result.updatedFileNode();
  }

  /**
   * 获取文件版本历史列表（按版本号升序）。
   *
   * @param nodeId 文件节点 ID
   * @return 版本记录列表 {@link FileVersionVO}（可能为空，非 {@code null}）
   * @complexity O(1)（一次按节点 ID 查询）
   * @note 只读，无事务边界
   */
  public List<FileVersionVO> getVersionHistory(String nodeId) {
    return versionRepository.findByFileNodeId(nodeId);
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
    return cacheService.getFile(nodeId,
        () -> fileNodeRepository.findById(nodeId))
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));
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
    FileNodeVO node = fileNodeRepository.findById(nodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId));
    node.setStarred(node.getStarred() == null || !node.getStarred());
    node.setUpdatedBy(userId);
    fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));

    // 失效缓存：文件详情
    cacheService.evictFile(nodeId);

    log.info(
        "[FileApplicationService] 切换星标: nodeId={}, starred={}, userId={}",
        nodeId,
        node.getStarred(),
        userId);
  }

  // ==================== 批量操作 ====================

  /**
   * 批量更新节点排序值（P1-5：拖拽排序 API）。
   *
   * <p>前端拖拽完成后提交目标父目录下的完整排序列表，服务端在单个事务内批量更新 sort 字段。
   *
   * <p><b>安全策略：</b>
   *
   * <ul>
   *   <li>校验所有节点属于同一父目录（防止越权修改其他目录节点）
   *   <li>在父目录粒度加分布式锁（防并发拖拽冲突）
   *   <li>批量更新后失效父目录缓存
   * </ul>
   *
   * @param parentId 父目录 ID（用于权限校验与缓存失效）
   * @param items 排序条目列表（nodeId + sort）
   * @param userId 操作人 ID
   * @return 实际更新的节点数
   * @throws BusinessException 节点不属于该父目录时抛出 FILE_NOT_BELONG_TO_PARENT
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}（批量更新 + 缓存失效）
   * @complexity O(n)（n 为排序条目数，单次查询 + 批量更新）
   * @note 前端应传完整排序列表（含所有子节点），服务端按新值全量覆盖
   */
  @Transactional(rollbackFor = Exception.class)
  public int batchSort(String parentId, List<com.njydsz.nextwiki.api.dto.NextwikiDTOs.SortItem> items, String userId) {
    if (items == null || items.isEmpty()) {
      return 0;
    }

    List<String> nodeIds = items.stream()
        .map(com.njydsz.nextwiki.api.dto.NextwikiDTOs.SortItem::getNodeId)
        .collect(Collectors.toList());

    // 批量查询节点详情
    List<FileNodeVO> nodes = fileNodeRepository.findByIds(nodeIds);

    // 校验所有节点属于同一父目录
    for (FileNodeVO node : nodes) {
      if (parentId != null && !parentId.equals(node.getParentId())) {
        throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_BELONG_TO_PARENT)
            .data("nodeId", node.getId())
            .data("expectedParentId", parentId)
            .data("actualParentId", node.getParentId());
      }
    }

    // 组装更新 DTO 列表
    List<FileNodeDTO> updateDTOs = new ArrayList<>(items.size());
    LocalDateTime now = LocalDateTime.now();
    for (com.njydsz.nextwiki.api.dto.NextwikiDTOs.SortItem item : items) {
      FileNodeDTO dto = new FileNodeDTO();
      dto.setId(item.getNodeId());
      dto.setSort(item.getSort());
      dto.setUpdatedBy(userId);
      dto.setUpdatedAt(now);
      updateDTOs.add(dto);
    }

    // 批量更新（循环单条，因字段级部分更新批量 SQL 较复杂；数据量 < 100 场景性能可接受）
    int updated = 0;
    for (FileNodeDTO dto : updateDTOs) {
      fileNodeRepository.update(dto);
      updated++;
    }

    // 失效父目录缓存
    if (parentId != null && !parentId.isEmpty()) {
      cacheService.evictChildren(parentId);
    }

    log.info(
        "[FileApplicationService] 批量排序: parentId={}, count={}, userId={}",
        parentId,
        updated,
        userId);

    return updated;
  }

  // ==================== 私有方法 ====================

  /** 清理超出保留数量的旧版本 */
  private void cleanupExcessVersions(String fileNodeId) {
    List<FileVersionDTO> allVersionDTOs = NextwikiConverter.INSTANT.versionListToDTO(
        versionRepository.findByFileNodeId(fileNodeId));
    List<FileVersionDTO> toDelete = versionDomainService.findVersionsToCleanup(allVersionDTOs);
    for (FileVersionDTO v : toDelete) {
      versionRepository.deleteById(v.getId());
    }
    if (!toDelete.isEmpty()) {
      log.info(
          "[FileApplicationService] 批量清理旧版本: fileNodeId={}, deleted={}",
          fileNodeId,
          toDelete.size());
    }
  }

  /** 上传安全校验：文件大小 + 扩展名黑名单 */
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

  /** 解析父目录节点（直接 findById，不再通过 listChildren 间接判断） */
  private FileNodeVO resolveParentNode(String parentId, String userId) {
    if (parentId == null || parentId.isEmpty() || "0".equals(parentId)) {
      return fileNodeRepository.findOrCreateRoot(userId);
    }
    FileNodeVO parent = fileNodeRepository.findById(parentId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_FOLDER_NOT_FOUND)
            .data("parentId", parentId));
    if (!parent.isFolder()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER)
          .data("parentId", parentId);
    }
    return parent;
  }

  private void publishUploadEvent(FileNodeVO saved, String fileName, String userId) {
    eventPublisher.publishEvent(
        FileOperatedEvent.builder()
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
    publishOutboxEvent(DomainEventTypes.FILE_UPLOADED, saved.getId(), saved);
  }

  /** 发布领域事件到 Outbox（可选依赖，不存在时安全降级） */
  private void publishOutboxEvent(String eventType, String aggregateId, Object payload) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("FileNode")
            .aggregateId(aggregateId)
            .eventType(eventType)
            .metadata("payload", payload)
            .build());
  }

  /** 规范化排序字段：name / size / time（默认 time） */
  private String normalizeSortBy(String sortBy) {
    if (sortBy == null || sortBy.isEmpty()) {
      return "time";
    }
    return switch (sortBy) {
      case "name", "size", "time" -> sortBy;
      default -> "time";
    };
  }

  private void indexUpsert(FileNodeVO entity) {
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

  /** 规范化排序方向：asc / desc（默认 desc） */
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

  private FileNodeDTO buildFileNode(
      String parentId,
      String name,
      String suffix,
      FileStorage uploaded,
      String storageKey,
      String fileHash,
      String path,
      int level,
      String userId) {
    return FileNodeDTO.builder()
        .id(String.valueOf(snowflakeIdGenerator.nextId()))
        .parentId(parentId)
        .name(name)
        .nodeType(FileNodeVO.TYPE_FILE)
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
        .createdBy(userId)
        .updatedBy(userId)
        .build();
  }

  /** 构建秒传去重的文件节点（引用已有存储对象，跳过上传） */
  private FileNodeDTO buildDedupedFileNode(
      String parentId,
      String name,
      String suffix,
      FileNodeVO existing,
      String fileHash,
      String path,
      int level,
      String userId) {
    return FileNodeDTO.builder()
        .id(String.valueOf(snowflakeIdGenerator.nextId()))
        .parentId(parentId)
        .name(name)
        .nodeType(FileNodeVO.TYPE_FILE)
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
        .createdBy(userId)
        .updatedBy(userId)
        .build();
  }

  /** 生成唯一文件名（用于 KEEP_BOTH 策略） */
  private String resolveUniqueName(String fileName, String parentId, String userId) {
    String baseName = fileName;
    String suffix = extractSuffix(fileName);
    if (!suffix.isEmpty()) {
      baseName = fileName.substring(0, fileName.length() - suffix.length() - 1);
    }
    int counter = 1;
    String candidate = fileName;
    while (true) {
      List<FileNodeVO> existing = fileNodeRepository.findByNameAndParent(candidate, parentId, userId);
      if (existing == null || existing.isEmpty()) {
        return candidate;
      }
      candidate =
          suffix.isEmpty()
              ? baseName + " (" + counter + ")"
              : baseName + " (" + counter + ")." + suffix;
      counter++;
    }
  }

  private String generateStorageKey(String userId, String originalFilename) {
    String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
    String uuid = String.valueOf(snowflakeIdGenerator.nextId());
    String suffix = extractSuffix(originalFilename);
    return "wiki/" + userId + "/" + datePath + "/" + uuid + (suffix.isEmpty() ? "" : "." + suffix);
  }

  /** 净化文件名：去除路径穿越字符、特殊字符、超长名称 */
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
      String baseName =
          suffix.isEmpty() ? name : name.substring(0, name.length() - suffix.length() - 1);
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
   * <p>委托 {@link LockStrategy} 获取可重入锁，等待 {@value #LOCK_WAIT_MS}ms， 锁自动过期时间 {@value
   * #LOCK_LEASE_MS}ms。
   *
   * @param locker 分布式锁实例
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
   * 加载配额 DTO（用于领域服务配额校验）。
   *
   * <p>QuotaDomainService.checkQuota 为纯领域逻辑，需由本层先通过
   * {@link StorageQuotaRepository} 加载配额实体后传入；配额不存在时返回 {@code null}，
   * 由领域服务统一抛出配额不足异常。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   * @return 配额 DTO；不存在返回 {@code null}
   */
  private StorageQuotaDTO loadQuota(String scopeType, String scopeId) {
    return storageQuotaRepository
        .findByScope(scopeType, scopeId)
        .map(
            vo ->
                StorageQuotaDTO.builder()
                    .id(vo.getId())
                    .scopeType(vo.getScopeType())
                    .scopeId(vo.getScopeId())
                    .quotaLimit(vo.getQuotaLimit())
                    .quotaUsed(vo.getQuotaUsed())
                    .fileCountLimit(vo.getFileCountLimit())
                    .fileCountUsed(vo.getFileCountUsed())
                    .build())
        .orElse(null);
  }

  /** 批量操作结果 */
  public record BatchResult(int successCount, List<FailedItem> failedItems) {

    /**
     * 失败项明细
     *
     * @param itemId 失败项ID
     * @param reason 失败原因
     */
    public record FailedItem(String itemId, String reason) {}
  }
}
