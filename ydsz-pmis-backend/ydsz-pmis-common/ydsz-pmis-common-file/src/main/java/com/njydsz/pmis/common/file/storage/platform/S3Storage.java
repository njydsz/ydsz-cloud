package com.njydsz.pmis.common.file.storage.platform;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.config.FileUploadProperties;
import com.njydsz.pmis.common.file.constant.FileConstant;
import com.njydsz.pmis.common.file.domain.ChunkedUploadResult;
import com.njydsz.pmis.common.file.domain.ListObjectsResult;
import com.njydsz.pmis.common.file.domain.ObjectMetadata;
import com.njydsz.pmis.common.file.domain.PolicyResult;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.file.storage.AbstractFileStorage;
import com.njydsz.pmis.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS S3 / S3 兼容对象存储实现
 * <p>继承 {@link AbstractFileStorage}，
 * 将操作翻译为 AWS S3 SDK v2 的原生 API 调用。
 *
 * <p>分片上传使用原生 multipart upload 协议：
 * CreateMultipartUpload / UploadPart / ListParts / CompleteMultipartUpload / AbortMultipartUpload
 *
 * @author ydsz-pmis-team
 * 
 * 
 */
@Slf4j
public class S3Storage extends AbstractFileStorage {

    /** S3 客户端 */
    private final S3Client s3Client;
    /** S3 预签名器 */
    private final S3Presigner s3Presigner;
    /** 存储桶名称 */
    @SuppressWarnings("unused")
    private final String bucket;
    /** AWS 区域 */
    private final String region;
    /** AWS SecretKey（用于生成上传策略签名） */
    private final String secretKey;

    /**
     * 构建 S3 客户端与签名器
     * <p>支持 AWS 官方 S3 与兼容 S3 协议的对象存储（通过 endpointOverride 指定）。
     *
     * @param config 存储配置
     */
    public S3Storage(FileProperties config) {
        this(config, null);
    }

    /**
     * 构建 S3 客户端与签名器
     * <p>支持 AWS 官方 S3 与兼容 S3 协议的对象存储（通过 endpointOverride 指定）。
     *
     * @param config 存储配置
     * @param uploadProps 分片上传配置
     */
    public S3Storage(FileProperties config, FileUploadProperties uploadProps) {
        super(config, uploadProps);
        try {
            String accessKey = config.getAccessKey();
            String secretKey = config.getSecretKey();
            String endpoint = config.getEndpoint();
            this.bucket = config.getBucket();

            if (StringUtils.isNotBlank(config.getRegion())) {
                this.region = config.getRegion();
            } else {
                if (endpoint != null && endpoint.contains(".")) {
                    String[] parts = endpoint.split("\\.");
                    if (parts.length >= 2) {
                        this.region = parts[1];
                    } else {
                        throw new BizException(FileExceptionCode.STORAGE_CONFIG_INVALID);
                    }
                } else {
                    throw new BizException(FileExceptionCode.STORAGE_CONFIG_INVALID);
                }
            }

            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
            this.secretKey = secretKey;

            S3ClientBuilder builder = S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .region(Region.of(region))
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallAttemptTimeout(Duration.ofMillis(config.getConnectionTimeout()))
                            .apiCallTimeout(Duration.ofMillis(config.getSocketTimeout()))
                            .build());

            if (StringUtils.isNotBlank(endpoint)) {
                builder.endpointOverride(URI.create(endpoint));
                builder.serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build());
            }

            this.s3Client = builder.build();

            S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .region(Region.of(region));

            if (StringUtils.isNotBlank(endpoint)) {
                presignerBuilder.endpointOverride(URI.create(endpoint));
            }

