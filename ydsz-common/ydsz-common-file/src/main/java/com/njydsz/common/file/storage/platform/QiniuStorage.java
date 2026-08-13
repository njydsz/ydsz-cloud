package com.njydsz.common.file.storage.platform;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.config.FileProperties;
import com.njydsz.common.file.config.FileUploadProperties;
import com.njydsz.common.file.constant.FileConstant;
import com.njydsz.common.file.domain.ChunkedUploadResult;
import com.njydsz.common.file.domain.ListObjectsResult;
import com.njydsz.common.file.domain.ObjectMetadata;
import com.njydsz.common.file.exception.FileExceptionCode;
import com.njydsz.common.file.storage.AbstractFileStorage;
import com.njydsz.common.file.storage.MultipartContextStore;
import com.njydsz.common.util.string.StringUtils;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Client;
import com.qiniu.storage.ApiUploadV2AbortUpload;
import com.qiniu.storage.ApiUploadV2CompleteUpload;
import com.qiniu.storage.ApiUploadV2InitUpload;
import com.qiniu.storage.ApiUploadV2UploadPart;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;

import lombok.extern.slf4j.Slf4j;

/**
 * 七牛云 Kodo 对象存储实现。
 *
 * <p>继承 {@link AbstractFileStorage}，将操作翻译为七牛云 Java SDK 的原生 API 调用。
 *
 * <h3>分片上传协议</h3>
 * <p>使用七牛云 Upload V2 协议（不同于 S3 multipart upload），完整支持分片上传生命周期：
 * <ol>
 *   <li>{@code ApiUploadV2InitUpload}：初始化分片上传，获取 uploadId</li>
 *   <li>{@code ApiUploadV2UploadPart}：上传单个分片</li>
 *   <li>{@code ApiUploadV2CompleteUpload}：合并所有分片为最终对象</li>
 *   <li>{@code ApiUploadV2AbortUpload}：取消上传并清理已上传分片</li>
 * </ol>
 *
 * <h3>私有空间下载</h3>
 * <p>通过 {@link Auth#privateDownloadUrl(String, long)} 生成临时下载链接，
 * 支持自定义过期时间。公开空间可直接通过 CDN 域名访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AbstractFileStorage
 * @see Auth
 * @see UploadManager
 * @see BucketManager
 */
@Slf4j
public class QiniuStorage extends AbstractFileStorage {

    private final Auth auth;
    private final UploadManager uploadManager;
    private final BucketManager bucketManager;
    private final Client httpClient;
    private final ApiUploadV2InitUpload initUploadApi;
    private final ApiUploadV2UploadPart uploadPartApi;
    private final ApiUploadV2CompleteUpload completeUploadApi;
    private final ApiUploadV2AbortUpload abortUploadApi;
    private final String domain;

    /**
     * 构建七牛客户端与 Upload V2 API
     * <p>UploadManager 用于常规上传，Upload V2 API 用于分片上传。
     *
     * @param config 存储配置（accessKey/secretKey/bucket/domain）
     */
    public QiniuStorage(FileProperties config) {
        this(config, null);
    }

    /**
     * 构建七牛客户端与 Upload V2 API
     * <p>UploadManager 用于常规上传，Upload V2 API 用于分片上传。
     *
     * @param config 存储配置（accessKey/secretKey/bucket/domain）
     * @param uploadProps 分片上传配置
     */
    public QiniuStorage(FileProperties config, FileUploadProperties uploadProps) {
        super(config, uploadProps);
        try {
            this.auth = Auth.create(config.getAccessKey(), config.getSecretKey());
            this.domain = config.getDomain();

            Configuration cfg = new Configuration(Region.autoRegion());
            if (config.getConnectionTimeout() != null) {
                cfg.connectTimeout = config.getConnectionTimeout() / 1000;
            }
            if (config.getSocketTimeout() != null) {
                cfg.readTimeout = config.getSocketTimeout() / 1000;
            }

            this.uploadManager = new UploadManager(cfg);
            this.httpClient = new Client(cfg);
            this.initUploadApi = new ApiUploadV2InitUpload(httpClient);
            this.uploadPartApi = new ApiUploadV2UploadPart(httpClient);
            this.completeUploadApi = new ApiUploadV2CompleteUpload(httpClient);
            this.abortUploadApi = new ApiUploadV2AbortUpload(httpClient);
            this.bucketManager = new BucketManager(auth, cfg);
        } catch (Exception e) {
            log.error("[Qiniu] QiniuClient build failed: {}", e.getMessage());
            throw new BusinessException(FileExceptionCode.STORAGE_CLIENT_BUILD_FAILED);
        }
    }

