package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.callback.UploadProgressListener;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.domain.ChunkedUploadResult;
import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.domain.UploadCheckpoint;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件上传委托类
 *
 * <p>从 {@link AbstractFileStorage} 中提取的上传相关操作，
 * 包括普通上传、分片上传、断点续传等。
 *
 * <p>通过持有 {@link AbstractFileStorage} 实例来访问基类的
 * protected 方法和抽象方法（如 resolveBucketName、doPutObject 等）。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class FileUploadDelegate {

    /**
     * 分片上下文 TTL（24 小时）
     */
    private static final long MULTIPART_CONTEXT_TTL_SECONDS = 24 * 3600;

    /**
     * 检查点 TTL（24 小时）
     */
    private static final long CHECKPOINT_TTL_SECONDS = 24 * 3600;

    /** 文件存储抽象层实例，用于访问基类的 protected 方法和抽象方法 */
    private final AbstractFileStorage storage;
    /** 文件存储基础配置 */
    private final FileProperties fileProperties;

    /** 分片上传上下文存储 */
    private MultipartContextStore multipartContextStore;
    /** 断点续传检查点存储 */
    private CheckpointStore checkpointStore;

    /**
     * 上传频率限制计数器
     * <p>Key: 时间窗口（秒级时间戳），Value: 该窗口内的上传次数
     */
    private final ConcurrentHashMap<Long, AtomicInteger> uploadCounters = new ConcurrentHashMap<>();

    /**
     * 并发上传保护器（可选）
     */
    private UploadConcurrencyGuard concurrencyGuard;

    /**
     * 构造文件上传委托类
     * <p>默认使用内存实现作为分片上下文存储，本地文件作为检查点存储（向后兼容），
     * 可通过 setter 注入分布式实现。
     *
     * @param storage        文件存储抽象层实例
     * @param fileProperties 文件存储基础配置
     */
    public FileUploadDelegate(AbstractFileStorage storage, FileProperties fileProperties) {
        this.storage = storage;
        this.fileProperties = fileProperties;
        // 默认使用内存实现（向后兼容），可通过 setter 注入分布式实现
        this.multipartContextStore = new InMemoryMultipartContextStore();
        this.checkpointStore = new LocalCheckpointStore(fileProperties.getCheckpointDir());
    }

    /**
     * 设置分片上传上下文存储
     */
    public void setMultipartContextStore(MultipartContextStore store) {
        if (store != null) {
            this.multipartContextStore = store;
        }
    }

    /**
     * 设置检查点存储
     */
    public void setCheckpointStore(CheckpointStore store) {
        if (store != null) {
            this.checkpointStore = store;
        }
    }

    /**
     * 设置并发上传保护器
     */
    public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
        this.concurrencyGuard = guard;
    }

    /**
     * 上传文件（无进度监听）
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径），为空时自动生成
     * @param file       上传的文件
     * @return 文件存储实体
     * @throws BusinessException 文件为空、上传失败等异常
     */
    public FileStorage upload(String bucketName, String objectName, MultipartFile file) {
        return upload(bucketName, objectName, file, null);
    }

    /**
     * 上传文件（带进度监听）
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径），为空时自动生成
     * @param file       上传的文件
     * @param listener   上传进度监听器，可为 null
     * @return 文件存储实体
     * @throws BusinessException 文件为空、上传失败等异常
     */
    public FileStorage upload(String bucketName, String objectName, MultipartFile file, UploadProgressListener listener) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        checkUploadRateLimit();

        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);

        // 获取并发上传锁
        String lockToken = acquireConcurrencyLock(resolvedKey);

        storage.makeBucket(resolvedBucket);

        FileStorage fileStorage = storage.buildFileStorage(file);
        long totalBytes = file.getSize();

        if (listener != null) {
            listener.onStart(totalBytes);
        }

        try (InputStream inputStream = file.getInputStream()) {
            long contentLength = file.getSize();
            String contentType = file.getContentType();

            storage.doPutObject(resolvedBucket, resolvedKey, inputStream, contentLength, contentType);

            fileStorage.setUuidName(resolvedKey);
            fileStorage.setUrl(storage.buildObjectUrl(resolvedBucket, resolvedKey));

            if (listener != null) {
                listener.onSuccess(resolvedKey);
            }

            log.info("[Storage] file upload success, bucket={}, object={}, size={}", resolvedBucket, resolvedKey, contentLength);
        } catch (BusinessException e) {
            if (listener != null) {
                listener.onFailure(resolvedKey, e);
            }
            throw e;
        } catch (Exception e) {
            log.error("[Storage] file upload failed, bucket={}, object={}, error={}", resolvedBucket, resolvedKey, e.getMessage(), e);
            if (listener != null) {
                listener.onFailure(resolvedKey, e);
            }
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        } finally {
            releaseConcurrencyLock(resolvedKey, lockToken);
        }

        return fileStorage;
    }

    /**
     * 发起分片上传
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径），为空时自动生成
     * @return 分片上传结果，包含 uploadId 等信息
     * @throws BusinessException 发起失败时抛出
     */
    public ChunkedUploadResult initiateChunkedUpload(String bucketName, String objectName) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);

        storage.makeBucket(resolvedBucket);

        ChunkedUploadResult result = storage.doInitiateMultipartUpload(resolvedBucket, resolvedKey);
        multipartContextStore.save(result.getUploadId(),
                new MultipartContextStore.MultipartContextData(result.getUploadId(), resolvedBucket, resolvedKey),
                MULTIPART_CONTEXT_TTL_SECONDS);

        log.info("[Storage] chunked upload initiated, bucket={}, object={}, uploadId={}", resolvedBucket, resolvedKey, result.getUploadId());
        return result;
    }

    /**
     * 上传单个分片
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径），为空时自动生成
     * @param uploadId   分片上传会话 ID
     * @param partNumber 分片序号（从 1 开始）
     * @param file       分片文件内容
     * @throws BusinessException uploadId 无效或上传失败时抛出
     */
    public void uploadChunk(String bucketName, String objectName, String uploadId, int partNumber, MultipartFile file) {
        validateUploadId(uploadId);
        validatePartNumber(partNumber);

        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);

        try (InputStream inputStream = file.getInputStream()) {
            String chunkObjectName = storage.buildChunkObjectName(resolvedKey, uploadId, partNumber);
            storage.doUploadPart(resolvedBucket, chunkObjectName, uploadId, partNumber, inputStream, file.getSize());

            MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
            if (context == null) {
                context = new MultipartContextStore.MultipartContextData(uploadId, resolvedBucket, resolvedKey);
            }
            Map<Integer, String> partChunkNames = new ConcurrentHashMap<>(context.partChunkNames());
            partChunkNames.put(partNumber, chunkObjectName);
            multipartContextStore.save(uploadId,
                    new MultipartContextStore.MultipartContextData(
                            context.uploadId(), context.bucketName(), context.objectName(),
                            partChunkNames, context.createTime(), System.currentTimeMillis()),
                    MULTIPART_CONTEXT_TTL_SECONDS);

            log.debug("[Storage] chunk uploaded, bucket={}, object={}, part={}", resolvedBucket, resolvedKey, partNumber);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] uploadChunk failed, bucket={}, object={}, part={}, error={}", resolvedBucket, resolvedKey, partNumber, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 完成分片上传，合并所有已上传的分片
     *
     * @param bucketName  存储桶名称，为空时使用默认桶
     * @param objectName  对象键（文件路径），为空时自动生成
     * @param uploadId    分片上传会话 ID
     * @param partNumbers 已上传的分片序号列表
     * @throws BusinessException uploadId 无效、分片缺失或合并失败时抛出
     */
    public void completeChunkedUpload(String bucketName, String objectName, String uploadId, List<Integer> partNumbers) {
        validateUploadId(uploadId);
        validatePartNumbers(partNumbers);

        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);

        MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
        if (context == null || !resolvedKey.equals(context.objectName())) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }

        Set<Integer> uniqueParts = new HashSet<>(partNumbers);
        List<Integer> sortedParts = new ArrayList<>(uniqueParts);
        Collections.sort(sortedParts);

        List<IFileStorage.PartInfo> uploadedParts = storage.listParts(resolvedBucket, resolvedKey, uploadId);
        for (Integer partNumber : sortedParts) {
            boolean found = uploadedParts.stream()
                    .anyMatch(p -> p.partNumber() == partNumber);
            if (!found) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
            }
        }

        try {
            storage.doCompleteMultipartUpload(resolvedBucket, resolvedKey, uploadId, sortedParts);
            storage.safeAbortMultipartUpload(resolvedBucket, resolvedKey, uploadId);
            multipartContextStore.remove(uploadId);
            log.info("[Storage] chunked upload completed, bucket={}, object={}, parts={}", resolvedBucket, resolvedKey, sortedParts.size());
        } catch (BusinessException e) {
            storage.safeAbortMultipartUpload(resolvedBucket, resolvedKey, uploadId);
            multipartContextStore.remove(uploadId);
            throw e;
        } catch (Exception e) {
            storage.safeAbortMultipartUpload(resolvedBucket, resolvedKey, uploadId);
            multipartContextStore.remove(uploadId);
            log.error("[Storage] completeChunkedUpload failed, bucket={}, object={}, error={}", resolvedBucket, resolvedKey, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    /**
     * 发起带断点续传的分片上传
     * <p>若已存在有效检查点，则尝试恢复上次上传进度；否则创建新的分片上传会话。
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径），为空时自动生成
     * @param file       上传的文件（用于校验文件大小一致性）
     * @return 上传检查点，包含 uploadId、已上传分片等信息
     * @throws BusinessException 文件为空或发起失败时抛出
     */
    public UploadCheckpoint initChunkedUploadWithCheckpoint(String bucketName, String objectName, MultipartFile file) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        storage.makeBucket(resolvedBucket);

        UploadCheckpoint existingCheckpoint = loadCheckpoint(resolvedBucket, resolvedKey);

        if (existingCheckpoint != null && existingCheckpoint.getUploadId() != null) {
            UploadCheckpoint loadedCheckpoint = validateAndRecoverCheckpoint(existingCheckpoint, file);
            if (loadedCheckpoint != null) {
                log.info("[Storage] recovered existing checkpoint, bucket={}, object={}, uploadId={}, uploadedParts={}",
                        resolvedBucket, resolvedKey, loadedCheckpoint.getUploadId(), loadedCheckpoint.getUploadedPartsCount());
                return loadedCheckpoint;
            }
        }

        ChunkedUploadResult chunkResult = initiateChunkedUpload(resolvedBucket, resolvedKey);

        long fileSize = file.getSize();
        long partSize = fileProperties.getPartSize() != null ? fileProperties.getPartSize() : 5242880L;

        UploadCheckpoint checkpoint = new UploadCheckpoint();
        checkpoint.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        checkpoint.setBucketName(resolvedBucket);
        checkpoint.setObjectName(resolvedKey);
        checkpoint.setUploadId(chunkResult.getUploadId());
        checkpoint.setTotalSize(fileSize);
        checkpoint.setFileName(file.getOriginalFilename());
        checkpoint.setContentType(file.getContentType());
        checkpoint.setPartSize(partSize);
        checkpoint.setCreateTime(LocalDateTime.now());
        checkpoint.setLastModifyTime(LocalDateTime.now());
        checkpoint.setUploadedBytes(0L);
        checkpoint.setUploadedPartsCount(0);
        checkpoint.setUploadedParts(new ArrayList<>());

        saveCheckpoint(checkpoint);

        log.info("[Storage] chunked upload with checkpoint initiated, bucket={}, object={}, uploadId={}, totalParts={}",
                resolvedBucket, resolvedKey, chunkResult.getUploadId(), checkpoint.getUploadedPartsCount());

        return checkpoint;
    }

    /**
     * 断点续传：根据检查点完成剩余分片上传
     * <p>直接调用 completeChunkedUpload 合并已上传的分片，适用于所有分片已上传完毕的场景。
     *
     * @param checkpoint 上传检查点
     * @param listener   上传进度监听器，可为 null
     * @return 文件存储实体
     * @throws BusinessException 检查点无效或合并失败时抛出
     */
    public FileStorage resumeChunkedUpload(UploadCheckpoint checkpoint, UploadProgressListener listener) {
        if (checkpoint == null || StringUtils.isBlank(checkpoint.getUploadId())) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }

        String bucketName = checkpoint.getBucketName();
        String objectName = checkpoint.getObjectName();
        String uploadId = checkpoint.getUploadId();

        if (listener != null) {
            listener.onStart(checkpoint.getTotalSize());
        }

        try {
            long uploadedBytes = checkpoint.getUploadedBytes() != null ? checkpoint.getUploadedBytes() : 0;
            if (listener != null) {
                listener.onProgress(uploadedBytes, checkpoint.getTotalSize());
            }

            completeChunkedUpload(bucketName, objectName, uploadId,
                    checkpoint.getUploadedParts().stream()
                            .map(UploadCheckpoint.UploadedPart::getPartNumber)
                            .sorted()
                            .toList());

            deleteCheckpoint(checkpoint);

            FileStorage fileStorage = new FileStorage();
            fileStorage.setUuidName(objectName);
            fileStorage.setUrl(storage.buildObjectUrl(bucketName, objectName));
            fileStorage.setSize(checkpoint.getTotalSize());
            fileStorage.setFileName(checkpoint.getFileName());

            if (listener != null) {
                listener.onSuccess(objectName);
            }

            return fileStorage;
        } catch (Exception e) {
            if (listener != null) {
                listener.onFailure(objectName, e);
            }
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    /**
     * 获取指定对象的断点续传检查点
     *
     * @param bucketName 存储桶名称，为空时使用默认桶
     * @param objectName 对象键（文件路径），为空时自动生成
     * @return 检查点信息，不存在时返回 null
     */
    public UploadCheckpoint getCheckpoint(String bucketName, String objectName) {
        String resolvedBucket = storage.resolveBucketName(bucketName);
        String resolvedKey = storage.resolveObjectKey(resolvedBucket, objectName);
        return loadCheckpoint(resolvedBucket, resolvedKey);
    }

    /**
     * 删除断点续传检查点
     *
     * @param checkpoint 要删除的检查点，为 null 时不执行操作
     */
    public void deleteCheckpoint(UploadCheckpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        checkpointStore.remove(checkpoint.getBucketName(), checkpoint.getObjectName());
    }

    /**
     * 从存储加载检查点
     */
    private UploadCheckpoint loadCheckpoint(String bucketName, String objectName) {
        try {
            String json = checkpointStore.get(bucketName, objectName);
            if (json != null) {
                return JsonUtils.fromJson(json, UploadCheckpoint.class);
            }
        } catch (Exception e) {
            log.warn("[Storage] loadCheckpoint failed, bucket={}, object={}, error={}",
                    bucketName, objectName, e.getMessage());
        }
        return null;
    }

    /**
     * 保存检查点
     */
    private void saveCheckpoint(UploadCheckpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        try {
            String json = JsonUtils.toJson(checkpoint);
            checkpointStore.save(checkpoint.getBucketName(), checkpoint.getObjectName(), json, CHECKPOINT_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[Storage] saveCheckpoint failed, bucket={}, object={}, error={}",
                    checkpoint.getBucketName(), checkpoint.getObjectName(), e.getMessage());
        }
    }

    /**
     * 验证并恢复检查点
     * @return 有效的检查点，若检查点无效则返回 null
     */
    private UploadCheckpoint validateAndRecoverCheckpoint(UploadCheckpoint checkpoint, MultipartFile file) {
        if (checkpoint.getTotalSize() == null || !checkpoint.getTotalSize().equals(file.getSize())) {
            log.warn("[Storage] checkpoint totalSize mismatch, expected={}, actual={}",
                    checkpoint.getTotalSize(), file.getSize());
            deleteCheckpoint(checkpoint);
            return null;
        }

        String uploadId = checkpoint.getUploadId();
        if (StringUtils.isBlank(uploadId)) {
            return null;
        }

        try {
            List<IFileStorage.PartInfo> existingParts = storage.listParts(checkpoint.getBucketName(), checkpoint.getObjectName(), uploadId);
            if (existingParts.isEmpty()) {
                deleteCheckpoint(checkpoint);
                return null;
            }

            List<UploadCheckpoint.UploadedPart> recoveredParts = new ArrayList<>();
            long uploadedBytes = 0;
            for (IFileStorage.PartInfo part : existingParts) {
                UploadCheckpoint.UploadedPart uploadedPart = new UploadCheckpoint.UploadedPart();
                uploadedPart.setPartNumber(part.partNumber());
                uploadedPart.setSize(part.size());
                uploadedPart.setETag(part.eTag());
                uploadedPart.setUploaded(true);
                recoveredParts.add(uploadedPart);
                uploadedBytes += part.size();
            }

            checkpoint.setUploadedParts(recoveredParts);
            checkpoint.setUploadedBytes(uploadedBytes);
            checkpoint.setUploadedPartsCount(recoveredParts.size());
            checkpoint.setLastModifyTime(LocalDateTime.now());
            saveCheckpoint(checkpoint);
            return checkpoint;
        } catch (Exception e) {
            log.warn("[Storage] validateAndRecoverCheckpoint failed, uploadId={}, error={}",
                    uploadId, e.getMessage());
            deleteCheckpoint(checkpoint);
            return null;
        }
    }

    private void validateUploadId(String uploadId) {
        if (StringUtils.isBlank(uploadId) || uploadId.length() > 64) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    private void validatePartNumber(int partNumber) {
        if (partNumber <= 0) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    /**
     * 校验分片序号列表的合法性
     *
     * @param partNumbers 分片序号列表
     * @throws BusinessException 列表为空、包含 null 或序号小于等于 0 时抛出
     */
    private void validatePartNumbers(List<Integer> partNumbers) {
        if (partNumbers == null || partNumbers.isEmpty()) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
        for (Integer partNumber : partNumbers) {
            if (partNumber == null || partNumber <= 0) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
            }
        }
    }

    /**
     * 检查上传频率限制
     *
     * <p>基于滑动窗口计数器实现，每 60 秒为一个窗口。
     * 当窗口内上传次数超过 maxUploadsPerMinute 时拒绝上传。
     */
    private void checkUploadRateLimit() {
        int maxUploadsPerMinute = fileProperties.getRateLimit().getMaxUploadsPerMinute();
        if (maxUploadsPerMinute <= 0) {
            return;
        }

        long currentWindow = System.currentTimeMillis() / 60_000;
        AtomicInteger counter = uploadCounters.computeIfAbsent(currentWindow, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        // 清理过期窗口（2分钟前的）
        uploadCounters.keySet().removeIf(window -> window < currentWindow - 1);

        if (currentCount > maxUploadsPerMinute) {
            log.warn("[Storage] upload rate limit exceeded, window={}, count={}, limit={}",
                    currentWindow, currentCount, maxUploadsPerMinute);
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 获取并发上传锁
     *
     * @param objectKey 文件对象键
     * @return 锁令牌，用于释放锁
     */
    private String acquireConcurrencyLock(String objectKey) {
        if (concurrencyGuard != null) {
            return concurrencyGuard.acquire(objectKey);
        }
        return null;
    }

    /**
     * 释放并发上传锁
     *
     * @param objectKey  文件对象键
     * @param lockToken  锁令牌
     */
    private void releaseConcurrencyLock(String objectKey, String lockToken) {
        if (concurrencyGuard != null && lockToken != null) {
            try {
                concurrencyGuard.release(objectKey, lockToken);
            } catch (Exception e) {
                log.warn("[Storage] releaseConcurrencyLock failed, object={}, error={}", objectKey, e.getMessage());
            }
        }
    }
}
