package com.njydsz.common.file.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.file.callback.UploadProgressListener;
import com.njydsz.common.file.domain.BatchDeleteResult;
import com.njydsz.common.file.domain.ChunkedUploadResult;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.domain.ListObjectsResult;
import com.njydsz.common.file.domain.ObjectMetadata;
import com.njydsz.common.file.domain.PolicyResult;
import com.njydsz.common.file.domain.UploadCheckpoint;
import com.njydsz.common.file.storage.platform.CosStorage;
import com.njydsz.common.file.storage.platform.LocalStorage;
import com.njydsz.common.file.storage.platform.MinioStorage;
import com.njydsz.common.file.storage.platform.ObsStorage;
import com.njydsz.common.file.storage.platform.OssStorage;
import com.njydsz.common.file.storage.platform.QiniuStorage;
import com.njydsz.common.file.storage.platform.S3Storage;

/**
 * 文件存储统一抽象接口
 *
 * <p>抽象所有存储后端（local / minio / s3 / oss / cos / qiniu / obs）的同一操作语义， 各实现类负责将本接口调用翻译为对应云厂商 SDK 的原生
 * API。业务层只需面向 {@link IFileStorage} 编程，切换存储后端无需修改业务代码。
 *
 * <p><b>核心语义约定：</b>
 *
 * <ul>
 *   <li>{@code bucketName} 传 {@code null} 时使用配置默认值（由 {@code FileProperties.bucket} 决定）
 *   <li>{@code objectName} 即存储键（Key），其格式由业务层自行规划，建议使用 "业务前缀/日期/文件名" 结构（如 {@code
 *       user/202601/upload/abc.jpg}）
 *   <li>分片上传三步曲：{@code initiateChunkedUpload} → {@code uploadChunk}（可并行）→ {@code
 *       completeChunkedUpload}
 *   <li>分片大小：默认 5MB（与 S3 协议对齐），可在 {@code FileProperties.chunkSize} 调整
 * </ul>
 *
 * <p><b>异常体系：</b>所有实现层异常均封装为 {@code BusinessException}，错误码参见 {@link FileExceptionCode}。
 * 上传/下载失败时不应直接抛出 SDK 异常，需转换为稳定的业务错误码。
 *
 * <p><b>并发一致性：</b>分片上传需保证 partNumber 唯一性；complete 时需校验所有分片 ETag 与云端一致，避免出现 "孤儿分片"。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see IFileStorageProvider
 * @see LocalStorage
 * @see MinioStorage
 * @see S3Storage
 * @see OssStorage
 * @see CosStorage
 * @see QiniuStorage
 * @see ObsStorage
 */
public interface IFileStorage {

  /**
   * 分片信息记录（Java 17+ record）
   *
   * <p>用于分片上传时携带分片标识信息。云存储通过 ETag 验证分片完整性。
   *
   * @param partNumber 分片编号（从 1 开始）
   * @param eTag 分片 ETag（云存储用于标识分片的唯一值）
   * @param size 分片大小（字节）
   */
  record PartInfo(int partNumber, String eTag, long size) {}

  // ==================== 上传相关方法 ====================

  /**
   * 普通文件上传
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径，即文件在存储层中的键
   * @param file 待上传的 Spring MultipartFile
   * @return 包含文件元信息的 FileStorage 对象
   */
  FileStorage upload(String bucketName, String objectName, MultipartFile file);

  /**
   * 带上传进度回调的文件上传
   *
   * <p>适用于大文件或弱网络环境，业务层可借此实现上传进度条等功能。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @param file 待上传的 MultipartFile
   * @param listener 上传进度回调，可传入 null 表示不需要回调
   * @return 包含文件元信息的 FileStorage 对象
   */
  FileStorage upload(
      String bucketName, String objectName, MultipartFile file, UploadProgressListener listener);

