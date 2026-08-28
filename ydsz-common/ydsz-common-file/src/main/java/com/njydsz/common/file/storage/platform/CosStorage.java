package com.njydsz.common.file.storage.platform;

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

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.AbortMultipartUploadRequest;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.model.CreateBucketRequest;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.HeadBucketRequest;
import com.qcloud.cos.model.InitiateMultipartUploadRequest;
import com.qcloud.cos.model.InitiateMultipartUploadResult;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ListPartsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.PartETag;
import com.qcloud.cos.model.PartListing;
import com.qcloud.cos.model.PartSummary;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.UploadPartRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;

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

/**
 * 腾讯云 COS 对象存储实现。
 *
 * <p>继承 {@link AbstractFileStorage}，将操作翻译为腾讯云 COS Java SDK 的原生 API 调用。
 *
 * <h3>分片上传协议</h3>
 *
 * <p>使用 COS 原生 multipart upload 协议，完整支持分片上传生命周期：
 *
 * <ol>
 *   <li>{@code InitiateMultipartUpload}：初始化分片上传，获取 uploadId
 *   <li>{@code UploadPart}：上传单个分片，返回分片 ETag
 *   <li>{@code ListParts}：列出已上传的分片
 *   <li>{@code CompleteMultipartUpload}：合并所有分片为最终对象
 *   <li>{@code AbortMultipartUpload}：取消上传并清理已上传分片
 * </ol>
 *
 * <h3>预签名 URL</h3>
 *
 * <p>通过 {@code generatePresignedUrl} 生成临时下载/上传链接，支持自定义 HTTP 方法和过期时间。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AbstractFileStorage
 * @see COSClient
 */
@Slf4j
public class CosStorage extends AbstractFileStorage {

  private final COSClient cosClient;

  /** 腾讯云 COS 区域 */
  private final String region;

  /**
   * 构建 COS 客户端
   *
   * <p>当前实现通过 endpoint 解析 region（如 cos.&lt;region&gt;.myqcloud.com）。
   *
   * @param config 存储配置
   */
  public CosStorage(FileProperties config) {
    this(config, null);
  }

  /**
   * 构建 COS 客户端
   *
   * <p>当前实现通过 endpoint 解析 region（如 cos.&lt;region&gt;.myqcloud.com）。
   *
   * @param config 存储配置
   * @param uploadProps 分片上传配置
   */
  public CosStorage(FileProperties config, FileUploadProperties uploadProps) {
    super(config, uploadProps);
    try {
      String secretId = config.getAccessKey();
      String secretKey = config.getSecretKey();
      String endpoint = config.getEndpoint();

      if (endpoint != null && endpoint.contains(".")) {
        String[] parts = endpoint.split("\\.");
        if (parts.length >= 3) {
          this.region = parts[1];
        } else {
          throw new BusinessException(FileExceptionCode.CONFIG_INVALID);
        }
      } else {
        throw new BusinessException(FileExceptionCode.CONFIG_INVALID);
      }

      COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
      ClientConfig clientConfig = new ClientConfig(new Region(region));
      if (config.getConnectionTimeout() != null) {
        clientConfig.setConnectionTimeout(config.getConnectionTimeout());
      }
      if (config.getSocketTimeout() != null) {
        clientConfig.setSocketTimeout(config.getSocketTimeout());
      }
      this.cosClient = new COSClient(cred, clientConfig);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("[COS] COSClient build failed: {}", e.getMessage());
      throw new BusinessException(FileExceptionCode.CONFIG_INVALID);
    }
  }

  @Override
  protected boolean doBucketExists(String bucketName) {
    try {
      HeadBucketRequest request = new HeadBucketRequest(bucketName);
      cosClient.headBucket(request);
      return true;
    } catch (Exception e) {
      log.error("[COS] bucketExists failed, bucket={}, message={}", bucketName, e.getMessage());
      return false;
    }
  }

  @Override
  protected void doMakeBucket(String bucketName) {
    try {
      if (!doBucketExists(bucketName)) {
        CreateBucketRequest request = new CreateBucketRequest(bucketName);
        cosClient.createBucket(request);
        log.info("[COS] make Bucket success bucketName:{}", bucketName);
      }
    } catch (Exception e) {
      log.error("[COS] make Bucket failed, bucket={}, message={}", bucketName, e.getMessage());
      throw new BusinessException(FileExceptionCode.BUCKET_ERROR);
    }
  }

  @Override
  protected boolean doFolderExists(String bucketName, String folderName) {
    try {
      String key =
          folderName.endsWith(FileConstant.DIR_SPLIT)
              ? folderName
              : folderName + FileConstant.DIR_SPLIT;
      com.qcloud.cos.model.ObjectMetadata metadata =
          cosClient.getObjectMetadata(bucketName, key);
      return metadata != null;
    } catch (Exception e) {
      log.debug(
          "[COS] folderExists failed, bucket={}, folder={}, message={}",
          bucketName,
          folderName,
          e.getMessage());
      return false;
    }
  }