    @Override
    protected boolean doBucketExists(String bucketName) {
        try {
            String[] buckets = bucketManager.buckets();
            return Arrays.asList(buckets).contains(bucketName);
        } catch (Exception e) {
            log.error("[Qiniu] bucketExists failed, bucket={}, message={}", bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    protected void doMakeBucket(String bucketName) {
        if (doBucketExists(bucketName)) {
            log.info("[Qiniu] bucket already exists, bucket={}", bucketName);
            return;
        }
        try {
            bucketManager.createBucket(bucketName, "z0");
            log.info("[Qiniu] bucket created, bucket={}", bucketName);
        } catch (Exception e) {
            log.error("[Qiniu] doMakeBucket failed, bucket={}, message={}", bucketName, e.getMessage());
            throw new BusinessException(FileExceptionCode.BUCKET_CREATE_FAILED);
        }
    }

    @Override
    protected boolean doFolderExists(String bucketName, String folderName) {
        try {
            String key = folderName.endsWith(FileConstant.DIR_SPLIT)
                    ? folderName : folderName + FileConstant.DIR_SPLIT;
            FileInfo fileInfo = bucketManager.stat(bucketName, key);
            return fileInfo != null;
        } catch (QiniuException e) {
            if (e.code() == 612) {
                return false;
            }
            log.debug("[Qiniu] folderExists failed, bucket={}, folder={}, code={}, message={}",
                    bucketName, folderName, e.code(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("[Qiniu] folderExist unexpected error, bucket={}, folder={}, message={}",
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
                String upToken = auth.uploadToken(bucketName);
                uploadManager.put(emptyStream, key, upToken, null, "application/octet-stream");
                log.info("[Qiniu] make Folder success folderName:{}", key);
            }
        } catch (Exception e) {
            log.error("[Qiniu] make Folder failed, bucket={}, folder={}, message={}",
                    bucketName, folderName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FOLDER_CREATE_FAILED);
        }
    }

    @Override
    protected void doPutObject(String bucketName, String objectName,
                               InputStream inputStream, long size, String contentType) {
        try {
            String upToken = auth.uploadToken(bucketName);
            uploadManager.put(inputStream, objectName, upToken, null, contentType);
        } catch (Exception e) {
            log.error("[Qiniu] doPutObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    protected InputStream doGetObject(String bucketName, String objectName,
                                       Long offset, Long length) {
        try {
            String urlString = buildPrivateUrl(bucketName, objectName);
            if (StringUtils.isEmpty(urlString)) {
                throw new BusinessException(FileExceptionCode.PRIVATE_URL_GENERATE_FAILED);
            }
            URI uri = URI.create(urlString);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (offset != null && length != null) {
                conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            }
            return conn.getInputStream();
        } catch (Exception e) {
            log.error("[Qiniu] doGetObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    protected void doRemoveObject(String bucketName, String objectName) {
        try {
            bucketManager.delete(bucketName, objectName);
        } catch (Exception e) {
            log.error("[Qiniu] doRemoveObject failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.FILE_DELETE_FAILED);
        }
    }

    @Override
    protected String buildObjectUrl(String bucketName, String objectName) {
        if (StringUtils.isBlank(domain)) {
            return "";
        }
        String domainUrl = domain;
        if (!domainUrl.endsWith("/")) {
            domainUrl += "/";
        }
        return domainUrl + objectName;
    }

    @Override
    protected String buildPrivateUrl(String bucketName, String objectName) {
        if (StringUtils.isBlank(domain)) {
            log.warn("[Qiniu] generate private url failed: domain is not configured");
            return "";
        }
        try {
            String baseUrl = domain.endsWith("/") ? domain + objectName : domain + "/" + objectName;
            return auth.privateDownloadUrl(baseUrl, 3600);
        } catch (Exception e) {
            log.error("[Qiniu] generate private url failed, object={}, message={}",
                    objectName, e.getMessage());
            return "";
        }
    }

    @Override
    protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
        try {
            String upToken = auth.uploadToken(bucketName);
            ApiUploadV2InitUpload.Request request = new ApiUploadV2InitUpload.Request(bucketName, upToken)
                    .setKey(objectName);
            ApiUploadV2InitUpload.Response response = initUploadApi.request(request);
            String uploadId = response.getUploadId();
            multipartContextStore.save(uploadId,
                    new MultipartContextStore.MultipartContextData(uploadId, bucketName, objectName),
                    24 * 3600);
            log.info("[Qiniu] chunked upload initiated, bucket={}, object={}", bucketName, objectName);
            return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
        } catch (Exception e) {
            log.error("[Qiniu] doInitiateMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_INIT_FAILED);
        }
    }

    @Override
    protected void doUploadPart(String bucketName, String chunkObjectName,
                               String uploadId, int partNumber,
                               InputStream inputStream, long size) {
        try {
            String upToken = auth.uploadToken(bucketName);
            ApiUploadV2UploadPart.Request request =
                    new ApiUploadV2UploadPart.Request(bucketName, upToken, uploadId, partNumber)
                            .setKey(chunkObjectName)
                            .setUploadData(inputStream, "application/octet-stream", size);
            ApiUploadV2UploadPart.Response response = uploadPartApi.request(request);
            String eTag = response.getEtag();
            if (StringUtils.isBlank(eTag)) {
                throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
            }
            MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
            if (context == null) {
                context = new MultipartContextStore.MultipartContextData(uploadId, bucketName, chunkObjectName);
            }
            Map<Integer, String> partChunkNames = new HashMap<>(context.partChunkNames());
            partChunkNames.put(partNumber, eTag);
            multipartContextStore.save(uploadId,
                    new MultipartContextStore.MultipartContextData(
                            context.uploadId(), context.bucketName(), context.objectName(),
                            partChunkNames, context.createTime(), System.currentTimeMillis()),
                    24 * 3600);
            log.info("[Qiniu] chunk uploaded, bucket={}, chunk={}, part={}",
                    bucketName, chunkObjectName, partNumber);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Qiniu] doUploadPart failed, bucket={}, chunk={}, part={}, message={}",
                    bucketName, chunkObjectName, partNumber, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
    }

    @Override
    protected void doCompleteMultipartUpload(String bucketName, String objectName,
                                           String uploadId, List<Integer> partNumbers) {
        MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
        if (context == null || !objectName.equals(context.objectName())) {
            safeAbortMultipartUpload(bucketName, objectName, uploadId);
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
        Set<Integer> uniqueParts = new HashSet<>(partNumbers);
        List<Integer> sortedParts = new ArrayList<>(uniqueParts);
        Collections.sort(sortedParts);
        try {
            List<Map<String, Object>> parts = new ArrayList<>();
            for (Integer partNumber : sortedParts) {
                String eTag = context.partChunkNames().get(partNumber);
                if (StringUtils.isBlank(eTag)) {
                    throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
                }
                Map<String, Object> partInfo = new HashMap<>();
                partInfo.put(ApiUploadV2CompleteUpload.Request.PART_NUMBER, partNumber);
                partInfo.put(ApiUploadV2CompleteUpload.Request.PART_ETG, eTag);
                parts.add(partInfo);
            }
            String upToken = auth.uploadToken(bucketName);
            ApiUploadV2CompleteUpload.Request request =
                    new ApiUploadV2CompleteUpload.Request(bucketName, upToken, uploadId, parts)
                            .setKey(objectName);
            completeUploadApi.request(request);
            multipartContextStore.remove(uploadId);
            log.info("[Qiniu] chunked upload completed, bucket={}, object={}, parts={}",
                    bucketName, objectName, sortedParts.size());
        } catch (BusinessException e) {
            safeAbortMultipartUpload(bucketName, objectName, uploadId);
            multipartContextStore.remove(uploadId);
            throw e;
        } catch (Exception e) {
            safeAbortMultipartUpload(bucketName, objectName, uploadId);
            multipartContextStore.remove(uploadId);
            log.error("[Qiniu] doCompleteMultipartUpload failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_COMPLETE_FAILED);
        }
    }

    @Override
    protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            String upToken = auth.uploadToken(bucketName);
            ApiUploadV2AbortUpload.Request request =
                    new ApiUploadV2AbortUpload.Request(bucketName, upToken, uploadId)
                            .setKey(objectName);
            abortUploadApi.request(request);
        } catch (Exception e) {
            log.warn("[Qiniu] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
                    bucketName, objectName, uploadId, e.getMessage());
        }
    }

    @Override
    protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
        return new ArrayList<>();
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
            FileInfo fileInfo = bucketManager.stat(bucketName, objectName);
            if (fileInfo == null) {
                return null;
            }
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setObjectName(objectName);
            metadata.setBucketName(bucketName);
            metadata.setSize(fileInfo.fsize);
            metadata.setETag(fileInfo.hash);
            metadata.setLastModified(fileInfo.putTime > 0
                    ? LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(fileInfo.putTime / 10000),
                            ZoneId.systemDefault())
                    : null);
            metadata.setDirectory(false);
            return metadata;
        } catch (QiniuException e) {
            if (e.code() == 612) {
                return null;
            }
            log.error("[Qiniu] doGetMetadata failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[Qiniu] doGetMetadata failed, bucket={}, object={}, message={}",
                    bucketName, objectName, e.getMessage());
            return null;
        }
    }

    @Override
    protected ListObjectsResult doListObjects(String bucketName, String prefix, String cursor, int maxKeys) {
        log.warn("[Qiniu] doListObjects is not fully implemented, returning empty result");
        return ListObjectsResult.empty();
    }
}
