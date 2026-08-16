package com.njydsz.common.file.storage.platform;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.config.FileProperties;
import com.njydsz.common.file.config.FileUploadProperties;
import com.njydsz.common.file.constant.FileConstant;
import com.njydsz.common.file.domain.ChunkedUploadResult;
import com.njydsz.common.file.domain.ListObjectsResult;
import com.njydsz.common.file.domain.ObjectMetadata;
import com.njydsz.common.file.domain.PolicyResult;
import com.njydsz.common.file.exception.FileExceptionCode;
import com.njydsz.common.file.storage.AbstractFileStorage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.util.string.StringUtils;
/**
 * MinIO 对象存储实现。
 *
 * <p>继承 {@link AbstractFileStorage}，将操作翻译为 MinIO Java SDK 的原生 API 调用。
 * MinIO 兼容 S3 协议，但使用独立的 Java SDK，API 更为简洁。
 *
 * <h3>分片上传策略</h3>
 * <p>MinIO SDK 不直接暴露 S3 multipart upload 协议，本实现采用「分片对象暂存 + composeObject 合并」策略：
 * <ol>
 *   <li>每个分片作为独立对象上传到临时前缀目录</li>
 *   <li>所有分片上传完成后，调用 {@code composeObject} 将分片合并为目标对象</li>
 *   <li>合并成功后清理临时分片对象</li>
 *   <li>失败时调用 {@code abortChunkedUpload} 清理已上传的分片</li>
 * </ol>
 *
 * <h3>预签名 URL</h3>
 * <p>通过 {@code getPresignedObjectUrl} 生成临时下载链接，支持过期时间配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AbstractFileStorage
 * @see MinioClient
 */
@Slf4j
public class MinioStorage extends AbstractFileStorage {

    private final MinioClient minioClient;

    /**
     * 构建 MinIO 客户端
     *
     * @param config 存储配置（endpoint/accessKey/secretKey/bucket）
     */
    public MinioStorage(FileProperties config) {
        this(config, null);
    }

    /**
     * 构建 MinIO 客户端
     *
     * @param config 存储配置（endpoint/accessKey/secretKey/bucket）
     * @param uploadProps 分片上传配置
     */
    public MinioStorage(FileProperties config, FileUploadProperties uploadProps) {
        super(config, uploadProps);
        try {
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.getSocketTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(config.getSocketTimeout(), TimeUnit.MILLISECONDS)
                    .build();
            this.minioClient = MinioClient.builder()
                    .credentials(config.getAccessKey(), config.getSecretKey())
                    .endpoint(config.getEndpoint())
                    .httpClient(httpClient)
                    .build();
        } catch (Exception e) {
            log.error("[Minio] MinioClient build failed: {}", e.getMessage());
            throw new BusinessException(FileExceptionCode.CONFIG_INVALID);
        }
    }

    @Override
    protected boolean doBucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("[Minio] bucketExists failed, bucket={}, message={}", bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeBucket(String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("[Minio] make Bucket success bucketName:{}", bucketName);
        } catch (Exception e) {
            log.error("[Minio] make Bucket failed, bucket={}, message={}", bucketName, e.getMessage());
            throw new BusinessException(FileExceptionCode.BUCKET_ERROR);
        }
    }