  /**
   * 异步文件上传（非阻塞）。
   *
   * <p>内部使用 ydsz-common-thread 管理的线程池执行上传操作，不阻塞调用者线程， 适用于批量上传或需要并发处理多个上传请求的场景。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @param file 待上传的 MultipartFile
   * @return 异步上传结果，包含文件元信息的 CompletableFuture
   */
  CompletableFuture<FileStorage> uploadAsync(
      String bucketName, String objectName, MultipartFile file);

  /**
   * 分片上传第一步：初始化分片任务
   *
   * <p>调用成功后返回 uploadId，业务层需保存此 uploadId 并在后续 uploadChunk / completeChunkedUpload 时传入。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 分片合并后的目标对象路径
   * @return 包含 uploadId 等分片任务信息的结果对象
   */
  ChunkedUploadResult initiateChunkedUpload(String bucketName, String objectName);

  /**
   * 分片上传第二步：上传单个分片
   *
   * <p>分片编号 partNumber 必须为正整数且在同一次分片任务内保持唯一。 不同分片可以并行上传，各实现类内部自行处理并发写入。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 分片合并后的目标对象路径（必须与 initiateChunkedUpload 时一致）
   * @param uploadId 初始化分片任务时返回的 uploadId
   * @param partNumber 分片编号，必须大于 0
   * @param file 当前分片的文件内容
   */
  void uploadChunk(
      String bucketName, String objectName, String uploadId, int partNumber, MultipartFile file);

  /**
   * 分片上传第三步：完成分片合并
   *
   * <p>将已上传的所有分片合并为最终对象。 传入的 partNumbers 必须与实际上传的分片编号一致，缺片将导致合并失败。 合并成功后云端会自动清理分片中间数据。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 分片合并后的目标对象路径（必须与 initiateChunkedUpload 时一致）
   * @param uploadId 初始化分片任务时返回的 uploadId
   * @param partNumbers 已上传的分片编号列表，编号必须为正整数且连续（允许跳号但不建议）
   */
  void completeChunkedUpload(
      String bucketName, String objectName, String uploadId, List<Integer> partNumbers);

  /**
   * 生成前端直传 Policy 签名
   *
   * <p>服务端生成上传凭证，前端可凭此凭证直接上传文件到云存储（无需经过服务端中转）。 适用于大文件上传场景，可减轻服务端压力。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectNamePrefix 对象路径前缀（如 "uploads/"），实际路径由前端生成
   * @param expires 签名过期时间（秒），默认使用配置中的 temporarySignatureExpiry
   * @return 前端直传签名结果，包含 Policy、Signature 等凭证
   */
  PolicyResult generateUploadPolicy(String bucketName, String objectNamePrefix, Integer expires);

  /**
   * 初始化带断点续传的分片上传
   *
   * <p>与 initiateChunkedUpload 类似，但额外记录检查点信息到 Redis。 上传过程中断后，可通过 resumeChunkedUpload 恢复进度。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 分片合并后的目标对象路径
   * @param file 待上传的文件（用于计算分片和记录元信息）
   * @return 分片上传检查点信息
   */
  UploadCheckpoint initChunkedUploadWithCheckpoint(
      String bucketName, String objectName, MultipartFile file);

  /**
   * 断点续传：从检查点恢复分片上传
   *
   * <p>读取检查点信息，自动跳过已上传的分片，只上传剩余分片。
   *
   * @param checkpoint 检查点信息（通常从 Redis 读取）
   * @param listener 上传进度回调
   * @return 包含文件元信息的 FileStorage 对象
   */
  FileStorage resumeChunkedUpload(UploadCheckpoint checkpoint, UploadProgressListener listener);

  /**
   * 查询分片上传进度
   *
   * <p>从检查点读取上传进度，返回已上传的分片信息。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @return 分片上传检查点信息，若不存在则返回 null
   */
  UploadCheckpoint getCheckpoint(String bucketName, String objectName);

