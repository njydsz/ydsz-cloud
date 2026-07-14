package com.njydsz.pmis.common.file.storage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.callback.UploadProgressListener;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.config.FileUploadProperties;
import com.njydsz.pmis.common.file.constant.FileConstant;
import com.njydsz.pmis.common.file.domain.BatchDeleteResult;
import com.njydsz.pmis.common.file.domain.ChunkedUploadResult;
import com.njydsz.pmis.common.file.domain.FileStorage;
import com.njydsz.pmis.common.file.domain.ListObjectsResult;
import com.njydsz.pmis.common.file.domain.ObjectMetadata;
import com.njydsz.pmis.common.file.domain.PolicyResult;
import com.njydsz.pmis.common.file.domain.UploadCheckpoint;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.file.util.FileTypeValidator;
import com.njydsz.pmis.common.util.string.StringUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件存储抽象基类
 * <p>封装所有存储实现公共逻辑，减少子类重复代码。
 *
 * <p>子类只需实现以下核心抽象方法即可：
 * <ul>
 *   <li>{@link #doBucketExists(String)} - 判断桶是否存在</li>
 *   <li>{@link #doMakeBucket(String)} - 创建桶</li>
 *   <li>{@link #doFolderExists(String, String)} - 判断目录是否存在</li>
 *   <li>{@link #doMakeFolder(String, String)} - 创建目录</li>
 *   <li>{@link #doPutObject(String, String, InputStream, long, String)} - 写入对象</li>
 *   <li>{@link #doGetObject(String, String, Long, Long)} - 读取对象</li>
 *   <li>{@link #doRemoveObject(String, String)} - 删除对象</li>
 *   <li>{@link #buildObjectUrl(String, String)} - 构建对象访问地址</li>
 *   <li>{@link #doInitiateMultipartUpload(String, String)} - 初始化分片上传</li>
 *   <li>{@link #doUploadPart(String, String, String, int, InputStream, long)} - 上传分片</li>
 *   <li>{@link #doCompleteMultipartUpload(String, String, String, List)} - 完成分片上传</li>
 *   <li>{@link #doAbortMultipartUpload(String, String, String)} - 中止分片上传</li>
 *   <li>{@link #listParts(String, String, String)} - 列举已上传分片</li>
 * </ul>
 *
 * <p>公共能力：
 * <ul>
 *   <li>bucketName 默认值解析（子类无需重复实现 formatBucketName）</li>
 *   <li>分片上传参数校验（子类无需重复实现 validateMultipartArgs / validateCompleteParts）</li>
 *   <li>分片合并前服务端校验（确保分片完整性）</li>
 *   <li>失败时自动 abort 清理</li>
 *   <li>进度回调触发（onStart/onProgress/onSuccess/onFailure）</li>
 *   <li>路径穿越防护（resolveObjectKey）</li>
 *   <li>分片上传上下文和检查点使用分布式存储（Redis），支持多实例共享</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see IFileStorage
 */
@Slf4j
public abstract class AbstractFileStorage implements IFileStorage {

    /**
     * 分片临时对象前缀（用于标识临时分片文件）
     */
    protected static final String CHUNK_DIR_PREFIX = ".multipart";

    /**
     * 分片文件名格式
     */
    protected static final String CHUNK_FILE_NAME_FORMAT = "part-%d";

    /**
     * 分片上下文 TTL（24 小时）
     */
    private static final long MULTIPART_CONTEXT_TTL_SECONDS = 24 * 3600;

    /**
     * 检查点 TTL（24 小时）
     */
    private static final long CHECKPOINT_TTL_SECONDS = 24 * 3600;

    /**
     * 存储配置属性
     */
    @Getter
    protected final FileProperties fileProperties;

    /**
     * 默认存储桶名称
     */
    @Getter
    protected final String defaultBucket;

    /**
     * 默认访问域名
     */
    @Getter
    protected final String domain;

    /**
     * 默认端点地址
     */
    @Getter
    protected final String endpoint;

    /**
     * 分片上传上下文存储（底层存储接口）
     */
    protected volatile MultipartContextStore multipartContextStore;

    /**
     * 检查点服务（高层业务封装）
     */
    protected volatile CheckpointService checkpointService;

    /**
     * 分片上传模板（组合方式，避免继承导致类膨胀）
     */
    protected AbstractChunkedUploadTemplate chunkedUploadTemplate;

    /**
     * 并发上传保护器（可选）
     */
    protected UploadConcurrencyGuard concurrencyGuard;

    /**
     * 分片上传配置（可选，为空时不使用 MD5 校验）
     */
    protected FileUploadProperties fileUploadProperties;

    /**
     * 流式 MD5 摘要器（uploadId → MessageDigest），每上传一片就更新摘要。
     * 仅缓存 MessageDigest 状态（约 128 字节），而非原始分片数据，避免大文件 OOM。
     */
    private final ConcurrentHashMap<String, MessageDigest> chunkedMd5DigestMap =
            new ConcurrentHashMap<>();

    protected AbstractFileStorage(FileProperties fileProperties) {
        this(fileProperties, null);
    }

    protected AbstractFileStorage(FileProperties fileProperties, FileUploadProperties fileUploadProperties) {
        this.fileProperties = fileProperties;
        this.fileUploadProperties = fileUploadProperties;
        this.defaultBucket = fileProperties.getBucket();
        this.domain = fileProperties.getDomain();
        this.endpoint = fileProperties.getEndpoint();
        // 默认使用内存/本地文件实现的服务层
        this.multipartContextStore = new InMemoryMultipartContextStore();
        CheckpointStore defaultCheckpointStore = new LocalCheckpointStore(fileProperties.getCheckpointDir());
        // 使用局部数组持有者延迟绑定 this::listParts，避免构造器中 this 逃逸
        final DefaultCheckpointService.MultipartLister[] listerHolder = new DefaultCheckpointService.MultipartLister[1];
        this.checkpointService = new DefaultCheckpointService(defaultCheckpointStore,
                (bucket, object, uploadId) -> {
                    DefaultCheckpointService.MultipartLister lister = listerHolder[0];
                    return lister != null ? lister.listParts(bucket, object, uploadId)
                            : Collections.emptyList();
                },
                CHECKPOINT_TTL_SECONDS);
        listerHolder[0] = this::listParts;
        // 初始化分片上传模板（基于当前实例的 checkpoint 保存/加载能力）
        this.chunkedUploadTemplate = createChunkedUploadTemplate();
    }

    /**
     * 设置分片上传配置
     */
    public void setFileUploadProperties(FileUploadProperties properties) {
        this.fileUploadProperties = properties;
    }

    /**
     * 是否启用分片 MD5 校验
     */
    protected boolean isChunkMd5CheckEnabled() {
        return fileUploadProperties != null && fileUploadProperties.isChunkMd5Check();
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
     * 设置检查点服务
     */
    public void setCheckpointService(CheckpointService service) {
        if (service != null) {
            this.checkpointService = service;
            // 重建模板以使用新的服务
            this.chunkedUploadTemplate = createChunkedUploadTemplate();
        }
    }

    /**
     * 设置分片上传模板
     * <p>子类可注入自定义的模板实现
     */
    public void setChunkedUploadTemplate(AbstractChunkedUploadTemplate template) {
        this.chunkedUploadTemplate = template;
    }

    /**
     * 创建默认的分片上传模板实现
     * <p>基于 checkpointStore 保存/加载检查点数据
     */
    protected final AbstractChunkedUploadTemplate createChunkedUploadTemplate() {
        final AbstractFileStorage self = this;
        return new AbstractChunkedUploadTemplate(CHECKPOINT_TTL_SECONDS * 1000L) {
            @Override
            protected void saveCheckpoint(String bucketName, String objectName, String uploadId,
                                          long partSize, long totalSize, int totalParts,
                                          int completedParts, long expiresAt) {
                // 委托给 AbstractFileStorage 的检查点保存逻辑
                UploadCheckpoint checkpoint = new UploadCheckpoint();
                checkpoint.setUploadId(uploadId);
                checkpoint.setBucketName(bucketName);
                checkpoint.setObjectName(objectName);
                checkpoint.setPartSize(partSize);
                checkpoint.setTotalSize(totalSize);
                checkpoint.setUploadedPartsCount(completedParts);
                self.saveCheckpoint(checkpoint);
            }

            @Override
            protected AbstractChunkedUploadTemplate.ChunkedUploadCheckpoint doLoadCheckpoint(
                    String bucketName, String objectName, String uploadId) {
                UploadCheckpoint loaded = self.loadCheckpoint(bucketName, objectName);
                if (loaded == null) {
                    return null;
                }
                long partSize = loaded.getPartSize() != null ? loaded.getPartSize() : 0;
                long totalSize = loaded.getTotalSize() != null ? loaded.getTotalSize() : 0;
                int totalParts = partSize > 0 && totalSize > 0 ? (int) Math.ceil((double) totalSize / partSize) : 0;
                return new AbstractChunkedUploadTemplate.ChunkedUploadCheckpoint(
                        uploadId,
                        partSize,
                        totalSize,
                        totalParts,
                        loaded.getUploadedPartsCount() != null ? loaded.getUploadedPartsCount() : 0,
                        System.currentTimeMillis() + CHECKPOINT_TTL_SECONDS * 1000L);
            }

            @Override
            protected void deleteCheckpoint(String bucketName, String objectName, String uploadId) {
                UploadCheckpoint checkpoint = new UploadCheckpoint();
                checkpoint.setBucketName(bucketName);
                checkpoint.setObjectName(objectName);
                self.deleteCheckpoint(checkpoint);
            }
        };
    }

    /**
     * 设置并发上传保护器
     */
    public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
        this.concurrencyGuard = guard;
    }

    /**
     * 清理过期的分片上传上下文
     * <p>建议定时调用（如每小时一次）清理超时未完成的上传任务
     *
     * @param timeoutMinutes 超时时间（分钟），超过此时间未更新的上下文将被清理
     */
    public void cleanExpiredMultipartContexts(int timeoutMinutes) {
        MultipartContextStore store = multipartContextStore;
        if (store != null) {
            store.cleanExpired(timeoutMinutes);
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        String resolvedBucket = resolveBucketName(bucketName);
        if (StringUtils.isBlank(resolvedBucket)) {
            return false;
        }
        return doBucketExists(resolvedBucket);
    }

    @Override
    public void makeBucket(String bucketName) {
        String resolvedBucket = resolveBucketName(bucketName);
        if (StringUtils.isBlank(resolvedBucket)) {
            throw new BusinessException(FileExceptionCode.BUCKET_NOT_FOUND);
        }
        if (!bucketExists(resolvedBucket)) {
            doMakeBucket(resolvedBucket);
        }
    }

    @Override
    public boolean folderExists(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return doFolderExists(resolvedBucket, resolvedObjectName);
    }

    @Override
    public boolean objectExists(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        try {
            ObjectMetadata metadata = doGetMetadata(resolvedBucket, resolvedObjectName);
            return metadata != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ObjectMetadata getMetadata(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return doGetMetadata(resolvedBucket, resolvedObjectName);
    }

    @Override
    public void copyObject(String srcBucketName, String srcObjectName, String destBucketName, String destObjectName) {
        String resolvedSrcBucket = resolveBucketName(srcBucketName);
        String resolvedSrcObject = resolveObjectKey(resolvedSrcBucket, srcObjectName);
        String resolvedDestBucket = resolveBucketName(destBucketName);
        String resolvedDestObject = resolveObjectKey(resolvedDestBucket, destObjectName);

        try {
            ObjectMetadata metadata = doGetMetadata(resolvedSrcBucket, resolvedSrcObject);
            if (metadata == null) {
                throw new BusinessException(FileExceptionCode.FILE_NOT_FOUND);
            }
            String contentType = metadata.getContentType() != null ? metadata.getContentType() : "application/octet-stream";
            long size = metadata.getSize();
            try (InputStream is = doGetObject(resolvedSrcBucket, resolvedSrcObject, null, null)) {
                doPutObject(resolvedDestBucket, resolvedDestObject, is, size, contentType);
            }
            log.info("[Storage] copyObject success, src={}/{}, dest={}/{}",
                    resolvedSrcBucket, resolvedSrcObject, resolvedDestBucket, resolvedDestObject);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] copyObject failed, src={}/{}, dest={}/{}, message={}",
                    resolvedSrcBucket, resolvedSrcObject, resolvedDestBucket, resolvedDestObject, e.getMessage());
            throw new BusinessException(FileExceptionCode.OBJECT_COPY_FAILED);
        }
    }

    @Override
    public void moveObject(String srcBucketName, String srcObjectName, String destBucketName, String destObjectName) {
        copyObject(srcBucketName, srcObjectName, destBucketName, destObjectName);
        delete(srcBucketName, srcObjectName);
        log.info("[Storage] moveObject success, src={}/{}, dest={}/{}",
                resolveBucketName(srcBucketName), resolveObjectKey(resolveBucketName(srcBucketName), srcObjectName),
                resolveBucketName(destBucketName), resolveObjectKey(resolveBucketName(destBucketName), destObjectName));
    }

    @Override
    public ListObjectsResult listObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        String resolvedBucket = resolveBucketName(bucketName);
        return doListObjects(resolvedBucket, prefix, cursor, maxKeys);
    }

    @Override
    public void makeFolder(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        if (!folderExists(resolvedBucket, resolvedObjectName)) {
            doMakeFolder(resolvedBucket, resolvedObjectName);
        }
    }

    @Override
    public FileStorage upload(String bucketName, String objectName, MultipartFile file) {
        return upload(bucketName, objectName, file, null);
    }

    @Override
    public FileStorage upload(String bucketName, String objectName, MultipartFile file, UploadProgressListener listener) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        if (file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        FileTypeValidator.validate(file);

        // 获取并发上传锁
        String lockToken = acquireConcurrencyLock(resolvedObjectName);

        makeBucket(resolvedBucket);
        FileStorage fileStorage = buildFileStorage(file);
        long totalBytes = file.getSize();

        if (listener != null) {
            listener.onStart(totalBytes);
        }

        try (InputStream inputStream = file.getInputStream()) {
            doPutObject(resolvedBucket, resolvedObjectName, inputStream,
                    file.getSize(), file.getContentType());

            fileStorage.setUuidName(resolvedObjectName);
            fileStorage.setUrl(buildObjectUrl(resolvedBucket, resolvedObjectName));

            if (listener != null) {
                listener.onSuccess(resolvedObjectName);
            }
            return fileStorage;
        } catch (BusinessException e) {
            if (listener != null) {
                listener.onFailure(resolvedObjectName, e);
            }
            throw e;
        } catch (Exception e) {
            log.error("[Storage] file upload failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            if (listener != null) {
                listener.onFailure(resolvedObjectName, e);
            }
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        } finally {
            releaseConcurrencyLock(resolvedObjectName, lockToken);
        }
    }

    @Override
    public void delete(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        if (StringUtils.isEmpty(resolvedObjectName)) {
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }

        try {
            doRemoveObject(resolvedBucket, resolvedObjectName);
        } catch (Exception e) {
            log.error("[Storage] file delete failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    public BatchDeleteResult batchDelete(String bucketName, List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return BatchDeleteResult.allSuccess(Collections.emptyList());
        }
        List<String> successList = new ArrayList<>();
        Map<String, String> failedMap = new ConcurrentHashMap<>();
        for (String objectName : objectNames) {
            try {
                delete(bucketName, objectName);
                successList.add(objectName);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failedMap.put(objectName, errorMsg);
                log.error("[Storage] batch delete failed, object={}, message={}", objectName, errorMsg, e);
            }
        }
        return new BatchDeleteResult(List.copyOf(successList), Map.copyOf(failedMap));
    }

    @Override
    public void download(String bucketName, String objectName, HttpServletResponse response) {
        download(bucketName, objectName, response, null, null);
    }

    @Override
    public void download(String bucketName, String objectName, HttpServletResponse response, Long offset, Long length) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        try (InputStream is = doGetObject(resolvedBucket, resolvedObjectName, offset, length);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            log.info("[Storage] file download success, bucket={}, object={}", resolvedBucket, resolvedObjectName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] file download failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public String getPublicUrl(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return buildObjectUrl(resolvedBucket, resolvedObjectName);
    }

    @Override
    public String getPrivateUrl(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return buildPrivateUrl(resolvedBucket, resolvedObjectName);
    }

    @Override
    public String generatePresignedUrl(String objectKey, int expireSeconds) {
        return generatePresignedUrl(null, objectKey, expireSeconds);
    }

    @Override
    public String generatePresignedUrl(String bucketName, String objectKey, int expireSeconds) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectKey = resolveObjectKey(resolvedBucket, objectKey);
        return doGeneratePresignedUrl(resolvedBucket, resolvedObjectKey, expireSeconds);
    }

    @Override
    public InputStream downloadAsStream(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        try {
            return doGetObject(resolvedBucket, resolvedObjectName, null, null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] downloadAsStream failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public ChunkedUploadResult initiateChunkedUpload(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        makeBucket(resolvedBucket);
        ChunkedUploadResult result = doInitiateMultipartUpload(resolvedBucket, resolvedObjectName);

        multipartContextStore.save(result.getUploadId(),
                new MultipartContextStore.MultipartContextData(result.getUploadId(), resolvedBucket, resolvedObjectName),
                MULTIPART_CONTEXT_TTL_SECONDS);

        log.info("[Storage] chunked upload initiated, bucket={}, object={}, uploadId={}",
                resolvedBucket, resolvedObjectName, result.getUploadId());
        return result;
    }

    @Override
    public void uploadChunk(String bucketName, String objectName, String uploadId, int partNumber, MultipartFile file) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        validateUploadId(uploadId);
        validatePartNumber(partNumber);

        try {
            byte[] chunkData = file.getBytes();

            // 流式更新 MD5 摘要，仅缓存 MessageDigest 状态而非原始数据，避免 OOM
            String chunkMd5 = null;
            if (isChunkMd5CheckEnabled()) {
                // 计算分片 MD5（用于校验）
                chunkMd5 = UploadCheckpoint.calculateMd5(chunkData);
                
                // 流式更新整体文件 MD5 摘要
                MessageDigest digest = chunkedMd5DigestMap.computeIfAbsent(
                        uploadId, k -> createMessageDigest());
                digest.update(chunkData);
            }

            String chunkObjectName = buildChunkObjectName(resolvedObjectName, uploadId, partNumber);
            doUploadPart(resolvedBucket, chunkObjectName, uploadId, partNumber,
                    new ByteArrayInputStream(chunkData), file.getSize());

            MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
            if (context == null) {
                context = new MultipartContextStore.MultipartContextData(uploadId, resolvedBucket, resolvedObjectName);
            }
            Map<Integer, String> partChunkNames = new ConcurrentHashMap<>(context.partChunkNames());
            partChunkNames.put(partNumber, chunkObjectName);
            multipartContextStore.save(uploadId,
                    new MultipartContextStore.MultipartContextData(
                            context.uploadId(), context.bucketName(), context.objectName(),
                            partChunkNames, context.createTime(), System.currentTimeMillis()),
                    MULTIPART_CONTEXT_TTL_SECONDS);

            // 更新检查点中的分片 MD5
            if (isChunkMd5CheckEnabled() && chunkMd5 != null) {
                checkpointService.updateChunkMd5InCheckpoint(resolvedBucket, resolvedObjectName, partNumber, chunkMd5, chunkData.length);
            }

            log.info("[Storage] chunk uploaded, bucket={}, object={}, part={}",
                    resolvedBucket, resolvedObjectName, partNumber);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Storage] uploadChunk failed, bucket={}, object={}, part={}, message={}",
                    resolvedBucket, resolvedObjectName, partNumber, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    public void completeChunkedUpload(String bucketName, String objectName, String uploadId, List<Integer> partNumbers) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        validateUploadId(uploadId);
        validatePartNumbers(partNumbers);

        MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
        if (context == null || !resolvedObjectName.equals(context.objectName())) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }

        Set<Integer> uniqueParts = new HashSet<>(partNumbers);
        List<Integer> sortedParts = new ArrayList<>(uniqueParts);
        Collections.sort(sortedParts);

        List<PartInfo> uploadedParts = listParts(resolvedBucket, resolvedObjectName, uploadId);

        for (Integer partNumber : sortedParts) {
            boolean found = uploadedParts.stream()
                    .anyMatch(p -> p.partNumber() == partNumber);
            if (!found) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
            }
        }

        try {
            doCompleteMultipartUpload(resolvedBucket, resolvedObjectName, uploadId, sortedParts);

            // 基于流式 MessageDigest 计算累积 MD5，避免缓存原始分片数据导致 OOM
            if (isChunkMd5CheckEnabled()) {
                MessageDigest digest = chunkedMd5DigestMap.get(uploadId);
                if (digest != null) {
                    String accumulatedMd5 = bytesToHex(digest.digest());
                    checkpointService.updateAccumulatedMd5(resolvedBucket, resolvedObjectName, accumulatedMd5);
                    log.debug("[Storage] computed accumulated MD5 via streaming digest, uploadId={}, md5={}", uploadId, accumulatedMd5);
                }
            }

            safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
            multipartContextStore.remove(uploadId);
            chunkedMd5DigestMap.remove(uploadId);

            // 分片上传完成后，基于 fileMd5 校验文件完整性
            if (isChunkMd5CheckEnabled()) {
                checkpointService.validateFileMd5(resolvedBucket, resolvedObjectName, true,
                        this::computeMd5,
                        (b, o) -> doGetObject(b, o, null, null));
            }

            log.info("[Storage] chunked upload completed, bucket={}, object={}, parts={}",
                    resolvedBucket, resolvedObjectName, sortedParts.size());
        } catch (BusinessException e) {
            safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
            multipartContextStore.remove(uploadId);
            chunkedMd5DigestMap.remove(uploadId);
            throw e;
        } catch (Exception e) {
            safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
            multipartContextStore.remove(uploadId);
            chunkedMd5DigestMap.remove(uploadId);
            log.error("[Storage] completeChunkedUpload failed, bucket={}, object={}, message={}",
                    resolvedBucket, resolvedObjectName, e.getMessage(), e);
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    /**
     * 将存储桶名称解析为实际使用的值
     * <p>当传入值为空时，使用配置文件中的默认桶名称
     *
     * @param bucketName 存储桶名称（可为 null）
     * @return 解析后的存储桶名称
     */
    protected String resolveBucketName(String bucketName) {
        return StringUtils.isNotBlank(bucketName) ? bucketName : defaultBucket;
    }

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(\\.\\.)|(%2e%2e)|(%2E%2E)");

    /**
     * 转义 JSON 字符串中的特殊字符
     * <p>用于安全地将字符串嵌入 JSON 文本中，防止注入。
     *
     * @param value 待转义的字符串
     * @return 转义后的字符串
     */
    protected static String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 校验路径是否在安全目录范围内，防止目录穿越攻击
     * <p>安全校验规则：
     * <ul>
     *   <li>使用 {@code Paths.normalize()} 规范化路径</li>
     *   <li>校验规范化后的路径以 baseDir 为前缀</li>
     *   <li>拒绝空字节、控制字符等异常输入</li>
     * </ul>
     *
     * @param path    待校验的文件路径
     * @param baseDir 允许的基础目录
     * @return true 表示路径安全
     */
    protected static boolean isSafePath(String path, String baseDir) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(baseDir)) {
            return false;
        }
        try {
            Path basePath = Paths.get(baseDir).normalize().toAbsolutePath();
            Path resolvedPath = basePath.resolve(path).normalize().toAbsolutePath();
            return resolvedPath.startsWith(basePath);
        } catch (Exception e) {
            log.warn("[Storage] path validation failed, path={}, baseDir={}, message={}",
                    path, baseDir, e.getMessage());
            return false;
        }
    }

    /**
     * 解析并校验对象路径，防止路径穿越攻击
     * <p>校验规则：
     * <ul>
     *   <li>路径不能为空</li>
     *   <li>禁止包含空字节 {@code \0} 及控制字符</li>
     *   <li>禁止包含 {@code ..} 路径穿越符（含 URL 编码形式）</li>
     *   <li>规范化路径后禁止以 {@code ..} 作为路径段</li>
     *   <li>使用 {@code Paths.normalize()} 进行二次校验</li>
     * </ul>
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @return 解析后的对象路径
     * @throws BusinessException 当路径为空或存在安全风险时
     */
    protected final String resolveObjectKey(String bucketName, String objectName) {
        if (StringUtils.isEmpty(objectName)) {
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        if (objectName.indexOf('\0') >= 0) {
            log.warn("[Storage] null byte detected in objectName={}", objectName);
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        if (PATH_TRAVERSAL_PATTERN.matcher(objectName).find()) {
            log.warn("[Storage] path traversal detected, objectName={}", objectName);
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        String resolved = objectName;
        if (!resolved.startsWith("/")) {
            resolved = "/" + resolved;
        }
        String normalized = resolved.replace("\\", "/").replaceAll("/+", "/");
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                log.warn("[Storage] path traversal after normalization, objectName={}", objectName);
                throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
            }
        }
        try {
            String canonicalPath = Paths.get(normalized).normalize().toString();
            if (!canonicalPath.equals(normalized) && canonicalPath.contains("..")) {
                log.warn("[Storage] path traversal after canonical normalization, objectName={}, normalized={}, canonical={}",
                        objectName, normalized, canonicalPath);
                throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
            }
        } catch (Exception e) {
            log.warn("[Storage] path canonicalization failed, objectName={}, message={}",
                    objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
        }
        return normalizeObjectKey(normalized);
    }

    protected String normalizeObjectKey(String objectKey) {
        return objectKey;
    }

    /**
     * 检测文件的 MIME Type
     * <p>优先使用 MultipartFile.getContentType()，若为空则通过
     * URLConnection.guessContentTypeFromStream() 基于文件头魔数检测。
     *
     * @param file 上传的文件
     * @return MIME Type，无法检测时返回 application/octet-stream
     */
    protected String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.isNotBlank(contentType)) {
            return contentType;
        }
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            is.mark(32);
            String guessed = URLConnection.guessContentTypeFromStream(is);
            if (StringUtils.isNotBlank(guessed)) {
                return guessed;
            }
        } catch (Exception e) {
            log.debug("[Storage] MIME Type detection failed for file: {}, message={}",
                    file.getOriginalFilename(), e.getMessage());
        }
        // fallback: 基于后缀推断
        String suffix = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf(FileConstant.SUFFIX_SPLIT);
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                suffix = originalFilename.substring(dotIndex + 1).toLowerCase();
            }
        }
        String mapped = URLConnection.guessContentTypeFromName("file." + suffix);
        return mapped != null ? mapped : "application/octet-stream";
    }

    /**
     * 从 FileStorage 构建 MultipartFile 的文件信息对象
     * <p>子类可覆盖此方法以自定义 FileStorage 的构建逻辑
     *
     * @param file 上传的文件
     * @return 文件存储信息对象
     */
    protected FileStorage buildFileStorage(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || StringUtils.isBlank(originalFilename)) {
            throw new BusinessException(FileExceptionCode.FILE_NAME_INVALID);
        }

        int dotIndex = originalFilename.lastIndexOf(FileConstant.SUFFIX_SPLIT);
        if (dotIndex < 0) {
            throw new BusinessException(FileExceptionCode.FILE_NAME_INVALID);
        }

        String suffix = originalFilename.substring(dotIndex + 1).toLowerCase();
        if (!isAllowedSuffix(suffix)) {
            throw new BusinessException(FileExceptionCode.FILE_SUFFIX_NOT_ALLOWED);
        }

        FileStorage fileStorage = new FileStorage();
        fileStorage.setFileName(originalFilename);
        fileStorage.setSuffix(suffix);
        fileStorage.setSize(file.getSize());
        fileStorage.setIsDir(0);
        fileStorage.setIsImage(isImageSuffix(suffix) ? 1 : 0);
        fileStorage.setIsVideo(isVideoSuffix(suffix) ? 1 : 0);
        fileStorage.setIsAudio(isAudioSuffix(suffix) ? 1 : 0);
        fileStorage.setIsOffice(isOfficeSuffix(suffix) ? 1 : 0);
        fileStorage.setIsCode(isCodeSuffix(suffix) ? 1 : 0);
        fileStorage.setType(isCodeSuffix(suffix) ? "code" : suffix);
        fileStorage.setMimeType(detectMimeType(file));
        fileStorage.setUploadAt(LocalDateTime.now());
        return fileStorage;
    }

    /**
     * 检查文件后缀是否允许上传
     *
     * @param suffix 文件后缀（不含点）
     * @return true 允许上传
     */
    protected boolean isAllowedSuffix(String suffix) {
        List<String> allowedSuffixes = fileProperties.getAllowedSuffixes();
        if (allowedSuffixes == null || allowedSuffixes.isEmpty()) {
            return true;
        }
        return allowedSuffixes.stream()
                .map(String::toLowerCase)
                .anyMatch(s -> s.equalsIgnoreCase(suffix));
    }

    private static final Set<String> IMAGE_SUFFIXES = Set.of(
            "png", "bmp", "jpg", "jpeg", "gif", "svg", "ico", "webp");

    private static final Set<String> VIDEO_SUFFIXES = Set.of(
            "mp4", "flv", "avi", "mkv", "mov", "wmv", "3gp");

    private static final Set<String> AUDIO_SUFFIXES = Set.of(
            "mp3", "wma", "wav", "flac", "aac", "ogg");

    private static final Set<String> OFFICE_SUFFIXES = Set.of(
            "txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "csv");

    private static final Set<String> CODE_SUFFIXES = Set.of(
            "java", "sql", "js", "py", "php", "vue", "sh", "css", "html", "htm", "xml", "json");

    /**
     * 检查是否为图片文件后缀
     */
    protected boolean isImageSuffix(String suffix) {
        return suffix != null && IMAGE_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 检查是否为视频文件后缀
     */
    protected boolean isVideoSuffix(String suffix) {
        return suffix != null && VIDEO_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 检查是否为音频文件后缀
     */
    protected boolean isAudioSuffix(String suffix) {
        return suffix != null && AUDIO_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 检查是否为办公文档后缀
     */
    protected boolean isOfficeSuffix(String suffix) {
        return suffix != null && OFFICE_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 检查是否为代码文件后缀
     */
    protected boolean isCodeSuffix(String suffix) {
        return suffix != null && CODE_SUFFIXES.contains(suffix.toLowerCase());
    }

    /**
     * 校验上传 ID 格式
     */
    protected void validateUploadId(String uploadId) {
        if (StringUtils.isBlank(uploadId) || uploadId.length() > 64) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    /**
     * 校验分片编号
     */
    protected void validatePartNumber(int partNumber) {
        if (partNumber <= 0) {
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    /**
     * 校验分片编号列表
     */
    protected void validatePartNumbers(List<Integer> partNumbers) {
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
     * 构建分片对象名称
     */
    protected String buildChunkObjectName(String objectName, String uploadId, int partNumber) {
        return CHUNK_DIR_PREFIX + FileConstant.DIR_SPLIT +
                objectName + FileConstant.DIR_SPLIT +
                uploadId + FileConstant.DIR_SPLIT +
                String.format(CHUNK_FILE_NAME_FORMAT, partNumber);
    }

    /**
     * 安全中止分片上传（失败时清理资源）
     */
    protected void safeAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        if (StringUtils.isBlank(uploadId)) {
            return;
        }
        try {
            doAbortMultipartUpload(bucketName, objectName, uploadId);
        } catch (Exception e) {
            log.warn("[Storage] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    /**
     * 创建目录（以 / 结尾的 0 字节对象）
     */
    protected void createFolderByEmptyObject(String bucketName, String folderName) {
        try (InputStream emptyStream = new ByteArrayInputStream(new byte[]{})) {
            doPutObject(bucketName, folderName, emptyStream, 0L, "application/directory");
        } catch (Exception e) {
            throw new BusinessException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    public PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires) {
        log.warn("[Storage] generateUploadPolicy is not supported for this storage type");
        return null;
    }

    @Override
    public UploadCheckpoint initChunkedUploadWithCheckpoint(String bucketName, String objectName, MultipartFile file) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

        if (file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        makeBucket(resolvedBucket);

        UploadCheckpoint existingCheckpoint = loadCheckpoint(resolvedBucket, resolvedObjectName);

        if (existingCheckpoint != null && existingCheckpoint.getUploadId() != null) {
            UploadCheckpoint loadedCheckpoint = validateAndRecoverCheckpoint(existingCheckpoint, file);
            if (loadedCheckpoint != null) {
                log.info("[Storage] recovered existing checkpoint, bucket={}, object={}, uploadId={}, uploadedParts={}",
                        resolvedBucket, resolvedObjectName, loadedCheckpoint.getUploadId(), loadedCheckpoint.getUploadedPartsCount());
                return loadedCheckpoint;
            }
        }

        ChunkedUploadResult chunkResult = initiateChunkedUpload(resolvedBucket, resolvedObjectName);

        long fileSize = file.getSize();
        long partSize = fileProperties.getPartSize() != null ? fileProperties.getPartSize() : 5242880L;

        UploadCheckpoint checkpoint = new UploadCheckpoint();
        checkpoint.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        checkpoint.setBucketName(resolvedBucket);
        checkpoint.setObjectName(resolvedObjectName);
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

        // 初始化时计算文件 MD5
        if (isChunkMd5CheckEnabled()) {
            try {
                String fileMd5 = UploadCheckpoint.calculateMd5(file.getBytes());
                checkpoint.setFileMd5(fileMd5);
            } catch (Exception e) {
                log.warn("[Storage] initChunkedUploadWithCheckpoint fileMd5 compute failed, message={}", e.getMessage());
            }
        }

        saveCheckpoint(checkpoint);

        return checkpoint;
    }

    @Override
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
            fileStorage.setUrl(getPublicUrl(bucketName, objectName));
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

    @Override
    public UploadCheckpoint getCheckpoint(String bucketName, String objectName) {
        String resolvedBucket = resolveBucketName(bucketName);
        String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);
        return loadCheckpoint(resolvedBucket, resolvedObjectName);
    }

    /**
     * 保存上传检查点
     *
     * @param checkpoint 检查点数据
     */
    protected void saveCheckpoint(UploadCheckpoint checkpoint) {
        CheckpointService service = checkpointService;
        if (service != null) {
            service.saveCheckpoint(checkpoint);
        }
    }

    /**
     * 加载上传检查点
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 检查点数据，不存在时返回 null
     */
    protected UploadCheckpoint loadCheckpoint(String bucketName, String objectName) {
        CheckpointService service = checkpointService;
        if (service != null) {
            return service.loadCheckpoint(bucketName, objectName);
        }
        return null;
    }

    /**
     * 校验并恢复上传检查点
     *
     * @param checkpoint 已有检查点
     * @param file 上传文件
     * @return 校验后的检查点
     */
    protected UploadCheckpoint validateAndRecoverCheckpoint(UploadCheckpoint checkpoint, MultipartFile file) {
        CheckpointService service = checkpointService;
        if (service != null) {
            return service.validateAndRecoverCheckpoint(checkpoint, file);
        }
        return checkpoint;
    }

    @Override
    public void deleteCheckpoint(UploadCheckpoint checkpoint) {
        CheckpointService service = checkpointService;
        if (service != null) {
            service.deleteCheckpoint(checkpoint);
        }
    }

    /**
     * 获取并发上传锁
     *
     * @param objectKey 文件对象键
     * @return 锁令牌，用于释放锁
     */
    protected String acquireConcurrencyLock(String objectKey) {
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
    protected void releaseConcurrencyLock(String objectKey, String lockToken) {
        if (concurrencyGuard != null && lockToken != null) {
            try {
                concurrencyGuard.release(objectKey, lockToken);
            } catch (Exception e) {
                log.warn("[Storage] releaseConcurrencyLock failed, object={}, error={}", objectKey, e.getMessage());
            }
        }
    }

    // ==================== MD5 校验辅助方法 ====================

    /**
     * 创建 MD5 摘要实例
     *
     * @return MessageDigest 实例
     */
    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create MD5 MessageDigest", e);
        }
    }

    /**
     * 计算输入流的 MD5（会消费流，调用者需自行重新获取流）
     */
    protected String computeMd5(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (Exception e) {
            log.warn("[Storage] computeMd5 failed, message={}", e.getMessage());
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== 抽象方法，子类必须实现 ====================

    /**
     * 判断存储桶是否存在
     *
     * @param bucketName 已解析的存储桶名称
     * @return true 表示存在
     */
    protected abstract boolean doBucketExists(String bucketName);

    /**
     * 创建存储桶
     *
     * @param bucketName 已解析的存储桶名称
     */
    protected abstract void doMakeBucket(String bucketName);

    /**
     * 判断目录是否存在
     *
     * @param bucketName 已解析的存储桶名称
     * @param folderName 已解析的目录名称
     * @return true 表示存在
     */
    protected abstract boolean doFolderExists(String bucketName, String folderName);

    /**
     * 获取对象元信息
     *
     * @param bucketName 已解析的存储桶名称
     * @param objectName 已解析的对象名称
     * @return 对象元信息，若不存在返回 null
     */
    protected abstract ObjectMetadata doGetMetadata(String bucketName, String objectName);

    /**
     * 分页列举对象
     *
     * @param bucketName 已解析的存储桶名称
     * @param prefix    对象前缀过滤
     * @param cursor    分页游标
     * @param maxKeys   每页最大返回数量
     * @return 分页结果
     */
    protected abstract ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys);

    /**
     * 创建目录
     *
     * @param bucketName 已解析的存储桶名称
     * @param folderName 目录名称（应确保以 / 结尾）
     */
    protected abstract void doMakeFolder(String bucketName, String folderName);

    /**
     * 写入对象到存储
     *
     * @param bucketName  存储桶名称
     * @param objectName  对象路径
     * @param inputStream 数据输入流
     * @param size        数据大小
     * @param contentType 内容类型
     */
    protected abstract void doPutObject(String bucketName, String objectName,
                                        InputStream inputStream, long size, String contentType);

    /**
     * 读取对象内容
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @param offset     起始偏移（null 表示从 0 开始）
     * @param length     读取长度（null 表示读取全部）
     * @return 输入流
     */
    protected abstract InputStream doGetObject(String bucketName, String objectName,
                                               Long offset, Long length);

    /**
     * 删除对象
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     */
    protected abstract void doRemoveObject(String bucketName, String objectName);

    /**
     * 构建公开访问 URL
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @return 公开访问 URL
     */
    protected abstract String buildObjectUrl(String bucketName, String objectName);

    /**
     * 构建私有签名访问 URL
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @return 私有签名 URL（不支持时返回空字符串）
     */
    protected String buildPrivateUrl(String bucketName, String objectName) {
        return "";
    }

    /**
     * 生成预签名 URL（可自定义过期时间）
     *
     * <p>默认实现返回公开访问 URL。子类可覆盖此方法以使用云厂商 SDK 生成签名 URL。
     *
     * @param bucketName    存储桶名称
     * @param objectName    对象路径
     * @param expireSeconds 过期时间（秒）
     * @return 预签名 URL
     */
    protected String doGeneratePresignedUrl(String bucketName, String objectName, int expireSeconds) {
        return buildObjectUrl(bucketName, objectName);
    }

    /**
     * 初始化分片上传
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @return 分片上传结果（包含 uploadId）
     */
    protected abstract ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName);

    /**
     * 上传单个分片
     *
     * @param bucketName    存储桶名称
     * @param chunkObjectName 分片对象名称
     * @param uploadId      分片任务 ID
     * @param partNumber    分片编号
     * @param inputStream   分片数据流
     * @param size          分片大小
     */
    protected abstract void doUploadPart(String bucketName, String chunkObjectName,
                                         String uploadId, int partNumber,
                                         InputStream inputStream, long size);

    /**
     * 完成分片上传并合并
     *
     * @param bucketName  存储桶名称
     * @param objectName  对象路径
     * @param uploadId    分片任务 ID
     * @param partNumbers 已上传的分片编号列表（升序）
     */
    protected abstract void doCompleteMultipartUpload(String bucketName, String objectName,
                                                       String uploadId, List<Integer> partNumbers);

    /**
     * 中止分片上传（清理已上传的分片）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @param uploadId   分片任务 ID
     */
    protected abstract void doAbortMultipartUpload(String bucketName, String objectName, String uploadId);

    /**
     * 列举已上传的分片
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象路径
     * @param uploadId   分片任务 ID
     * @return 已上传分片信息列表
     */
    protected abstract List<PartInfo> listParts(String bucketName, String objectName, String uploadId);
}