  @Override
  protected void doMakeFolder(String bucketName, String folderName) {
    try {
      String key =
          folderName.endsWith(FileConstant.DIR_SPLIT)
              ? folderName
              : folderName + FileConstant.DIR_SPLIT;
      if (!doFolderExists(bucketName, key)) {
        InputStream emptyStream = new ByteArrayInputStream(new byte[] {});
        com.qcloud.cos.model.ObjectMetadata objectMetadata =
            new com.qcloud.cos.model.ObjectMetadata(); // FQN-OK: name conflict with ObjectMetadata
        objectMetadata.setContentLength(0);
        PutObjectRequest putObjectRequest =
            new PutObjectRequest(bucketName, key, emptyStream, objectMetadata);
        cosClient.putObject(putObjectRequest);
        log.info("[COS] make Folder success folderName:{}", key);
      }
    } catch (Exception e) {
      log.error(
          "[COS] make Folder failed, bucket={}, folder={}, message={}",
          bucketName,
          folderName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  protected void doPutObject(
      String bucketName,
      String objectName,
      InputStream inputStream,
      long size,
      String contentType) {
    try {
      com.qcloud.cos.model.ObjectMetadata objectMetadata =
          new com.qcloud.cos.model.ObjectMetadata(); // FQN-OK: name conflict with ObjectMetadata
      objectMetadata.setContentLength(size);
      if (contentType != null) {
        objectMetadata.setContentType(contentType);
      }
      PutObjectRequest putObjectRequest =
          new PutObjectRequest(bucketName, objectName, inputStream, objectMetadata);
      cosClient.putObject(putObjectRequest);
    } catch (Exception e) {
      log.error(
          "[COS] doPutObject failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
    }
  }

  @Override
  protected InputStream doGetObject(
      String bucketName, String objectName, Long offset, Long length) {
    try {
      GetObjectRequest request = new GetObjectRequest(bucketName, objectName);
      if (offset != null && offset >= 0 && length != null && length > 0) {
        request.setRange(offset, offset + length - 1);
      }
      COSObject cosObject = cosClient.getObject(request);
      return cosObject.getObjectContent();
    } catch (Exception e) {
      log.error(
          "[COS] doGetObject failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  protected void doRemoveObject(String bucketName, String objectName) {
    try {
      cosClient.deleteObject(bucketName, objectName);
    } catch (Exception e) {
      log.error(
          "[COS] doRemoveObject failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  protected String buildObjectUrl(String bucketName, String objectName) {
    return String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, region, objectName);
  }

  @Override
  protected String buildPrivateUrl(String bucketName, String objectName) {
    try {
      GeneratePresignedUrlRequest req =
          new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethodName.GET);
      req.setExpiration(new Date(System.currentTimeMillis() + 3600000));
      return cosClient.generatePresignedUrl(req).toString();
    } catch (Exception e) {
      log.error(
          "[COS] generate private url failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      return "";
    }
  }

  @Override
  protected ChunkedUploadResult doInitiateMultipartUpload(String bucketName, String objectName) {
    try {
      InitiateMultipartUploadRequest request =
          new InitiateMultipartUploadRequest(bucketName, objectName);
      InitiateMultipartUploadResult result = cosClient.initiateMultipartUpload(request);
      String uploadId = result.getUploadId();
      log.info("[COS] chunked upload initiated, bucket={}, object={}", bucketName, objectName);
      return new ChunkedUploadResult(objectName, bucketName, uploadId, 0, 0);
    } catch (Exception e) {
      log.error(
          "[COS] doInitiateMultipartUpload failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  @Override
  protected void doUploadPart(
      String bucketName,
      String chunkObjectName,
      String uploadId,
      int partNumber,
      InputStream inputStream,
      long size) {
    try {
      com.qcloud.cos.model.ObjectMetadata metadata =
          new com.qcloud.cos.model.ObjectMetadata(); // FQN-OK: name conflict with ObjectMetadata
      metadata.setContentLength(size);
      UploadPartRequest uploadPartRequest = new UploadPartRequest();
      uploadPartRequest.setBucketName(bucketName);
      uploadPartRequest.setKey(chunkObjectName);
      uploadPartRequest.setUploadId(uploadId);
      uploadPartRequest.setPartNumber(partNumber);
      uploadPartRequest.setInputStream(inputStream);
      uploadPartRequest.setPartSize(size);
      uploadPartRequest.setObjectMetadata(metadata);
      cosClient.uploadPart(uploadPartRequest);
      log.info(
          "[COS] chunk uploaded, bucket={}, chunk={}, part={}",
          bucketName,
          chunkObjectName,
          partNumber);
    } catch (Exception e) {
      log.error(
          "[COS] doUploadPart failed, bucket={}, chunk={}, part={}, message={}",
          bucketName,
          chunkObjectName,
          partNumber,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  @Override
  protected void doCompleteMultipartUpload(
      String bucketName, String objectName, String uploadId, List<Integer> partNumbers) {
    try {
      ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
      PartListing partListing = cosClient.listParts(listPartsRequest);
      Map<Integer, PartSummary> uploadedPartMap = new HashMap<>();
      for (PartSummary uploadedPart : partListing.getParts()) {
        uploadedPartMap.put(uploadedPart.getPartNumber(), uploadedPart);
      }
      List<PartETag> partETags = new ArrayList<>();
      for (Integer partNumber : partNumbers) {
        PartSummary uploadedPart = uploadedPartMap.get(partNumber);
        if (uploadedPart == null || StringUtils.isBlank(uploadedPart.getETag())) {
          throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
        }
        partETags.add(new PartETag(partNumber, uploadedPart.getETag()));
      }
      partETags.sort(Comparator.comparingInt(PartETag::getPartNumber));
      CompleteMultipartUploadRequest request =
          new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);
      cosClient.completeMultipartUpload(request);
      log.info(
          "[COS] chunked upload completed, bucket={}, object={}, parts={}",
          bucketName,
          objectName,
          partETags.size());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[COS] doCompleteMultipartUpload failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  @Override
  protected void doAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
    try {
      AbortMultipartUploadRequest request =
          new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
      cosClient.abortMultipartUpload(request);
    } catch (Exception e) {
      log.warn(
          "[COS] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
          bucketName,
          objectName,
          uploadId,
          e.getMessage());
    }
  }

  @Override
  protected List<PartInfo> listParts(String bucketName, String objectName, String uploadId) {
    List<PartInfo> parts = new ArrayList<>();
    try {
      ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
      PartListing partListing = cosClient.listParts(listPartsRequest);
      for (PartSummary part : partListing.getParts()) {
        parts.add(new PartInfo(part.getPartNumber(), part.getETag(), part.getSize()));
      }
    } catch (Exception e) {
      log.warn("[COS] listParts failed, object={}, message={}", objectName, e.getMessage());
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
      com.qcloud.cos.model.ObjectMetadata cosMetadata =
          cosClient.getObjectMetadata(
              bucketName, objectName); // FQN-OK: name conflict with ObjectMetadata
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setObjectName(objectName);
      metadata.setBucketName(bucketName);
      metadata.setSize(cosMetadata.getContentLength());
      metadata.setContentType(cosMetadata.getContentType());
      metadata.setETag(cosMetadata.getETag());
      metadata.setLastModified(
          cosMetadata.getLastModified() != null
              ? LocalDateTime.ofInstant(
                  cosMetadata.getLastModified().toInstant(), ZoneId.systemDefault())
              : null);
      metadata.setDirectory(false);
      return metadata;
    } catch (Exception e) {
      log.error(
          "[COS] doGetMetadata failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      return null;
    }
  }

  @Override
  protected ListObjectsResult doListObjects(
      String bucketName, String prefix, String cursor, int maxKeys) {
    List<ObjectMetadata> objects = new ArrayList<>();
    try {
      ListObjectsRequest request = new ListObjectsRequest();
      request.setBucketName(bucketName);
      if (prefix != null && !prefix.isEmpty()) {
        request.setPrefix(prefix);
      }
      if (cursor != null && !cursor.isEmpty()) {
        request.setMarker(cursor);
      }
      request.setMaxKeys(maxKeys + 1);
      ObjectListing listing = cosClient.listObjects(request);
      boolean hasMore = listing.isTruncated();
      String nextCursor = hasMore ? listing.getNextMarker() : null;
      for (COSObjectSummary summary : listing.getObjectSummaries()) {
        if (objects.size() < maxKeys) {
          ObjectMetadata om = new ObjectMetadata();
          om.setObjectName(summary.getKey());
          om.setBucketName(bucketName);
          om.setSize(summary.getSize());
          om.setLastModified(
              summary.getLastModified() != null
                  ? LocalDateTime.ofInstant(
                      summary.getLastModified().toInstant(), ZoneId.systemDefault())
                  : null);
          om.setETag(summary.getETag());
          om.setDirectory(false);
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
      log.error(
          "[COS] doListObjects failed, bucket={}, prefix={}, message={}",
          bucketName,
          prefix,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  public PolicyResult generateUploadPolicy(
      String bucketName, String objectNamePrefix, Integer expires) {
    log.warn("[COS] generateUploadPolicy is not supported, use STS temporary access");
    return null;
  }
}