  /**
   * 删除分片上传检查点
   *
   * <p>上传成功后调用，清理检查点数据。
   *
   * @param checkpoint 检查点信息
   */
  void deleteCheckpoint(UploadCheckpoint checkpoint);

  /**
   * 复制对象
   *
   * <p>在存储桶内复制对象，常用于备份、版本管理、归档等场景。
   *
   * @param srcBucketName 源存储桶名称，传 null 时使用配置默认值
   * @param srcObjectName 源对象路径
   * @param destBucketName 目标存储桶名称，传 null 时使用配置默认值
   * @param destObjectName 目标对象路径
   */
  void copyObject(
      String srcBucketName, String srcObjectName, String destBucketName, String destObjectName);

  /**
   * 移动/重命名对象
   *
   * <p>本质是复制后删除源对象，不保证原子性。
   *
   * @param srcBucketName 源存储桶名称，传 null 时使用配置默认值
   * @param srcObjectName 源对象路径
   * @param destBucketName 目标存储桶名称，传 null 时使用配置默认值
   * @param destObjectName 目标对象路径
   */
  void moveObject(
      String srcBucketName, String srcObjectName, String destBucketName, String destObjectName);

  /**
   * 生成上传预签名 URL（用于临时授权上传）
   *
   * <p>默认实现抛出 UnsupportedOperationException，各云存储实现类按需覆盖。
   *
   * @param bucketName 存储桶名称（为 null 时使用默认配置）
   * @param objectName 对象存储键
   * @param expiryDuration 过期时长
   * @return 预签名上传 URL
   * @throws UnsupportedOperationException 如果当前存储后端不支持
   */
  default String generatePresignedUploadUrl(
      String bucketName, String objectName, Duration expiryDuration) {
    throw new UnsupportedOperationException(
        "Presigned upload URL not supported by this storage backend");
  }

  // ==================== 下载相关方法 ====================

  /**
   * 下载文件（完整内容）
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 待下载的对象路径
   * @param response HttpServletResponse，模块内部直接向输出流写入字节
   */
  void download(String bucketName, String objectName, HttpServletResponse response);

  /**
   * 范围下载（支持断点续传、视频点播等场景）
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 待下载的对象路径
   * @param response HttpServletResponse
   * @param offset 起始字节偏移量（从 0 开始），传 null 表示从 0 开始
   * @param length 请求的字节长度，传 null 表示读取到文件末尾
   */
  void download(
      String bucketName, String objectName, HttpServletResponse response, Long offset, Long length);

  /**
   * 获取文件公开访问地址
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @return 公开访问 URL（若未配置 domain 则返回云厂商默认地址）
   */
  String getPublicUrl(String bucketName, String objectName);

  /**
   * 获取文件私有签名地址（临时访问令牌）
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @return 私有签名 URL，仅在有效期内可访问；若存储类型不支持私有签名则抛出异常
   */
  String getPrivateUrl(String bucketName, String objectName);

  /**
   * 流式下载（返回 InputStream）
   *
   * <p>适合作为图片代理、文件预览等业务内嵌场景。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @return 文件输入流
   */
  InputStream downloadAsStream(String bucketName, String objectName);

  /**
   * 生成文件预签名 URL（临时访问令牌）
   *
   * <p>与 {@link #getPrivateUrl} 的区别在于此方法允许自定义过期时间， 适用于需要灵活控制临时访问有效期的场景。
   *
   * @param objectKey 对象存储键（objectKey）
   * @param expireSeconds 过期时间（秒），最小 1 秒
   * @return 预签名 URL，在有效期内可直接用于访问文件
   */
  String generatePresignedUrl(String objectKey, int expireSeconds);

  /**
   * 生成文件预签名 URL（临时访问令牌，指定存储桶）
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectKey 对象存储键（objectKey）
   * @param expireSeconds 过期时间（秒），最小 1 秒
   * @return 预签名 URL，在有效期内可直接用于访问文件
   */
  String generatePresignedUrl(String bucketName, String objectKey, int expireSeconds);