    @Override
    protected boolean doFolderExists(String bucketName, String folderName) {
        try {
            String prefix = folderName.endsWith(FileConstant.DIR_SPLIT) ? folderName : folderName + FileConstant.DIR_SPLIT;
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(false)
                            .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() && prefix.equals(item.objectName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("[Minio] folderExist failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeFolder(String bucketName, String folderName) {
        try {
            String resolvedFolderName = folderName.endsWith(FileConstant.DIR_SPLIT)
                    ? folderName : folderName + FileConstant.DIR_SPLIT;
            if (!doFolderExists(bucketName, resolvedFolderName)) {
                InputStream emptyStream = new ByteArrayInputStream(new byte[]{});
                PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(resolvedFolderName)
                        .stream(emptyStream, 0, -1)
                        .build();
                minioClient.putObject(putObjectArgs);
                log.info("[Minio] make Folder success folderName:{}", resolvedFolderName);
            }
        } catch (Exception e) {
            log.error("[Minio] make Folder failed, folder={}, message={}", folderName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
        }
    }

    @Override
    protected void doPutObject(String bucketName, String objectName,
                               InputStream inputStream, long size, String contentType) {
        try {
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build();
            minioClient.putObject(putObjectArgs);
        } catch (Exception e) {
            log.error("[Minio] doPutObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    protected InputStream doGetObject(String bucketName, String objectName,
                                      Long offset, Long length) {
        try {
            GetObjectArgs.Builder argsBuilder = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName);
            if (offset != null && offset >= 0 && length != null && length > 0) {
                argsBuilder.offset(offset).length(length);
            }
            return minioClient.getObject(argsBuilder.build());
        } catch (Exception e) {
            log.error("[Minio] doGetObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
        }
    }

    @Override
    protected void doRemoveObject(String bucketName, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("[Minio] doRemoveObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
        }
    }

    @Override
    protected String buildObjectUrl(String bucketName, String objectName) {
        return endpoint + FileConstant.DIR_SPLIT + bucketName + FileConstant.DIR_SPLIT + objectName;
    }

    @Override
    protected String buildPrivateUrl(String bucketName, String objectName) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(7, TimeUnit.DAYS)
                    .build();
            return minioClient.getPresignedObjectUrl(args);
        } catch (Exception e) {
            log.error("[Minio] generate private url failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
        }
    }

    @Override
    protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
        String uploadId = IdGenerator.nextIdStr();
        return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
    }

    @Override
    protected void doUploadPart(String bucketName, String chunkObjectName,
                               String uploadId, int partNumber,
                               InputStream inputStream, long size) {
        try {
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(chunkObjectName)
                    .stream(inputStream, size, -1)
                    .build();
            minioClient.putObject(putObjectArgs);
        } catch (Exception e) {
            log.error("[Minio] doUploadPart failed, chunk={}, part={}, message={}",
                    chunkObjectName, partNumber, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doCompleteMultipartUpload(String bucketName, String objectName,
                                           String uploadId, List<Integer> partNumbers) {
        try {
            List<ComposeSource> sources = new ArrayList<>();
            for (Integer partNumber : partNumbers) {
                String chunkObjectName = buildChunkObjectName(objectName, uploadId, partNumber);
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(chunkObjectName)
                        .build());
                sources.add(ComposeSource.builder()
                        .bucket(bucketName)
                        .object(chunkObjectName)
                        .build());
            }
            ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .sources(sources)
                    .build();
            minioClient.composeObject(composeObjectArgs);

            for (Integer partNumber : partNumbers) {
                String chunkObjectName = buildChunkObjectName(objectName, uploadId, partNumber);
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(chunkObjectName)
                            .build());
                } catch (Exception e) {
                    log.warn("[Minio] cleanup chunk object failed after compose, object={}, message={}",
                            chunkObjectName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Minio] doCompleteMultipartUpload failed, object={}, message={}",
                    objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            List<PartInfo> existingParts = listParts(bucketName, objectName, uploadId);
            for (PartInfo part : existingParts) {
                String chunkObjectName = buildChunkObjectName(objectName, uploadId, part.partNumber());
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(chunkObjectName)
                            .build());
                } catch (Exception e) {
                    log.warn("[Minio] abort remove chunk failed, object={}, message={}",
                            chunkObjectName, e.getMessage());
                }
            }
            log.info("[Minio] doAbortMultipartUpload cleaned {} chunks, object={}",
                    existingParts.size(), objectName);
        } catch (Exception e) {
            log.warn("[Minio] doAbortMultipartUpload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    @Override
    protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        try {
            String prefix = CHUNK_DIR_PREFIX + FileConstant.DIR_SPLIT + objectName + FileConstant.DIR_SPLIT + uploadId + FileConstant.DIR_SPLIT + "part-";
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(false)
                            .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String name = item.objectName();
                if (name != null && name.contains("/part-")) {
                    try {
                        String numStr = name.substring(name.lastIndexOf("/part-") + 6);
                        int partNumber = Integer.parseInt(numStr);
                        parts.add(new PartInfo(partNumber, null, item.size()));
                    } catch (NumberFormatException ignored) {
                        log.debug("Caught exception (ignored): {}", ignored.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Minio] listParts failed, object={}, message={}", objectName, e.getMessage());
        }
        return parts;
    }

    @Override
    protected boolean supportsServerSideCopy() {
        return true;
    }

    @Override
    protected void doCopyObject(String srcBucket, String srcObject, String destBucket, String destObject) {
        try {
            minioClient.copyObject(
                CopyObjectArgs.builder()
                    .source(CopySource.builder().bucket(srcBucket).object(srcObject).build())
                    .bucket(destBucket)
                    .object(destObject)
                    .build());
        } catch (Exception e) {
            log.error("Minio server-side copy failed: {} ", e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
        }
    }

    @Override
    protected String normalizeObjectKey(String objectKey) {
        if (objectKey.startsWith("/")) {
            return objectKey.substring(1);
        }
        return objectKey;
    }

    @Override
    protected ObjectMetadata doGetMetadata(String bucketName, String objectName) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectName(objectName);
            metadata.setBucketName(bucketName);
            metadata.setSize(stat.size());
            metadata.setContentType(stat.contentType());
            metadata.setETag(stat.etag());
            metadata.setLastModified(stat.lastModified() != null
                    ? LocalDateTime.ofInstant(stat.lastModified().toInstant(), ZoneId.systemDefault())
                    : null);
            metadata.setDirectory(false);
            return metadata;
        } catch (Exception e) {
            log.error("[Minio] doGetMetadata failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return null;
        }
    }

    @Override
    protected ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        List<ObjectMetadata> objects = new ArrayList<>();
        try {
            ListObjectsArgs.Builder argsBuilder = ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .recursive(false)
                    .maxKeys(maxKeys + 1);
            if (prefix != null && !prefix.isEmpty()) {
                argsBuilder.prefix(prefix);
            }
            if (cursor != null && !cursor.isEmpty()) {
                argsBuilder.startAfter(cursor);
            }
            Iterable<Result<Item>> results = minioClient.listObjects(argsBuilder.build());
            int count = 0;
            String lastKey = null;
            for (Result<Item> itemResult : results) {
                Item item = itemResult.get();
                if (count < maxKeys) {
                    ObjectMetadata om = new ObjectMetadata();
                    om.setObjectName(item.objectName());
                    om.setBucketName(bucketName);
                    om.setSize(item.size());
                    om.setLastModified(item.lastModified() != null
                            ? LocalDateTime.ofInstant(item.lastModified().toInstant(), ZoneId.systemDefault())
                            : null);
                    om.setETag(item.etag());
                    om.setDirectory(item.isDir());
                    objects.add(om);
                    lastKey = item.objectName();
                } else {
                    ListObjectsResult listResult = new ListObjectsResult();
                    listResult.setObjects(objects);
                    listResult.setHasMore(true);
                    listResult.setNextCursor(lastKey);
                    listResult.setObjectCount(objects.size());
                    return listResult;
                }
                count++;
            }
            ListObjectsResult listResult = new ListObjectsResult();
            listResult.setObjects(objects);
            listResult.setHasMore(false);
            listResult.setNextCursor(null);
            listResult.setObjectCount(objects.size());
            return listResult;
        } catch (Exception e) {
            log.error("[Minio] doListObjects failed, bucket={}, prefix={}, message={}",
                    bucketName, prefix, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
        }
    }

    @Override
    protected String doGeneratePresignedUrl(String bucketName, String objectName, int expireSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expireSeconds, TimeUnit.SECONDS)
                            .method(Method.GET)
                            .build());
        } catch (Exception e) {
            log.error("[MinIO] generatePresignedUrl failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage(), e);
            return buildObjectUrl(bucketName, objectName);
        }
    }

    @Override
    public PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires) {
        try {
            String resolvedBucket = resolveBucketName(bucketName);
            String resolvedPrefix = objectNamePrefix != null ? objectNamePrefix : "";
            int expirySeconds = expires != null ? expires :
                    (fileProperties.getTemporarySignatureExpiry() != null ? fileProperties.getTemporarySignatureExpiry() : 3600);

            Instant expirationInstant = Instant.now().plusSeconds(expirySeconds);
            String expirationStr = DateTimeFormatter.ISO_INSTANT.format(expirationInstant);

            Map<String, Object> policyMap = Map.of(
                    "expiration", expirationStr,
                    "conditions", List.of(
                            List.of("starts-with", "$key", resolvedPrefix),
                            Map.of("bucket", resolvedBucket)));
            String policyJson = YdszJson.toJson(policyMap);

            String policyBase64 = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fileProperties.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(policyBase64.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            String resolvedEndpoint = fileProperties.getEndpoint();
            if (StringUtils.isBlank(resolvedEndpoint)) {
                resolvedEndpoint = String.format("https://%s.minio.local", resolvedBucket);
            }

            PolicyResult result = new PolicyResult();
            result.setAccessKeyId(fileProperties.getAccessKey());
            result.setPolicy(policyBase64);
            result.setSignature(signature);
            result.setBucket(resolvedBucket);
            result.setObjectKeyPrefix(resolvedPrefix);
            result.setExpiration(expirationStr);
            result.setEndpoint(resolvedEndpoint);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Minio] generateUploadPolicy failed, bucket={}, prefix={}, message={}",
                    bucketName, objectNamePrefix, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 清理分片上传产生的过期 chunk 残留对象。
     *
     * <p>大文件分片上传中断后，MinIO 不会自动回收已写入的分片，长期堆积会占用大量存储并增加列举开销。
     * 本方法按 {@code CHUNK_DIR_PREFIX} 前缀列举疑似分片对象，将超过 {@code maxAgeHours} 阈值的对象删除。
     *
     * <p><b>容错策略：</b>单条对象删除失败仅 {@code warn} 记录并继续后续对象，不中断整体清理；
     * 列举或整体流程异常仅 {@code error} 记录后吞掉，<b>不得向外抛出</b>，避免清理任务失败影响主存储链路。
     * 桶名经 {@code resolveBucketName} 归一化，{@code null}/空桶名由调用方保证。
     *
     * @param bucketName   目标桶名（会经 resolveBucketName 解析），非空
     * @param maxAgeHours  分片对象的最大存活时长（小时），超过该时长的残留分片将被清理
     */
    public void cleanupStaleMultipartUploads(String bucketName, long maxAgeHours) {
        String resolvedBucket = resolveBucketName(bucketName);
        String stalePrefix = CHUNK_DIR_PREFIX + FileConstant.DIR_SPLIT;
        long cutoffTime = System.currentTimeMillis() - (maxAgeHours * 3600L * 1000L);
        int cleanedCount = 0;
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(resolvedBucket)
                            .prefix(stalePrefix)
                            .recursive(true)
                            .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                LocalDateTime lastModified = item.lastModified() != null
                        ? LocalDateTime.ofInstant(item.lastModified().toInstant(), ZoneId.systemDefault())
                        : null;
                if (lastModified != null && lastModified.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() < cutoffTime) {
                    try {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(resolvedBucket)
                                .object(objectName)
                                .build());
                        cleanedCount++;
                    } catch (Exception e) {
                        log.warn("[Minio] cleanup stale multipart chunk failed, object={}, message={}",
                                objectName, e.getMessage());
                    }
                }
            }
            if (cleanedCount > 0) {
                log.info("[Minio] cleanupStaleMultipartUploads completed, bucket={}, cleaned={}", resolvedBucket, cleanedCount);
            }
        } catch (Exception e) {
            log.error("[Minio] cleanupStaleMultipartUploads failed, bucket={}, message={}", resolvedBucket, e.getMessage());
        }
    }
}
