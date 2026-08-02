package com.njydsz.nextwiki.server.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.json.YdszJson;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.nextwiki.domain.service.FolderDomainService;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
/**
 * 分片上传应用服务
 * <p>
 * 支持大文件分片上传（初始化→上传分片→完成合并→取消），支持断点续传。
 *
 * <p><b>流程：</b>
 * <ol>
 *   <li>初始化：生成 uploadId，记录文件元数据到 Redis</li>
 *   <li>上传分片：将分片写入临时目录，记录已上传分片号</li>
 *   <li>完成合并：按分片顺序合并为完整文件，上传到存储，创建 FileNode</li>
 *   <li>取消：清理临时文件和 Redis 记录</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class ChunkUploadApplicationService
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChunkUploadApplicationService.class); {

    private final RedisService redisService;
    private final FileNodeRepository fileNodeRepository;
    private final QuotaDomainService quotaDomainService;
    private final FileVersionDomainService versionDomainService;
    private final FolderDomainService folderDomainService;
    private final ApplicationEventPublisher eventPublisher;
    private final NextwikiProperties properties;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /** 单个分片最大大小（默认 10MB） */
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;

    /** 最大分片数 */
    private static final int MAX_CHUNKS = 1000;

    /** 上传会话 Redis Key 前缀 */
    private static final String KEY_UPLOAD_SESSION = "nextwiki:chunk:session:";
    private static final String KEY_UPLOADED_CHUNKS = "nextwiki:chunk:uploaded:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    public ChunkUploadApplicationService(RedisService redisService,
                                          FileNodeRepository fileNodeRepository,
                                          QuotaDomainService quotaDomainService,
                                          FileVersionDomainService versionDomainService,
                                          FolderDomainService folderDomainService,
                                          ApplicationEventPublisher eventPublisher,
                                          NextwikiProperties properties) {
        this.redisService = redisService;
        this.fileNodeRepository = fileNodeRepository;
        this.quotaDomainService = quotaDomainService;
        this.versionDomainService = versionDomainService;
        this.folderDomainService = folderDomainService;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * 初始化分片上传会话。
     * <p>生成 {@code uploadId}，校验配额与总分片数上限（{@link #MAX_CHUNKS}），
     * 将元数据写入 Redis（{@code KEY_UPLOAD_SESSION} 前缀，TTL {@link #SESSION_TTL}）以支持断点续传。
     *
     * @param fileName    原始文件名（含后缀），用于合并后落库
     * @param fileSize    文件总大小（字节），用于配额预校验
     * @param totalChunks 总分片数，超过 {@link #MAX_CHUNKS} 直接拒绝
     * @param parentId    目标父目录节点 ID（{@code null}/"0" 视为根目录）
     * @param userId      操作人 ID，用于配额归属与审计
     * @return 初始化结果 {@link ChunkUploadInit}，含 {@code uploadId}、总分片数与单分片大小
     * @throws BusinessException 分片数超限（FILE_TOO_LARGE）或配额不足时抛出
     * @complexity O(1)（仅 Redis 写入 + 配额校验）
     * @note 无数据库写；会话状态存于 Redis，依赖 {@link #SESSION_TTL} 自动过期清理
     */
    public ChunkUploadInit initChunkUpload(String fileName, long fileSize, int totalChunks,
                                            String parentId, String userId) {
        if (totalChunks > MAX_CHUNKS) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_TOO_LARGE)
                    .data("maxChunks", MAX_CHUNKS);
        }

        // 配额校验
        quotaDomainService.checkQuota("user", userId, fileSize);

        String uploadId = UUID.randomUUID().toString().replace("-", "");

        ChunkUploadSession session = new ChunkUploadSession();
        session.setUploadId(uploadId);
        session.setFileName(fileName);
        session.setFileSize(fileSize);
        session.setTotalChunks(totalChunks);
        session.setParentId(parentId);
        session.setUserId(userId);
        session.setCreatedAt(LocalDateTime.now().toString());

        redisService.set(KEY_UPLOAD_SESSION + uploadId, session.toJson(), SESSION_TTL);

        log.info("[ChunkUploadApplicationService] 初始化分片上传: uploadId={}, fileName={}, totalChunks={}",
                uploadId, fileName, totalChunks);
        return ChunkUploadInit.builder()
                .uploadId(uploadId)
                .totalChunks(totalChunks)
                .chunkSize(MAX_CHUNK_SIZE)
                .build();
    }

    /**
     * 上传单个分片并落盘到临时目录。
     * <p>先校验会话有效性、分片非空与单分片大小（{@link #MAX_CHUNK_SIZE}），
     * 写入磁盘后将分片号加入 Redis 已上传集合（{@code KEY_UPLOADED_CHUNKS}）。
     *
     * @param uploadId    初始化时返回的会话 ID
     * @param chunkNumber 当前分片序号（从 1 开始）
     * @param chunk       分片文件内容
     * @throws BusinessException 会话不存在、分片为空或超限时抛出
     * @complexity O(1)（磁盘写入 + Redis 集合追加）
     * @concurrency 多个分片可并发上传，最终由 {@link #completeChunkUpload} 串行按序合并
     * @note 仅依赖会话 Redis 状态，单分片幂等可重传（覆盖写同名临时文件）
     */
    public void uploadChunk(String uploadId, int chunkNumber, MultipartFile chunk) {
        validateSession(uploadId);

        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException(NextwikiExceptionCode.FILE_UPLOAD_EMPTY);
        }
        if (chunk.getSize() > MAX_CHUNK_SIZE) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_TOO_LARGE)
                    .data("maxChunkSize", MAX_CHUNK_SIZE / 1024 / 1024 + "MB");
        }

        try {
            Path chunkFile = getChunkPath(uploadId, chunkNumber);
            Files.createDirectories(chunkFile.getParent());
            chunk.transferTo(chunkFile);

            // 记录已上传分片
            redisService.sAdd(KEY_UPLOADED_CHUNKS + uploadId, String.valueOf(chunkNumber));
            redisService.expire(KEY_UPLOADED_CHUNKS + uploadId, SESSION_TTL);

            log.debug("[ChunkUploadApplicationService] 分片上传成功: uploadId={}, chunk={}", uploadId, chunkNumber);
        } catch (IOException e) {
            log.error("[ChunkUploadApplicationService] 分片上传失败: uploadId={}, chunk={}", uploadId, chunkNumber, e);
            throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 完成分片上传：校验完整性 → 合并分片 → 上传存储 → 建节点（含秒传去重）。
     * <p>先核对已上传分片数与声明总数一致，再按序合并临时分片、计算 SHA-256 做秒传去重，
     * 命中已有文件则复用其存储键（仅新增节点与配额）；否则上传对象存储并创建 FileNode。
     * 成功后发布上传事件（驱动内容提取/索引/审计），并在 {@code finally} 清理临时文件与 Redis 会话。
     *
     * @param uploadId 会话 ID
     * @param userId   操作人 ID，用于配额与审计归属
     * @return 新建/去重的文件节点视图 {@link FileNodeVO}
     * @throws BusinessException 分片不完整、存储未配置或合并失败（FILE_DOWNLOAD_FAILED）时抛出
     * @transaction 整个方法 {@code @Transactional(rollbackFor = Exception.class)}，节点落库与配额/版本变更同事务
     * @complexity O(totalChunks)（顺序合并）+ 一次存储上传 + 一次 SHA-256 全量读取
     * @concurrency 同一 {@code uploadId} 应串行完成，避免并发合并竞争；分片上传阶段可并发
     * @note 方法结束后强制清理临时分片与 Redis 会话，确保不残留孤儿文件
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNodeVO completeChunkUpload(String uploadId, String userId) {
        ChunkUploadSession session = validateSession(uploadId);

        // 检查所有分片是否已上传
        Set<String> uploaded = redisService.sMembers(KEY_UPLOADED_CHUNKS + uploadId, String.class);
        if (uploaded == null || uploaded.size() < session.getTotalChunks()) {
            throw BusinessException.of(NextwikiExceptionCode.CHUNK_INCOMPLETE)
                    .data("uploaded", uploaded != null ? uploaded.size() : 0)
                    .data("total", session.getTotalChunks());
        }

        // 合并分片
        Path mergedFile = getMergedPath(uploadId, session.getFileName());
        try {
            Files.createDirectories(mergedFile.getParent());
            try (OutputStream os = Files.newOutputStream(mergedFile)) {
                for (int i = 1; i <= session.getTotalChunks(); i++) {
                    Path chunkFile = getChunkPath(uploadId, i);
                    Files.copy(chunkFile, os);
                }
            }

            long actualSize = Files.size(mergedFile);
            if (actualSize != session.getFileSize()) {
                log.warn("[ChunkUploadApplicationService] 合并文件大小不匹配: expected={}, actual={}",
                        session.getFileSize(), actualSize);
            }

            // 上传到存储
            IFileStorage storage = resolveStorage();
            if (storage == null) {
                throw new BusinessException(NextwikiExceptionCode.FILE_STORAGE_NOT_CONFIGURED);
            }

            // P0-R2: 计算 SHA-256 哈希（用于秒传去重）
            String fileHash = calculateSha256(mergedFile);

            // P0-R2: 秒传去重检查
            if (fileHash != null) {
                FileNode existing = fileNodeRepository.findByFileHash(fileHash);
                if (existing != null) {
                    log.info("[ChunkUploadApplicationService] 秒传命中: hash={}, existingId={}",
                            fileHash, existing.getId());
                    FileNode deduped = buildDedupedNode(session, existing, fileHash, userId);
                    FileNode saved = fileNodeRepository.save(deduped);
                    versionDomainService.createVersion(saved.getId(), existing.getStorageKey(),
                            existing.getSize(), fileHash, existing.getMimeType(), "秒传", userId);
                    quotaDomainService.addUsage("user", userId, existing.getSize(), 1);
                    publishUploadEvent(saved, userId);
                    return FileNodeVO.builder().id(saved.getId()).name(saved.getName())
                            .nodeType(saved.getNodeType()).size(saved.getSize()).build();
                }
            }

            String storageKey = generateStorageKey(userId, session.getFileName());
            MultipartFile multipartFile = createMultipartFile(
                    mergedFile, session.getFileName());
            FileStorage stored = storage.upload(null, storageKey, multipartFile);

            // P0-R2: 解析父目录获取正确 path/level（不再硬编码 "/" 和 1）
            FileNode parent = resolveParent(session.getParentId(), userId);
            String parentPath = parent.getPath() != null ? parent.getPath() : "/";
            String path = parentPath.endsWith("/")
                    ? parentPath + session.getFileName() + "/"
                    : parentPath + "/" + session.getFileName() + "/";
            int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

            FileNode fileNode = FileNode.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .parentId(parent.getId())
                    .name(session.getFileName())
                    .nodeType(FileNode.TYPE_FILE)
                    .suffix(extractSuffix(session.getFileName()))
                    .size(stored.getSize())
                    .storageKey(storageKey)
                    .bucketName(stored.getUuidName())
                    .mimeType(stored.getMimeType())
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
            fileNode.setCreatedBy(userId);
            fileNode.setUpdatedBy(userId);

            FileNode saved = fileNodeRepository.save(fileNode);

            versionDomainService.createVersion(saved.getId(), storageKey, stored.getSize(),
                    fileHash, stored.getMimeType(), "分片上传", userId);

            quotaDomainService.addUsage("user", userId, stored.getSize(), 1);

            // P0-R2: 发布上传事件（触发内容提取、索引同步、审计日志）
            publishUploadEvent(saved, userId);

            log.info("[ChunkUploadApplicationService] 分片上传完成: uploadId={}, nodeId={}", uploadId, saved.getId());
            return FileNodeVO.builder()
                    .id(saved.getId())
                    .name(saved.getName())
                    .nodeType(saved.getNodeType())
                    .size(saved.getSize())
                    .build();
        } catch (IOException e) {
            log.error("[ChunkUploadApplicationService] 合并失败: uploadId={}", uploadId, e);
            throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
        } finally {
            cleanupChunks(uploadId, session.getTotalChunks(), session.getFileName());
            redisService.delete(KEY_UPLOAD_SESSION + uploadId);
            redisService.delete(KEY_UPLOADED_CHUNKS + uploadId);
        }
    }

    /**
     * 取消分片上传并清理所有临时资源。
     * <p>无论 Redis 会话是否存在都尝试清理临时分片与合并文件及会话键，保证不残留孤儿数据。
     *
     * @param uploadId 会话 ID
     * @return 无返回值
     * @note 幂等：重复取消不会报错（清理失败仅告警日志）
     * @complexity O(totalChunks)（逐文件删除）+ Redis 删除
     */
    public void abortChunkUpload(String uploadId) {
        ChunkUploadSession session = getSession(uploadId);
        if (session != null) {
            cleanupChunks(uploadId, session.getTotalChunks(), session.getFileName());
        } else {
            // session 不存在时仍尝试清理目录
            cleanupChunks(uploadId, 0, null);
        }
        redisService.delete(KEY_UPLOAD_SESSION + uploadId);
        redisService.delete(KEY_UPLOADED_CHUNKS + uploadId);
        log.info("[ChunkUploadApplicationService] 取消分片上传: uploadId={}", uploadId);
    }

    /**
     * 查询已上传分片号集合（客户端用于断点续传，仅重传缺失分片）。
     *
     * @param uploadId 会话 ID
     * @return 已成功上传的分片序号集合；会话不存在时返回空集合（非 {@code null}）
     * @complexity O(1)（Redis 集合读取）
     * @note 只读，无副作用
     */
    public Set<Integer> getUploadedChunks(String uploadId) {
        Set<String> uploaded = redisService.sMembers(KEY_UPLOADED_CHUNKS + uploadId, String.class);
        if (uploaded == null) {
            return new HashSet<>();
        }
        return uploaded.stream().map(Integer::parseInt).collect(Collectors.toSet());
    }

    // ==================== 私有方法 ====================

    private ChunkUploadSession validateSession(String uploadId) {
        ChunkUploadSession session = getSession(uploadId);
        if (session == null) {
            throw BusinessException.of(NextwikiExceptionCode.CHUNK_UPLOAD_NOT_FOUND)
                    .data("uploadId", uploadId);
        }
        return session;
    }

    private ChunkUploadSession getSession(String uploadId) {
        String json = redisService.get(KEY_UPLOAD_SESSION + uploadId, String.class);
        if (json == null) {
            return null;
        }
        return ChunkUploadSession.fromJson(json);
    }

    private Path getChunkPath(String uploadId, int chunkNumber) {
        return Path.of(properties.getUpload().getChunkTempDir(), uploadId, "chunk-" + chunkNumber + ".tmp");
    }

    private Path getMergedPath(String uploadId, String fileName) {
        return Path.of(properties.getUpload().getChunkTempDir(), uploadId, "merged-" + sanitizeFileName(fileName));
    }

    private void cleanupChunks(String uploadId, int totalChunks, String fileName) {
        for (int i = 1; i <= totalChunks; i++) {
            try {
                Files.deleteIfExists(getChunkPath(uploadId, i));
            } catch (IOException e) {
                log.warn("[ChunkUploadApplicationService] 清理分片失败: uploadId={}, chunk={}", uploadId, i);
            }
        }
        // P0-R2: 使用真实文件名清理合并文件（不再用 "merged" 硬编码）
        if (fileName != null) {
            try {
                Files.deleteIfExists(getMergedPath(uploadId, fileName));
            } catch (IOException e) {
                log.warn("[ChunkUploadApplicationService] 清理合并文件失败: uploadId={}", uploadId);
            }
        }
        try {
            Path sessionDir = Path.of(properties.getUpload().getChunkTempDir(), uploadId);
            if (Files.exists(sessionDir)) {
                Files.list(sessionDir).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    }                     } catch (IOException ignored) {
                    }     log.debug("Caught exception (ignored): {}", ignored.getMessage());
                    } }
                });
                Files.deleteIfExists(sessionDir);
            }
        } catch (IOException e) {
            log.warn("[ChunkUploadApplicationService] 清理目录失败: uploadId={}", uploadId);
        }
    }

    private String sanitizeFileName(String filename) {
        if (filename == null) return "unknown";
        return filename.replace("/", "_").replace("\\", "_").replace("..", "_");
    }

    private String extractSuffix(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    private String generateStorageKey(String userId, String originalFilename) {
        String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String suffix = extractSuffix(originalFilename);
        return "wiki/" + userId + "/" + datePath + "/" + uuid + (suffix.isEmpty() ? "" : "." + suffix);
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

    // P0-R2: 新增辅助方法

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

    private String calculateSha256(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[ChunkUploadApplicationService] SHA-256 计算失败: {}", e.getMessage());
            return null;
        }
    }

    private FileNode buildDedupedNode(ChunkUploadSession session, FileNode existing,
                                         String fileHash, String userId) {
        FileNode parent = resolveParent(session.getParentId(), userId);
        String parentPath = parent.getPath() != null ? parent.getPath() : "/";
        String path = parentPath.endsWith("/")
                ? parentPath + session.getFileName() + "/"
                : parentPath + "/" + session.getFileName() + "/";
        int level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;

        FileNode node = FileNode.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .parentId(parent.getId())
                .name(session.getFileName())
                .nodeType(FileNode.TYPE_FILE)
                .suffix(extractSuffix(session.getFileName()))
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

    private void publishUploadEvent(FileNode saved, String userId) {
        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_UPLOAD)
                .fileNodeId(saved.getId())
                .fileName(saved.getName())
                .nodeType(saved.getNodeType())
                .storageKey(saved.getStorageKey())
                .bucketName(saved.getBucketName())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .build());
    }