            this.s3Presigner = presignerBuilder.build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[S3] S3Client build failed: {}", e.getMessage());
            throw new BizException(FileExceptionCode.STORAGE_CLIENT_BUILD_FAILED);
        }
    }

    @Override
    protected boolean doBucketExists(String bucketName) {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("[S3] bucketExists failed, bucket={}, code={}, message={}",
                    bucketName, e.statusCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[S3] bucketExists unexpected error, bucket={}, message={}",
                    bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeBucket(String bucketName) {
        try {
            if (!doBucketExists(bucketName)) {
                CreateBucketRequest request = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(request);
                log.info("[S3] make Bucket success bucketName:{}", bucketName);
            }
        } catch (Exception e) {
            log.error("[S3] make Bucket failed, bucket={}, message={}", bucketName, e.getMessage());
            throw new BizException(FileExceptionCode.BUCKET_CREATE_FAILED);
        }
    }

    @Override
    protected boolean doFolderExists(String bucketName, String folderName) {
        try {
            String key = folderName.endsWith(FileConstant.DIR_SPLIT)
                    ? folderName : folderName + FileConstant.DIR_SPLIT;
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.debug("[S3] folderExist failed, bucket={}, folder={}, code={}, message={}",
                    bucketName, folderName, e.statusCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("[S3] folderExist unexpected error, bucket={}, folder={}, message={}",
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
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.putObject(request, RequestBody.fromInputStream(emptyStream, 0));
                log.info("[S3] make Folder success folderName:{}", key);
            }
        } catch (Exception e) {
            log.error("[S3] make Folder failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            throw new BizException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    protected void doPutObject(String bucketName, String objectName,
                               InputStream inputStream, long size, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentLength(size)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
        } catch (Exception e) {
            log.error("[S3] doPutObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BizException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    protected InputStream doGetObject(String bucketName, String objectName,
                                       Long offset, Long length) {
        try {
            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName);
            if (offset != null && offset >= 0 && length != null && length > 0) {
                requestBuilder.range("bytes=" + offset + "-" + (offset + length - 1));
            }
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(requestBuilder.build());
            return s3Object;
        } catch (NoSuchKeyException e) {
            throw new BizException(FileExceptionCode.FILE_NOT_FOUND);
        } catch (Exception e) {
            log.error("[S3] doGetObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BizException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    protected void doRemoveObject(String bucketName, String objectName) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            s3Client.deleteObject(request);
        } catch (Exception e) {
            log.error("[S3] doRemoveObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BizException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    protected String buildObjectUrl(String bucketName, String objectName) {
        if (StringUtils.isNotBlank(endpoint)) {
            String cleanEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            String endpointWithoutProtocol = cleanEndpoint.replace("http://", "").replace("https://", "");
            String protocol = cleanEndpoint.startsWith("http://") ? "http://" : "https://";
            return protocol + endpointWithoutProtocol + "/" + bucketName + "/" + objectName;
        } else {
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, objectName);
        }
    }

    @Override
    protected String buildPrivateUrl(String bucketName, String objectName) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .getObjectRequest(getObjectRequest)
                    .build();
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("[S3] generate private url failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return "";
        }
    }

    @Override
    protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
        try {
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
            String uploadId = response.uploadId();
            log.info("[S3] chunked upload initiated, bucket={}, object={}", bucketName, objectName);
            return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
        } catch (Exception e) {
            log.error("[S3] doInitiateMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BizException(FileExceptionCode.MULTIPART_UPLOAD_INIT_FAILED);
        }
    }

    @Override
    protected void doUploadPart(String bucketName, String chunkObjectName,
                               String uploadId, int partNumber,
                               InputStream inputStream, long size) {
        try {
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(chunkObjectName)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(size)
                    .build();
            s3Client.uploadPart(request, RequestBody.fromInputStream(inputStream, size));
            log.info("[S3] chunk uploaded, bucket={}, chunk={}, part={}",
                    bucketName, chunkObjectName, partNumber);
        } catch (Exception e) {
            log.error("[S3] doUploadPart failed, bucket={}, chunk={}, part={}, message={}",
                    bucketName, chunkObjectName, partNumber, e.getMessage());
            throw new BizException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doCompleteMultipartUpload(String bucketName, String objectName,
                                           String uploadId, List<Integer> partNumbers) {
        try {
            ListPartsResponse listPartsResponse = s3Client.listParts(ListPartsRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .build());
            Map<Integer, Part> uploadedPartMap = new HashMap<>();
            for (Part uploadedPart : listPartsResponse.parts()) {
                uploadedPartMap.put(uploadedPart.partNumber(), uploadedPart);
            }
            List<CompletedPart> completedParts = new ArrayList<>();
            for (Integer partNumber : partNumbers) {
                Part uploadedPart = uploadedPartMap.get(partNumber);
                if (uploadedPart == null || StringUtils.isBlank(uploadedPart.eTag())) {
                    throw new BizException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
                }
                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadedPart.eTag())
                        .build());
            }
            completedParts.sort(Comparator.comparingInt(CompletedPart::partNumber));
            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();
            s3Client.completeMultipartUpload(request);
            log.info("[S3] chunked upload completed, bucket={}, object={}, parts={}",
                    bucketName, objectName, completedParts.size());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[S3] doCompleteMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BizException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    @Override
    protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .build();
            s3Client.abortMultipartUpload(abortRequest);
        } catch (Exception e) {
            log.warn("[S3] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    @Override
    protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        try {
            ListPartsResponse listPartsResponse = s3Client.listParts(ListPartsRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .build());
            for (Part part : listPartsResponse.parts()) {
                parts.add(new PartInfo(part.partNumber(), part.eTag(), part.size()));
            }
        } catch (Exception e) {
            log.warn("[S3] listParts failed, object={}, message={}", objectName, e.getMessage());
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
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectName(objectName);
            metadata.setBucketName(bucketName);
            metadata.setSize(response.contentLength());
            metadata.setContentType(response.contentType());
            metadata.setETag(response.eTag());
            metadata.setLastModified(response.lastModified() != null
                    ? LocalDateTime.ofInstant(response.lastModified(), ZoneId.systemDefault())
                    : null);
            metadata.setIsDirectory(false);
            return metadata;
        } catch (Exception e) {
            log.error("[S3] doGetMetadata failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return null;
        }
    }

    @Override
    protected ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        List<ObjectMetadata> objects = new ArrayList<>();
        try {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(maxKeys + 1);
            if (prefix != null && !prefix.isEmpty()) {
                requestBuilder.prefix(prefix);
            }
            if (cursor != null && !cursor.isEmpty()) {
                requestBuilder.continuationToken(cursor);
            }
            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
            boolean hasMore = response.isTruncated();
            String nextCursor = response.nextContinuationToken();
            for (S3Object s3Object : response.contents()) {
                if (objects.size() < maxKeys) {
                    ObjectMetadata om = new ObjectMetadata();
                    om.setObjectName(s3Object.key());
                    om.setBucketName(bucketName);
                    om.setSize(s3Object.size());
                    om.setLastModified(s3Object.lastModified() != null
                            ? LocalDateTime.ofInstant(s3Object.lastModified(), ZoneId.systemDefault())
                            : null);
                    om.setETag(s3Object.eTag());
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
            log.error("[S3] doListObjects failed, bucket={}, prefix={}, message={}",
                    bucketName, prefix, e.getMessage());
            throw new BizException(FileExceptionCode.OBJECT_LIST_FAILED);
        }
    }

    @Override
    public PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires) {
        try {
            String resolvedBucket = resolveBucketName(bucketName);
            String resolvedPrefix = objectNamePrefix != null ? objectNamePrefix : "";
            int expirySeconds = expires != null ? expires :
                    (fileProperties.getTemporarySignatureExpiry() != null ? fileProperties.getTemporarySignatureExpiry() : 3600);

            long expirationTime = System.currentTimeMillis() / 1000 + expirySeconds;

            String safePrefix = escapeJsonString(resolvedPrefix);
            String safeBucket = escapeJsonString(resolvedBucket);
            String policyJson = String.format(
                    "{\"expiration\":%d,\"conditions\":[[\"starts-with\",\"$key\",\"%s\"],[\"eq\",\"$bucket\",\"%s\"]]}",
                    expirationTime, safePrefix, safeBucket);

            String policyBase64 = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(policyBase64.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            String resolvedEndpoint = endpoint;
            if (StringUtils.isBlank(resolvedEndpoint)) {
                resolvedEndpoint = String.format("https://%s.s3.%s.amazonaws.com", resolvedBucket, region);
            }

            PolicyResult result = new PolicyResult();
            result.setAccessKeyId(fileProperties.getAccessKey());
            result.setPolicy(policyBase64);
            result.setSignature(signature);
            result.setBucket(resolvedBucket);
            result.setObjectKeyPrefix(resolvedPrefix);
            result.setExpiration(expirationTime);
            result.setRegion(region);
            result.setEndpoint(resolvedEndpoint);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[S3] generateUploadPolicy failed, bucket={}, prefix={}, message={}",
                    bucketName, objectNamePrefix, e.getMessage());
            throw new BizException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }
}