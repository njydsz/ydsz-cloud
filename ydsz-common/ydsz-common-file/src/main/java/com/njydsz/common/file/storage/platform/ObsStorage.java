package com.njydsz.common.file.storage.platform;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.njydsz.common.util.string.StringUtils;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.model.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 华为云 OBS 对象存储实现。
 *
 * <p>继承 {@link AbstractFileStorage}，将操作翻译为华为云 OBS Java SDK 的原生 API 调用。
 * 实现 {@link AutoCloseable} 以在应用关闭时释放 OBS 客户端资源。
 *
 * <h3>分片上传协议</h3>
 * <p>使用 OBS 原生 multipart upload 协议，完整支持分片上传生命周期：
 * <ol>
 *   <li>{@code initiateMultipartUpload}：初始化分片上传，获取 uploadId</li>
 *   <li>{@code uploadPart}：上传单个分片，返回分片 ETag</li>
 *   <li>{@code listParts}：列出已上传的分片</li>
 *   <li>{@code completeMultipartUpload}：合并所有分片为最终对象</li>
 *   <li>{@code abortMultipartUpload}：取消上传并清理已上传分片</li>
 * </ol>
 *
 * <h3>临时授权</h3>
 * <p>通过 {@code generatePresignedUrl} 生成临时下载链接，支持自定义过期时间和 HTTP 方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AbstractFileStorage
 * @see ObsClient
 */
@Slf4j
public class ObsStorage extends AbstractFileStorage implements AutoCloseable {

    private final ObsClient obsClient;

    /**
     * 构建华为云 OBS 客户端
     *
     * @param config 存储配置（endpoint/accessKey/secretKey/bucket）
     */
    public ObsStorage(FileProperties config) {
        this(config, null);
    }

    /**
     * 构建华为云 OBS 客户端
     *
     * @param config 存储配置（endpoint/accessKey/secretKey/bucket）
     * @param uploadProps 分片上传配置
     */
    public ObsStorage(FileProperties config, FileUploadProperties uploadProps) {
        super(config, uploadProps);
        Objects.requireNonNull(config.getEndpoint(), "OBS endpoint cannot be null");
        Objects.requireNonNull(config.getAccessKey(), "OBS accessKey cannot be null");
        Objects.requireNonNull(config.getSecretKey(), "OBS secretKey cannot be null");

        try {
            ObsConfiguration obsConfig = new ObsConfiguration();
            obsConfig.setEndPoint(config.getEndpoint());
            obsConfig.setConnectionTimeout(
                    config.getConnectionTimeout() != null ? config.getConnectionTimeout() : 30_000);
            obsConfig.setSocketTimeout(
                    config.getSocketTimeout() != null ? config.getSocketTimeout() : 60_000);
            obsConfig.setMaxConnections(
                    config.getMaxConnections() != null ? config.getMaxConnections() : 100);

            this.obsClient = new ObsClient(
                    config.getAccessKey(),
                    config.getSecretKey(),
                    obsConfig);
        } catch (Exception e) {
            log.error("[OBS] ObsClient build failed: {}", e.getMessage());
            throw new BusinessException(FileExceptionCode.STORAGE_CLIENT_BUILD_FAILED);
        }
    }