  // ==================== 管理相关方法 ====================

  /**
   * 判断指定存储桶是否存在
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @return true 表示桶已存在
   */
  boolean bucketExists(String bucketName);

  /**
   * 创建指定存储桶
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   */
  void makeBucket(String bucketName);

  /**
   * 判断指定路径是否已存在（通常用于判断"目录"是否已存在）
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径（末尾带 / 表示目录）
   * @return true 表示已存在
   */
  boolean folderExists(String bucketName, String objectName);

  /**
   * 创建目录（本质是在存储层写入一个以 / 结尾的 0 字节对象）
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 目录路径，必须以 / 结尾
   */
  void makeFolder(String bucketName, String objectName);

  /**
   * 判断对象是否存在
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @return true 表示对象存在
   */
  boolean objectExists(String bucketName, String objectName);

  /**
   * 获取对象元信息
   *
   * <p>返回对象的大小、类型、ETag、最后修改时间等元数据。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @return 对象元信息，若不存在返回 null
   */
  ObjectMetadata getMetadata(String bucketName, String objectName);

  /**
   * 变更对象的存储类型（用于冷数据归档 / 热数据解冻）。
   *
   * <p>将对象从标准存储迁移至归档存储（如 GLACIER / DEEP_ARCHIVE），或从归档存储解冻回标准存储。
   * 不同云厂商的存储类型和 API 不同，由各自实现类负责翻译。
   *
   * <p>默认实现抛出 UnsupportedOperationException，各云存储实现类按需覆盖。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 对象路径
   * @param storageClass 目标存储类型（如 "GLACIER"、"DEEP_ARCHIVE"、"STANDARD"、"STANDARD_IA"）
   * @throws UnsupportedOperationException 如果当前存储后端不支持存储类型变更
   */
  default void changeStorageClass(String bucketName, String objectName, String storageClass) {
    throw new UnsupportedOperationException(
        "Storage class change not supported by this storage backend");
  }

  /**
   * 删除单个文件对象
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectName 待删除的对象路径
   */
  void delete(String bucketName, String objectName);

  /**
   * 批量删除多个文件对象
   *
   * <p>返回 BatchDeleteResult，包含成功列表和失败列表（含失败原因），不静默吞异常。
   *
   * <p>删除操作不保证原子性，部分失败时已成功删除的文件不可恢复。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param objectNames 待删除的对象路径列表
   * @return 批量删除结果，包含成功删除的路径列表和失败路径及原因映射
   */
  BatchDeleteResult batchDelete(String bucketName, List<String> objectNames);

  /**
   * 分页列举对象
   *
   * <p>用于目录遍历、文件列表展示等场景。
   *
   * @param bucketName 存储桶名称，传 null 时使用配置默认值
   * @param prefix 对象前缀过滤（常用于模拟目录，如 "images/"）
   * @param cursor 分页游标（首次调用传 null，后续调用传上次返回的 nextCursor）
   * @param maxKeys 每页最大返回数量（建议 100-1000）
   * @return 分页结果，包含对象列表和下次继续列举的游标
   */
  ListObjectsResult listObjects(String bucketName, String prefix, String cursor, int maxKeys);

  /**
   * 生成文件预签名 URL（用于临时授权访问）
   *
   * <p>默认实现抛出 UnsupportedOperationException，各云存储实现类按需覆盖。
   *
   * @param bucketName 存储桶名称（为 null 时使用默认配置）
   * @param objectName 对象存储键
   * @param expiryDuration 过期时长
   * @return 预签名 URL
   * @throws UnsupportedOperationException 如果当前存储后端不支持
   */
  default String generatePresignedUrl(
      String bucketName, String objectName, Duration expiryDuration) {
    throw new UnsupportedOperationException("Presigned URL not supported by this storage backend");
  }
}
