package com.njydsz.nextwiki.server.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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
 * @since 1.4.0
 */
@Slf4j
@Service
public class ChunkUploadApplicationService {

    private final StringRedisTemplate redisTemplate;
    private final FileNodeRepository fileNodeRepository;
    private final QuotaDomainService quotaDomainService;
    private final FileVersionDomainService versionDomainService;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    @Value("${nextwiki.upload.chunk-temp-dir:#{T(java.lang.System).getProperty('java.io.tmpdir') + '/nextwiki-chunk'}}")
    private String chunkTempDir;

    /** 单个分片最大大小（默认 10MB） */
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;

    /** 最大分片数 */
    private static final int MAX_CHUNKS = 1000;

    /** 上传会话 Redis Key 前缀 */
    private static final String KEY_UPLOAD_SESSION = "nextwiki:chunk:session:";
    private static final String KEY_UPLOADED_CHUNKS = "nextwiki:chunk:uploaded:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    public ChunkUploadApplicationService(StringRedisTemplate redisTemplate,
                                          FileNodeRepository fileNodeRepository,
                                          QuotaDomainService quotaDomainService,
                                          FileVersionDomainService versionDomainService) {
        this.redisTemplate = redisTemplate;
        this.fileNodeRepository = fileNodeRepository;
        this.quotaDomainService = quotaDomainService;
        this.versionDomainService = versionDomainService;
    }

    /**
     * 初始化分片上传
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

        redisTemplate.opsForValue().set(KEY_UPLOAD_SESSION + uploadId,
                session.toJson(), SESSION_TTL);

        log.info("[ChunkUploadApplicationService] 初始化分片上传: uploadId={}, fileName={}, totalChunks={}",
                uploadId, fileName, totalChunks);
        return ChunkUploadInit.builder()
                .uploadId(uploadId)
                .totalChunks(totalChunks)
                .chunkSize(MAX_CHUNK_SIZE)
                .build();
    }

    /**
     * 上传单个分片
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
            redisTemplate.opsForSet().add(KEY_UPLOADED_CHUNKS + uploadId, String.valueOf(chunkNumber));
            redisTemplate.expire(KEY_UPLOADED_CHUNKS + uploadId, SESSION_TTL);

            log.debug("[ChunkUploadApplicationService] 分片上传成功: uploadId={}, chunk={}", uploadId, chunkNumber);
        } catch (IOException e) {
            log.error("[ChunkUploadApplicationService] 分片上传失败: uploadId={}, chunk={}", uploadId, chunkNumber, e);
            throw new BusinessException(NextwikiExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 完成分片上传（合并分片并上传到存储）
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public FileNodeVO completeChunkUpload(String uploadId, String userId) {
        ChunkUploadSession session = validateSession(uploadId);

        // 检查所有分片是否已上传
        Set<String> uploaded = redisTemplate.opsForSet().members(KEY_UPLOADED_CHUNKS + uploadId);
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

            String storageKey = generateStorageKey(userId, session.getFileName());
            MultipartFile multipartFile = createMultipartFile(
                    mergedFile, session.getFileName());
            FileStorage stored = storage.upload(null, storageKey, multipartFile);

            // 创建 FileNode
            FileNode fileNode = FileNode.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .parentId(session.getParentId())
                    .name(session.getFileName())
                    .nodeType(FileNode.TYPE_FILE)
                    .suffix(extractSuffix(session.getFileName()))
                    .size(stored.getSize())
                    .storageKey(storageKey)
                    .bucketName(stored.getUuidName())
                    .mimeType(stored.getMimeType())
                    .path("/")
                    .level(1)
                    .sort(0)
                    .currentVersion(0)
                    .previewReady(false)
                    .starred(false)
                    .shareStatus("private")
                    .status("active")
                    .deleted(0)
                    .revision(0)
                    .build();
            fileNode.setCreatedBy(userId);
            fileNode.setCreatedAt(LocalDateTime.now());
            fileNode.setUpdatedBy(userId);
            fileNode.setUpdatedAt(LocalDateTime.now());

            FileNode saved = fileNodeRepository.save(fileNode);

            versionDomainService.createVersion(saved.getId(), storageKey, stored.getSize(),
                    null, stored.getMimeType(), "分片上传", userId);

            quotaDomainService.addUsage("user", userId, stored.getSize(), 1);

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
            cleanupChunks(uploadId, session.getTotalChunks());
            redisTemplate.delete(KEY_UPLOAD_SESSION + uploadId);
            redisTemplate.delete(KEY_UPLOADED_CHUNKS + uploadId);
        }
    }

    /**
     * 取消分片上传
     */
    public void abortChunkUpload(String uploadId) {
        ChunkUploadSession session = getSession(uploadId);
        if (session != null) {
            cleanupChunks(uploadId, session.getTotalChunks());
        }
        redisTemplate.delete(KEY_UPLOAD_SESSION + uploadId);
        redisTemplate.delete(KEY_UPLOADED_CHUNKS + uploadId);
        log.info("[ChunkUploadApplicationService] 取消分片上传: uploadId={}", uploadId);
    }

    /**
     * 查询已上传分片列表（用于断点续传）
     */
    public Set<Integer> getUploadedChunks(String uploadId) {
        Set<String> uploaded = redisTemplate.opsForSet().members(KEY_UPLOADED_CHUNKS + uploadId);
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
        String json = redisTemplate.opsForValue().get(KEY_UPLOAD_SESSION + uploadId);
        if (json == null) {
            return null;
        }
        return ChunkUploadSession.fromJson(json);
    }

    private Path getChunkPath(String uploadId, int chunkNumber) {
        return Path.of(chunkTempDir, uploadId, "chunk-" + chunkNumber + ".tmp");
    }

    private Path getMergedPath(String uploadId, String fileName) {
        return Path.of(chunkTempDir, uploadId, "merged-" + sanitizeFileName(fileName));
    }

    private void cleanupChunks(String uploadId, int totalChunks) {
        for (int i = 1; i <= totalChunks; i++) {
            try {
                Files.deleteIfExists(getChunkPath(uploadId, i));
            } catch (IOException e) {
                log.warn("[ChunkUploadApplicationService] 清理分片失败: uploadId={}, chunk={}", uploadId, i);
            }
        }
        try {
            Files.deleteIfExists(getMergedPath(uploadId, "merged"));
            Path sessionDir = Path.of(chunkTempDir, uploadId);
            if (Files.exists(sessionDir)) {
                Files.list(sessionDir).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
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

/**
     * 创建基于 Path 的 MultipartFile（与 PreviewApplicationService 中实现一致）
     */
    private MultipartFile createMultipartFile(Path filePath, String name) throws IOException {
        String contentType = java.nio.file.Files.probeContentType(filePath);
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
        public java.io.InputStream getInputStream() throws IOException { return Files.newInputStream(filePath); }

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
        private String uploadId;
        private int totalChunks;
        private long chunkSize;
    }

    /**
     * 分片上传会话（Redis 存储）
     */
    @Data
    public static class ChunkUploadSession {
        private String uploadId;
        private String fileName;
        private long fileSize;
        private int totalChunks;
        private String parentId;
        private String userId;
        private String createdAt;

        String toJson() {
            return uploadId + "|" + fileName + "|" + fileSize + "|"
                    + totalChunks + "|" + parentId + "|" + userId + "|" + createdAt;
        }

        static ChunkUploadSession fromJson(String json) {
            String[] parts = json.split("\\|", 7);
            ChunkUploadSession s = new ChunkUploadSession();
            s.uploadId = parts[0];
            s.fileName = parts[1];
            s.fileSize = Long.parseLong(parts[2]);
            s.totalChunks = Integer.parseInt(parts[3]);
            s.parentId = parts.length > 4 ? parts[4] : null;
            s.userId = parts.length > 5 ? parts[5] : null;
            s.createdAt = parts.length > 6 ? parts[6] : null;
            return s;
        }
    }
}