    @Override
    protected boolean doBucketExists(String bucketName) {
        try {
            return obsClient.headBucket(bucketName);
        } catch (Exception e) {
            log.error("[OBS] bucketExists failed, bucket={}, message={}", bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeBucket(String bucketName) {
        try {
            if (!doBucketExists(bucketName)) {
                CreateBucketRequest request = new CreateBucketRequest(bucketName);
                obsClient.createBucket(request);
                log.info("[OBS] make Bucket success bucketName:{}", bucketName);
            }
        } catch (Exception e) {
            log.error("[OBS] make Bucket failed, bucket={}, message={}", bucketName, e.getMessage());
            throw new BusinessException(FileExceptionCode.BUCKET_CREATE_FAILED);
        }
    }

    @Override
    protected boolean doFolderExists(String bucketName, String folderName) {
        try {
            String key = normalizeFolderPath(folderName);
            obsClient.getObjectMetadata(bucketName, key);
            return true;
        } catch (Exception e) {
            log.debug("[OBS] folderExists failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeFolder(String bucketName, String folderName) {
        try {
            String key = normalizeFolderPath(folderName);
            if (!doFolderExists(bucketName, key)) {
                try (InputStream emptyStream = InputStream.nullInputStream()) {
                    PutObjectRequest request = new PutObjectRequest(bucketName, key, emptyStream);
                    obsClient.putObject(request);
                    log.info("[OBS] make Folder success folderName:{}", key);
                }
            }
        } catch (Exception e) {
            log.error("[OBS] make Folder failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    protected void doPutObject(String bucketName, String objectName,
                               InputStream inputStream, long size, String contentType) {
        try {
            PutObjectRequest request = new PutObjectRequest(bucketName, objectName, inputStream);
            com.obs.services.model.ObjectMetadata metadata = new com.obs.services.model.ObjectMetadata(); // FQN-OK: name conflict with ObjectMetadata
            metadata.setContentLength(size);
            if (StringUtils.isNotBlank(contentType)) {
                metadata.setContentType(contentType);
            }
            request.setMetadata(metadata);
            obsClient.putObject(request);
        } catch (Exception e) {
            log.error("[OBS] doPutObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    protected InputStream doGetObject(String bucketName, String objectName,
                                      Long offset, Long length) {
        try {
            GetObjectRequest request = new GetObjectRequest(bucketName, objectName);
            long safeOffset = Objects.requireNonNullElse(offset, 0L);
            long safeLength = Objects.requireNonNullElse(length, 0L);

            if (safeOffset >= 0 && safeLength > 0) {
                request.setRangeStart(safeOffset);
                request.setRangeEnd(safeOffset + safeLength - 1);
            }
            ObsObject obsObject = obsClient.getObject(request);
            return obsObject.getObjectContent();
        } catch (Exception e) {
            log.error("[OBS] doGetObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    protected void doRemoveObject(String bucketName, String objectName) {
        try {
            DeleteObjectRequest request = new DeleteObjectRequest(bucketName, objectName);
            obsClient.deleteObject(request);
        } catch (Exception e) {
            log.error("[OBS] doRemoveObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    protected String buildObjectUrl(String bucketName, String objectName) {
        String ep = endpoint != null ? endpoint : "";
        if (StringUtils.isBlank(ep)) {
            return "";
        }
        String protocol = ep.startsWith("https") ? "https://" : "http://";
        String cleanEndpoint = ep.replaceFirst("^https?://", "");
        return String.format("%s%s.%s/%s", protocol, bucketName, cleanEndpoint, objectName);
    }

    @Override
    protected String buildPrivateUrl(String bucketName, String objectName) {
        try {
            TemporarySignatureRequest request = new TemporarySignatureRequest(
                    HttpMethodEnum.GET, bucketName, objectName, null, 3600);
            TemporarySignatureResponse response = obsClient.createTemporarySignature(request);
            return response.getSignedUrl();
        } catch (Exception e) {
            log.error("[OBS] generate private url failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return "";
        }
    }

    @Override
    protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
        try {
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);
            InitiateMultipartUploadResult response = obsClient.initiateMultipartUpload(request);
            String uploadId = response.getUploadId();
            log.info("[OBS] chunked upload initiated, bucket={}, object={}", bucketName, objectName);
            return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
        } catch (Exception e) {
            log.error("[OBS] doInitiateMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_INIT_FAILED);
        }
    }

    @Override
    protected void doUploadPart(String bucketName, String chunkObjectName,
                                String uploadId, int partNumber,
                                InputStream inputStream, long size) {
        try {
            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(bucketName);
            request.setObjectKey(chunkObjectName);
            request.setUploadId(uploadId);
            request.setPartNumber(partNumber);
            request.setInput(inputStream);
            request.setPartSize(size);
            obsClient.uploadPart(request);
            log.info("[OBS] chunk uploaded, bucket={}, chunk={}, part={}",
                    bucketName, chunkObjectName, partNumber);
        } catch (Exception e) {
            log.error("[OBS] doUploadPart failed, bucket={}, chunk={}, part={}, message={}",
                    bucketName, chunkObjectName, partNumber, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doCompleteMultipartUpload(String bucketName, String objectName,
                                             String uploadId, List<Integer> partNumbers) {
        try {
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            ListPartsResult listPartsResponse = obsClient.listParts(listPartsRequest);

            Map<Integer, Multipart> uploadedPartMap = listPartsResponse.getMultipartList().stream()
                    .collect(Collectors.toMap(Multipart::getPartNumber, Function.identity()));

            List<PartEtag> partETags = partNumbers.stream()
                    .map(partNumber -> {
                        Multipart multipart = uploadedPartMap.get(partNumber);
                        if (multipart == null || StringUtils.isBlank(multipart.getEtag())) {
                            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
                        }
                        return new PartEtag(multipart.getEtag(), partNumber);
                    })
                    .sorted(Comparator.comparingInt(PartEtag::getPartNumber))
                    .toList();

            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                    bucketName, objectName, uploadId, partETags);
            obsClient.completeMultipartUpload(request);
            log.info("[OBS] chunked upload completed, bucket={}, object={}, parts={}",
                    bucketName, objectName, partETags.size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OBS] doCompleteMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    @Override
    protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
            obsClient.abortMultipartUpload(request);
        } catch (Exception e) {
            log.warn("[OBS] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    @Override
    protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
        try {
            ListPartsRequest request = new ListPartsRequest(bucketName, objectName, uploadId);
            ListPartsResult response = obsClient.listParts(request);

            return response.getMultipartList().stream()
                    .map(multipart -> new PartInfo(multipart.getPartNumber(), multipart.getEtag(), multipart.getSize()))
                    .toList();
        } catch (Exception e) {
            log.warn("[OBS] listParts failed, object={}, message={}", objectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    protected String normalizeObjectKey(String objectKey) {
        return StringUtils.removeStart(objectKey, "/");
    }

    /**
     * 关闭 OBS 客户端并释放资源
     */
    @Override
    public void close() {
        try {
            if (obsClient != null) {
                obsClient.close();
                log.info("[OBS] ObsClient closed successfully");
            }
        } catch (Exception e) {
            log.error("[OBS] ObsClient close failed: {}", e.getMessage());
        }
    }

    /**
     * 规范化文件夹路径，确保以 "/" 结尾
     *
     * @param folderName 文件夹名称
     * @return 以 "/" 结尾的文件夹路径
     */
    private String normalizeFolderPath(String folderName) {
        return folderName.endsWith(FileConstant.DIR_SPLIT)
                ? folderName
                : folderName + FileConstant.DIR_SPLIT;
    }

    @Override
    protected ObjectMetadata doGetMetadata(String bucketName, String objectName) {
        try {
            ObsObject obsObject = obsClient.getObject(bucketName, objectName);
            if (obsObject == null) {
                return null;
            }
            com.obs.services.model.ObjectMetadata obsMetadata = obsObject.getMetadata(); // FQN-OK: name conflict with ObjectMetadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectName(objectName);
            metadata.setBucketName(bucketName);
            if (obsMetadata != null) {
                metadata.setSize(obsMetadata.getContentLength());
                metadata.setContentType(obsMetadata.getContentType());
                metadata.setETag(obsMetadata.getEtag());
                Date lastModified = obsMetadata.getLastModified();
                metadata.setLastModified(lastModified != null
                        ? LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault())
                        : null);
            }
            metadata.setDirectory(false);
            return metadata;
        } catch (Exception e) {
            log.error("[OBS] doGetMetadata failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return null;
        }
    }

    @Override
    protected ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        List<ObjectMetadata> objects = new ArrayList<>();
        try {
            ObjectListing objectListing;
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucketName);
            request.setMaxKeys(maxKeys + 1);
            if (prefix != null && !prefix.isEmpty()) {
                request.setPrefix(prefix);
            }
            if (cursor != null && !cursor.isEmpty()) {
                request.setMarker(cursor);
            }
            objectListing = obsClient.listObjects(request);

            boolean hasMore = objectListing.isTruncated();
            String nextCursor = hasMore ? objectListing.getNextMarker() : null;

            for (ObsObject obsObject : objectListing.getObjects()) {
                if (objects.size() >= maxKeys) {
                    break;
                }
                com.obs.services.model.ObjectMetadata obsMetadata = obsObject.getMetadata(); // FQN-OK: name conflict with ObjectMetadata
                ObjectMetadata om = new ObjectMetadata();
                om.setObjectName(obsObject.getObjectKey());
                om.setBucketName(bucketName);
                if (obsMetadata != null) {
                    om.setSize(obsMetadata.getContentLength());
                    om.setETag(obsMetadata.getEtag());
                    Date lastModified = obsMetadata.getLastModified();
                    om.setLastModified(lastModified != null
                            ? LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault())
                            : null);
                }
                om.setDirectory(false);
                objects.add(om);
            }

            ListObjectsResult result = new ListObjectsResult();
            result.setObjects(objects);
            result.setHasMore(hasMore);
            result.setNextCursor(nextCursor);
            result.setObjectCount(objects.size());
            return result;
        } catch (Exception e) {
            log.error("[OBS] doListObjects failed, bucket={}, prefix={}, message={}",
                    bucketName, prefix, e.getMessage());
            throw new BusinessException(FileExceptionCode.OBJECT_LIST_FAILED);
        }
    }

    @Override
    public PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires) {
        log.warn("[OBS] generateUploadPolicy is not supported, use STS temporary access");
        return null;
    }
}
