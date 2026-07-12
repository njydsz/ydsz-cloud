package com.njydsz.pmis.common.file.storage.platform;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.model.*;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.config.FileUploadProperties;
import com.njydsz.pmis.common.file.constant.FileConstant;
import com.njydsz.pmis.common.file.domain.ChunkedUploadResult;
import com.njydsz.pmis.common.file.domain.ListObjectsResult;
import com.njydsz.pmis.common.file.domain.ObjectMetadata;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.file.storage.AbstractFileStorage;
import com.njydsz.pmis.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云 OSS 对象存储实现
 * <p>继承 {@link AbstractFileStorage}，
 * 将操作翻译为阿里云 OSS Java SDK 的原生 API 调用。
 *
 * <p>分片上传使用原生 multipart upload 协议：
 * InitiateMultipartUpload / UploadPart / ListParts / CompleteMultipartUpload / AbortMultipartUpload
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class OssStorage extends AbstractFileStorage {

    private final OSS ossClient;
    private final String endpoint;

    /**
     * 构建 OSS 客户端
     *
     * @param config 存储配置（endpoint/accessKey/secretKey/bucket）
     */
    public OssStorage(FileProperties config) {
        this(config, null);
    }

    /**
     * 构建 OSS 客户端
     *
     * @param config 存储配置（endpoint/accessKey/secretKey/bucket）
     * @param uploadProps 分片上传配置
     */
    public OssStorage(FileProperties config, FileUploadProperties uploadProps) {
        super(config, uploadProps);
        try {
            ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
            if (config.getConnectionTimeout() != null) {
                conf.setConnectionTimeout(config.getConnectionTimeout());
            }
            if (config.getSocketTimeout() != null) {
                conf.setSocketTimeout(config.getSocketTimeout());
            }
            if (config.getMaxConnections() != null) {
                conf.setMaxConnections(config.getMaxConnections());
            }
            this.ossClient = new OSSClientBuilder().build(
                    config.getEndpoint(),
                    config.getAccessKey(),
                    config.getSecretKey(),
                    conf);
            this.endpoint = config.getEndpoint();
        } catch (Exception e) {
            log.error("[OSS] OSSClient build failed: {}", e.getMessage());
            throw new BusinessException(FileExceptionCode.STORAGE_CLIENT_BUILD_FAILED);
        }
    }

    @Override
    protected boolean doBucketExists(String bucketName) {
        try {
            return ossClient.doesBucketExist(bucketName);
        } catch (Exception e) {
            log.error("[OSS] bucketExists failed, bucket={}, message={}", bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeBucket(String bucketName) {
        try {
            if (!doBucketExists(bucketName)) {
                ossClient.createBucket(bucketName);
                log.info("[OSS] make Bucket success bucketName:{}", bucketName);
            }
        } catch (Exception e) {
            log.error("[OSS] make Bucket failed, bucket={}, message={}", bucketName, e.getMessage());
            throw new BusinessException(FileExceptionCode.BUCKET_CREATE_FAILED);
        }
    }

    @Override
    protected boolean doFolderExists(String bucketName, String folderName) {
        try {
            String key = folderName.endsWith(FileConstant.DIR_SPLIT)
                    ? folderName : folderName + FileConstant.DIR_SPLIT;
            return ossClient.doesObjectExist(bucketName, key);
        } catch (Exception e) {
            log.debug("[OSS] folderExists failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeFolder(String bucketName, String folderName) {
        try {
            String key = folderName.endsWith(FileConstant.DIR_SPLIT)
                    ? folderName : folderName + FileConstant.DIR_SPLIT;
            if (!doFolderExists(bucketName, key)) {
                InputStream emptyStream = new ByteArrayInputStream(new byte[]{});
                ossClient.putObject(bucketName, key, emptyStream);
                log.info("[OSS] make Folder success folderName:{}", key);
            }
        } catch (Exception e) {
            log.error("[OSS] make Folder failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    protected void doPutObject(String bucketName, String objectName,
                               InputStream inputStream, long size, String contentType) {
        try {
            ossClient.putObject(bucketName, objectName, inputStream);
        } catch (Exception e) {
            log.error("[OSS] doPutObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    protected InputStream doGetObject(String bucketName, String objectName,
                                       Long offset, Long length) {
        try {
            GetObjectRequest request = new GetObjectRequest(bucketName, objectName);
            if (offset != null && offset >= 0 && length != null && length > 0) {
                request.setRange(offset, offset + length - 1);
            }
            OSSObject ossObject = ossClient.getObject(request);
            return ossObject.getObjectContent();
        } catch (Exception e) {
            log.error("[OSS] doGetObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    protected void doRemoveObject(String bucketName, String objectName) {
        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (Exception e) {
            log.error("[OSS] doRemoveObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    protected String buildObjectUrl(String bucketName, String objectName) {
        return String.format("https://%s.%s/%s", bucketName, endpoint, objectName);
    }

    @Override
    protected String buildPrivateUrl(String bucketName, String objectName) {
        try {
            return ossClient.generatePresignedUrl(bucketName, objectName,
                    new Date(System.currentTimeMillis() + 3600 * 1000)).toString();
        } catch (Exception e) {
            log.error("[OSS] generate private url failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return "";
        }
    }

    @Override
    protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
        try {
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);
            InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
            String uploadId = result.getUploadId();
            log.info("[OSS] chunked upload initiated, bucket={}, object={}", bucketName, objectName);
            return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
        } catch (Exception e) {
            log.error("[OSS] doInitiateMultipartUpload failed, bucket={}, object={}, message={}",
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
            request.setKey(chunkObjectName);
            request.setUploadId(uploadId);
            request.setPartNumber(partNumber);
            request.setInputStream(inputStream);
            request.setPartSize(size);
            ossClient.uploadPart(request);
            log.info("[OSS] chunk uploaded, bucket={}, chunk={}, part={}",
                    bucketName, chunkObjectName, partNumber);
        } catch (Exception e) {
            log.error("[OSS] doUploadPart failed, bucket={}, chunk={}, part={}, message={}",
                    bucketName, chunkObjectName, partNumber, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doCompleteMultipartUpload(String bucketName, String objectName,
                                           String uploadId, List<Integer> partNumbers) {
        try {
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            PartListing partListing = ossClient.listParts(listPartsRequest);
            Map<Integer, PartSummary> uploadedPartMap = new HashMap<>();
            for (PartSummary uploadedPart : partListing.getParts()) {
                uploadedPartMap.put(uploadedPart.getPartNumber(), uploadedPart);
            }
            List<PartETag> partETags = new ArrayList<>();
            for (Integer partNumber : partNumbers) {
                PartSummary uploadedPart = uploadedPartMap.get(partNumber);
                if (uploadedPart == null || StringUtils.isBlank(uploadedPart.getETag())) {
                    throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
                }
                partETags.add(new PartETag(partNumber, uploadedPart.getETag()));
            }
            partETags.sort(Comparator.comparingInt(PartETag::getPartNumber));
            CompleteMultipartUploadRequest request =
                    new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);
            ossClient.completeMultipartUpload(request);
            log.info("[OSS] chunked upload completed, bucket={}, object={}, parts={}",
                    bucketName, objectName, partETags.size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OSS] doCompleteMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    @Override
    protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            AbortMultipartUploadRequest abortRequest = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
            ossClient.abortMultipartUpload(abortRequest);
        } catch (Exception e) {
            log.warn("[OSS] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    @Override
    protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        try {
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            PartListing partListing = ossClient.listParts(listPartsRequest);
            for (PartSummary part : partListing.getParts()) {
                parts.add(new PartInfo(part.getPartNumber(), part.getETag(), part.getSize()));
            }
        } catch (Exception e) {
            log.warn("[OSS] listParts failed, object={}, message={}", objectName, e.getMessage());
        }
        return parts;
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
            com.aliyun.oss.model.ObjectMetadata ossMetadata = ossClient.getObjectMetadata(bucketName, objectName);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectName(objectName);
            metadata.setBucketName(bucketName);
            metadata.setSize(ossMetadata.getContentLength());
            metadata.setContentType(ossMetadata.getContentType());
            metadata.setETag(ossMetadata.getETag());
            metadata.setLastModified(ossMetadata.getLastModified() != null
                    ? LocalDateTime.ofInstant(ossMetadata.getLastModified().toInstant(), ZoneId.systemDefault())
                    : null);
            metadata.setIsDirectory(false);
            return metadata;
        } catch (Exception e) {
            log.error("[OSS] doGetMetadata failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return null;
        }
    }

    @Override
    protected ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        List<ObjectMetadata> objects = new ArrayList<>();
        try {
            ListObjectsRequest request = new ListObjectsRequest(bucketName);
            if (prefix != null && !prefix.isEmpty()) {
                request.setPrefix(prefix);
            }
            if (cursor != null && !cursor.isEmpty()) {
                request.setMarker(cursor);
            }
            request.setMaxKeys(maxKeys + 1);
            ObjectListing listing = ossClient.listObjects(request);
            boolean hasMore = listing.isTruncated();
            String nextCursor = hasMore ? listing.getNextMarker() : null;
            for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                if (objects.size() < maxKeys) {
                    ObjectMetadata om = new ObjectMetadata();
                    om.setObjectName(summary.getKey());
                    om.setBucketName(bucketName);
                    om.setSize(summary.getSize());
                    om.setLastModified(summary.getLastModified() != null
                            ? LocalDateTime.ofInstant(summary.getLastModified().toInstant(), ZoneId.systemDefault())
                            : null);
                    om.setETag(summary.getETag());
                    om.setIsDirectory(false);
                    objects.add(om);
                }
            }
            ListObjectsResult result = new ListObjectsResult();
            result.setObjects(objects);
            result.setHasMore(hasMore);
            result.setNextCursor(nextCursor);
            result.setObjectCount(objects.size());
            return result;
        } catch (Exception e) {
            log.error("[OSS] doListObjects failed, bucket={}, prefix={}, message={}",
                    bucketName, prefix, e.getMessage());
            throw new BusinessException(FileExceptionCode.OBJECT_LIST_FAILED);
        }
    }

    @Override
    protected String doGeneratePresignedUrl(String bucketName, String objectName, int expireSeconds) {
        try {
            java.util.Date expiration = new java.util.Date(System.currentTimeMillis() + (long) expireSeconds * 1000);
            return ossClient.generatePresignedUrl(bucketName, objectName, expiration).toString();
        } catch (Exception e) {
            log.error("[OSS] generatePresignedUrl failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage(), e);
            return buildObjectUrl(bucketName, objectName);
        }
    }
}