/**
     * 创建基于 Path 的 MultipartFile（与 PreviewApplicationService 中实现一致）
     */
    private MultipartFile createMultipartFile(Path filePath, String name) throws IOException {
        String contentType = Files.probeContentType(filePath);
        return new SimplePathMultipartFile(filePath, name, contentType != null ? contentType : "application/octet-stream");
    }

    /**
     * 基于 Path 的 MultipartFile 简单实现
     */
    private static class SimplePathMultipartFile implements MultipartFile {
        private final Path filePath;
        private final String name;
        private final String contentType;
        private final long size;

        SimplePathMultipartFile(Path filePath, String name, String contentType) throws IOException {
            this.filePath = filePath;
            this.name = name;
            this.contentType = contentType;
            this.size = Files.size(filePath);
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getOriginalFilename() {
            Path fileName = filePath.getFileName();
            return fileName != null ? fileName.toString() : name;
        }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return size == 0; }

        @Override
        public long getSize() { return size; }

        @Override
        public byte[] getBytes() throws IOException { return Files.readAllBytes(filePath); }

        @Override
        public InputStream getInputStream() throws IOException { return Files.newInputStream(filePath); }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(filePath, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 分片上传初始化结果
     */
    @Data
    @Builder
    public static class ChunkUploadInit {
        /** 上传会话 ID（UUID，作为 Redis 键与续传凭证） */
        private String uploadId;
        /** 总分片数 */
        private int totalChunks;
        /** 单分片大小上限（字节），见 {@link #MAX_CHUNK_SIZE} */
        private long chunkSize;
    }

    /**
     * 分片上传会话（Redis 存储，P0-R4: 改用 YdszJson 序列化替代管道符分隔）。
     * <p>会话承载一次分片上传的全部上下文，TTL 由 {@link #SESSION_TTL} 控制，过期即视为放弃。
     */
    @Data
    public static class ChunkUploadSession {
        /** 上传会话 ID */
        private String uploadId;
        /** 原始文件名（含后缀） */
        private String fileName;
        /** 文件总大小（字节），用于合并后大小校验 */
        private long fileSize;
        /** 总分片数 */
        private int totalChunks;
        /** 目标父目录节点 ID */
        private String parentId;
        /** 操作人 ID（配额/审计归属） */
        private String userId;
        /** 会话创建时间（ISO 本地时间字符串） */
        private String createdAt;

        String toJson() {
            return YdszJson.toJson(this);
        }

        static ChunkUploadSession fromJson(String json) {
            return YdszJson.fromJson(json, ChunkUploadSession.class);
        }
    }
}
