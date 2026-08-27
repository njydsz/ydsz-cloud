package com.njydsz.common.file.storage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.callback.UploadProgressListener;
import com.njydsz.common.file.config.FileProperties;
import com.njydsz.common.file.config.FileUploadProperties;
import com.njydsz.common.file.constant.FileConstant;
import com.njydsz.common.file.domain.BatchDeleteResult;
import com.njydsz.common.file.domain.ChunkedUploadResult;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.domain.ListObjectsResult;
import com.njydsz.common.file.domain.ObjectMetadata;
import com.njydsz.common.file.domain.PolicyResult;
import com.njydsz.common.file.domain.UploadCheckpoint;
import com.njydsz.common.file.exception.FileExceptionCode;
import com.njydsz.common.file.metrics.FileMetrics;
import com.njydsz.common.file.service.FileDedupService;
import com.njydsz.common.file.util.FileTypeValidator;
import com.njydsz.common.file.virus.VirusScanner;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.util.security.HexUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * 文件存储抽象基类
 *
 * <p>封装所有存储实现公共逻辑，减少子类重复代码。
 *
 * <p>子类只需实现以下核心抽象方法即可：
 *
 * <ul>
 *   <li>{@link #doBucketExists(String)} - 判断桶是否存在
 *   <li>{@link #doMakeBucket(String)} - 创建桶
 *   <li>{@link #doFolderExists(String, String)} - 判断目录是否存在
 *   <li>{@link #doMakeFolder(String, String)} - 创建目录
 *   <li>{@link #doPutObject(String, String, InputStream, long, String)} - 写入对象
 *   <li>{@link #doGetObject(String, String, Long, Long)} - 读取对象
 *   <li>{@link #doRemoveObject(String, String)} - 删除对象
 *   <li>{@link #buildObjectUrl(String, String)} - 构建对象访问地址
 *   <li>{@link #doInitiateMultipartUpload(String, String)} - 初始化分片上传
 *   <li>{@link #doUploadPart(String, String, String, int, InputStream, long)} - 上传分片
 *   <li>{@link #doCompleteMultipartUpload(String, String, String, List)} - 完成分片上传
 *   <li>{@link #doAbortMultipartUpload(String, String, String)} - 中止分片上传
 *   <li>{@link #listParts(String, String, String)} - 列举已上传分片
 * </ul>
 *
 * <p>公共能力：
 *
 * <ul>
 *   <li>bucketName 默认值解析（子类无需重复实现 formatBucketName）
 *   <li>分片上传参数校验（子类无需重复实现 validateMultipartArgs / validateCompleteParts）
 *   <li>分片合并前服务端校验（确保分片完整性）
 *   <li>失败时自动 abort 清理
 *   <li>进度回调触发（onStart/onProgress/onSuccess/onFailure）
 *   <li>路径穿越防护（resolveObjectKey）
 *   <li>分片上传上下文和检查点使用分布式存储（Redis），支持多实例共享
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see IFileStorage
 */
@Slf4j
public abstract class AbstractFileStorage implements IFileStorage {

  /** 分片临时对象前缀（用于标识临时分片文件） */
  protected static final String CHUNK_DIR_PREFIX = ".multipart";

  /** 分片文件名格式 */
  protected static final String CHUNK_FILE_NAME_FORMAT = "part-%d";

  /** 分片上下文 TTL（24 小时） */
  private static final long MULTIPART_CONTEXT_TTL_SECONDS = 24 * 3600;

  /** 检查点 TTL（24 小时） */
  private static final long CHECKPOINT_TTL_SECONDS = 24 * 3600;

  /** 存储配置属性 */
  @Getter protected final FileProperties fileProperties;

  /** 默认存储桶名称 */
  @Getter protected final String defaultBucket;

  /** 默认访问域名 */
  @Getter protected final String domain;

  /** 默认端点地址 */
  @Getter protected final String endpoint;

  /** 分片上传上下文存储（底层存储接口） */
  protected volatile MultipartContextStore multipartContextStore;

  /** 检查点服务（高层业务封装） */
  protected volatile CheckpointService checkpointService;

  /** 并发上传保护器（可选） */
  protected UploadConcurrencyGuard concurrencyGuard;

  protected FileDedupService fileDedupService;
  protected VirusScanner virusScanner;
  protected FileMetrics fileMetrics;
  protected StorageRetryHelper retryHelper;
  protected FileTypeValidator fileTypeValidator;

  /** 分片上传配置（可选，为空时不使用 MD5 校验） */
  protected FileUploadProperties fileUploadProperties;

  /**
   * 流式 MD5 摘要器（uploadId → MessageDigest），每上传一片就更新摘要。 仅缓存 MessageDigest 状态（约 128 字节），而非原始分片数据，避免大文件
   * OOM。
   */
  private final ConcurrentHashMap<String, MessageDigest> chunkedMd5DigestMap =
      new ConcurrentHashMap<>();

  /**
   * 批量删除专用线程池（由 ydsz-common-thread 统一管理）。 Bean 名称 {@code fileDeleteExecutor}，通过 setter 注入。 未注入时使用
   * {@link CompletableFuture#runAsync(Runnable)} 作为降级。
   *
   * @see <a href="https://ydsz-cloud.github.io/docs/encoding-spec#15-4">云顶编码规范 15.4 节</a>
   */
  private transient ExecutorService deleteExecutor;

  /**
   * 异步上传专用线程池（由 ydsz-common-thread 统一管理）。 Bean 名称 {@code fileUploadExecutor}，通过 setter 注入。 未注入时使用
   * {@link CompletableFuture#supplyAsync(java.util.function.Supplier)} 作为降级。
   */
  private transient ExecutorService asyncUploadExecutor;

  protected AbstractFileStorage(FileProperties fileProperties) {
    this(fileProperties, null);
  }

  protected AbstractFileStorage(
      FileProperties fileProperties, FileUploadProperties fileUploadProperties) {
    this.fileProperties = fileProperties;
    this.fileUploadProperties = fileUploadProperties;
    this.defaultBucket = fileProperties.getBucket();
    this.domain = fileProperties.getDomain();
    this.endpoint = fileProperties.getEndpoint();
    // 默认使用内存/本地文件实现的服务层
    this.multipartContextStore = new InMemoryMultipartContextStore();
    CheckpointStore defaultCheckpointStore =
        new LocalCheckpointStore(fileProperties.getCheckpointDir());
    // 使用局部数组持有者延迟绑定 this::listParts，避免构造器中 this 逃逸
    final DefaultCheckpointService.MultipartLister[] listerHolder =
        new DefaultCheckpointService.MultipartLister[1];
    this.checkpointService =
        new DefaultCheckpointService(
            defaultCheckpointStore,
            (bucket, object, uploadId) -> {
              DefaultCheckpointService.MultipartLister lister = listerHolder[0];
              return lister != null
                  ? lister.listParts(bucket, object, uploadId)
                  : Collections.emptyList();
            },
            CHECKPOINT_TTL_SECONDS);
    listerHolder[0] = this::listParts;
  }

  /**
   * 设置分片上传配置。
   *
   * @param properties 分片上传配置
   */
  public void setFileUploadProperties(FileUploadProperties properties) {
    this.fileUploadProperties = properties;
  }

  /** 是否启用分片 MD5 校验 */
  protected boolean isChunkMd5CheckEnabled() {
    return fileUploadProperties != null && fileUploadProperties.isChunkMd5Check();
  }

  /**
   * 设置分片上传上下文存储。
   *
   * @param store 分片上传上下文存储
   */
  public void setMultipartContextStore(MultipartContextStore store) {
    if (store != null) {
      this.multipartContextStore = store;
    }
  }

  /**
   * 设置检查点服务。
   *
   * @param service 检查点服务
   */
  public void setCheckpointService(CheckpointService service) {
    if (service != null) {
      this.checkpointService = service;
    }
  }

  /**
   * 设置并发上传保护器。
   *
   * @param guard 并发上传保护器
   */
  public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
    this.concurrencyGuard = guard;
  }

  public void setFileDedupService(FileDedupService service) {
    this.fileDedupService = service;
  }

  public void setVirusScanner(VirusScanner scanner) {
    this.virusScanner = scanner;
  }

  public void setFileMetrics(FileMetrics metrics) {
    this.fileMetrics = metrics;
  }

  public void setRetryHelper(StorageRetryHelper helper) {
    this.retryHelper = helper;
  }

  public void setFileTypeValidator(FileTypeValidator validator) {
    this.fileTypeValidator = validator;
  }

  /**
   * 设置批量删除专用线程池（ydsz-common-thread 管理的 Bean）。
   *
   * @param executor 线程池实例，为 null 时降级为 {@link CompletableFuture#runAsync(Runnable)}
   */
  public void setDeleteExecutor(ExecutorService executor) {
    this.deleteExecutor = executor;
  }

  /**
   * 设置异步上传专用线程池（ydsz-common-thread 管理的 Bean）。
   *
   * @param executor 线程池实例，为 null 时降级为 {@link
   *     CompletableFuture#supplyAsync(java.util.function.Supplier)}
   */
  public void setAsyncUploadExecutor(ExecutorService executor) {
    this.asyncUploadExecutor = executor;
  }

  /**
   * 声明当前存储实现是否支持服务端复制（Server-Side Copy）。
   *
   * <p>返回 {@code true} 时 {@link #copyObject} 会走 {@link #doCopyObject}，
   * 由对象存储服务内部完成数据搬运，无需经过应用进程，省带宽且速度快； 返回 {@code false} 则降级为"下载 + 重新上传"，大文件下会显著占用应用内存与带宽。
   *
   * <p>默认返回 {@code false}，支持该能力的子类（如 MinIO / OSS / COS）应覆盖为 {@code true} 并同时实现 {@link
   * #doCopyObject}。
   *
   * @return 是否支持服务端复制
   */
  protected boolean supportsServerSideCopy() {
    return false;
  }

  /**
   * 执行服务端复制，由具体存储的 SDK 直接完成对象搬运。
   *
   * <p>仅当 {@link #supportsServerSideCopy()} 返回 {@code true} 时才会被调用。
   * 入参均已由上层完成默认桶解析与路径穿越校验，实现方无需重复校验。
   *
   * @param srcBucket 源桶名，已解析
   * @param srcObject 源对象键，已规范化
   * @param destBucket 目标桶名，已解析
   * @param destObject 目标对象键，已规范化
   * @throws UnsupportedOperationException 默认实现直接抛出； 子类若声明支持服务端复制却未覆盖本方法即属实现缺陷
   */
  protected void doCopyObject(
      String srcBucket, String srcObject, String destBucket, String destObject) {
    throw new UnsupportedOperationException("Server-side copy not supported");
  }

  /**
   * 清理过期的分片上传上下文
   *
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
      throw new BusinessException(FileExceptionCode.BUCKET_ERROR);
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
  public void copyObject(
      String srcBucketName, String srcObjectName, String destBucketName, String destObjectName) {
    String resolvedSrcBucket = resolveBucketName(srcBucketName);
    String resolvedSrcObject = resolveObjectKey(resolvedSrcBucket, srcObjectName);
    String resolvedDestBucket = resolveBucketName(destBucketName);
    String resolvedDestObject = resolveObjectKey(resolvedDestBucket, destObjectName);

    try {
      // P0-7: Try server-side copy first
      if (supportsServerSideCopy()) {
        doCopyObject(resolvedSrcBucket, resolvedSrcObject, resolvedDestBucket, resolvedDestObject);
        log.info("copyObject via server-side copy success");
        return;
      }
      // Fallback: download + upload
      ObjectMetadata metadata = doGetMetadata(resolvedSrcBucket, resolvedSrcObject);
      if (metadata == null) {
        throw new BusinessException(FileExceptionCode.FILE_NOT_FOUND);
      }
      String contentType =
          metadata.getContentType() != null
              ? metadata.getContentType()
              : "application/octet-stream";
      long size = metadata.getSize();
      try (InputStream is = doGetObject(resolvedSrcBucket, resolvedSrcObject, null, null)) {
        doPutObject(resolvedDestBucket, resolvedDestObject, is, size, contentType);
      }
      log.info(
          "[Storage] copyObject success, src={}/{}, dest={}/{}",
          resolvedSrcBucket,
          resolvedSrcObject,
          resolvedDestBucket,
          resolvedDestObject);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[Storage] copyObject failed, src={}/{}, dest={}/{}, message={}",
          resolvedSrcBucket,
          resolvedSrcObject,
          resolvedDestBucket,
          resolvedDestObject,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  public void moveObject(
      String srcBucketName, String srcObjectName, String destBucketName, String destObjectName) {
    copyObject(srcBucketName, srcObjectName, destBucketName, destObjectName);
    delete(srcBucketName, srcObjectName);
    log.info(
        "[Storage] moveObject success, src={}/{}, dest={}/{}",
        resolveBucketName(srcBucketName),
        resolveObjectKey(resolveBucketName(srcBucketName), srcObjectName),
        resolveBucketName(destBucketName),
        resolveObjectKey(resolveBucketName(destBucketName), destObjectName));
  }

  @Override
  public ListObjectsResult listObjects(
      String bucketName, String prefix, String cursor, int maxKeys) {
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
  public FileStorage upload(
      String bucketName, String objectName, MultipartFile file, UploadProgressListener listener) {
    String resolvedBucket = resolveBucketName(bucketName);
    String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

    if (file.isEmpty()) {
      throw new BusinessException(FileExceptionCode.FILE_EMPTY);
    }

    if (fileTypeValidator != null) {
      fileTypeValidator.validate(file);
    }

    Long maxFileSize = fileProperties.getMaxFileSize();
    if (maxFileSize != null && maxFileSize > 0 && file.getSize() > maxFileSize) {
      throw new BusinessException(FileExceptionCode.FILE_SIZE_EXCEEDED);
    }

    // 内容源抽象：小文件走内存缓冲（多次复用高效），大文件落盘临时文件（避免 OOM）。
    // 后续秒传校验、病毒扫描、对象存储上传复用同一内容源，避免重复读取 IO。
    FileContentSource contentSource = null;
    try {
      try {
        contentSource = bufferFileContent(file);
      } catch (IOException e) {
        log.error("[Storage] failed to buffer file content, object={}", resolvedObjectName, e);
        throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
      }

      // P0-1: File dedup check — 基于已缓冲的内容计算秒传 hash，不重新读取上传流
      String dedupHash = null;
      if (fileDedupService != null) {
        try (InputStream dedupStream = contentSource.openStream()) {
          dedupHash = fileDedupService.calculateHash(dedupStream);
          String existingUrl = fileDedupService.checkExisting(file.getSize(), dedupHash);
          if (existingUrl != null) {
            if (fileMetrics != null) {
              fileMetrics.recordDedupHit();
            }
            FileStorage dedupResult = buildFileStorage(file);
            dedupResult.setUuidName(resolvedObjectName);
            dedupResult.setUrl(existingUrl);
            return dedupResult;
          }
          if (fileMetrics != null) {
            fileMetrics.recordDedupMiss();
          }
        } catch (BusinessException e) {
          throw e;
        } catch (Exception e) {
          log.warn(
              "[Storage] dedup check failed, object={}, message={}",
              resolvedObjectName,
              e.getMessage());
        }
      }

      // P0-2: Virus scan — 基于已缓冲的内容进行扫描，不重新读取上传流
      if (virusScanner != null) {
        try (InputStream virusStream = contentSource.openStream()) {
          VirusScanner.ScanResult scanResult =
              virusScanner.scan(virusStream, file.getOriginalFilename());
          if (scanResult == VirusScanner.ScanResult.INFECTED) {
            if (fileMetrics != null) {
              fileMetrics.recordVirusDetected();
            }
            throw new BusinessException(FileExceptionCode.FILE_VIRUS_DETECTED);
          }
        } catch (BusinessException e) {
          throw e;
        } catch (Exception e) {
          log.warn(
              "[Storage] virus scan failed, object={}, message={}",
              resolvedObjectName,
              e.getMessage());
        }
      }

      // 获取并发上传锁
      String lockToken = acquireConcurrencyLock(resolvedObjectName);

      makeBucket(resolvedBucket);
      FileStorage fileStorage = buildFileStorage(file);
      long totalBytes = file.getSize();

      if (listener != null) {
        listener.onStart(totalBytes);
      }

      long startTime = System.nanoTime();
      try (InputStream uploadStream = contentSource.openStream()) {
        if (retryHelper != null) {
          retryHelper.executeRunnableWithRetry(
              () ->
                  doPutObject(
                      resolvedBucket,
                      resolvedObjectName,
                      uploadStream,
                      file.getSize(),
                      file.getContentType()),
              "upload");
        } else {
          doPutObject(
              resolvedBucket,
              resolvedObjectName,
              uploadStream,
              file.getSize(),
              file.getContentType());
        }

        fileStorage.setUuidName(resolvedObjectName);
        fileStorage.setUrl(buildObjectUrl(resolvedBucket, resolvedObjectName));

        if (listener != null) {
          listener.onSuccess(resolvedObjectName);
        }
        if (fileDedupService != null && dedupHash != null) {
          try {
            fileDedupService.registerHash(
                file.getSize(), dedupHash, fileStorage.getUrl(), resolvedObjectName);
          } catch (Exception e) {
            log.warn(
                "[Storage] dedup register failed, object={}, message={}",
                resolvedObjectName,
                e.getMessage());
          }
        }
        if (fileMetrics != null) {
          fileMetrics.recordUpload(System.nanoTime() - startTime);
        }
        return fileStorage;
      } catch (BusinessException e) {
        if (listener != null) {
          listener.onFailure(resolvedObjectName, e);
        }
        throw e;
      } catch (Exception e) {
        log.error(
            "[Storage] file upload failed, bucket={}, object={}, message={}",
            resolvedBucket,
            resolvedObjectName,
            e.getMessage(),
            e);
        if (listener != null) {
          listener.onFailure(resolvedObjectName, e);
        }
        throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
      } finally {
        releaseConcurrencyLock(resolvedObjectName, lockToken);
      }
    } finally {
      if (contentSource != null) {
        contentSource.close();
      }
    }
  }

  /**
   * 将上传文件内容缓冲为可多次复用的内容源。
   *
   * <p>文件大小不超过 {@link FileProperties#getMemoryBufferThreshold()} 时在内存中缓冲（字节数组）； 超过阈值时落盘到临时文件，
   * 避免大文件全量读入内存导致 OOM。临时文件在调用方 {@code finally} 中清理。
   *
   * @param file 上传文件
   * @return 可多次打开 InputStream 的内容源
   * @throws IOException 读取文件内容失败时抛出
   */
  private FileContentSource bufferFileContent(MultipartFile file) throws IOException {
    long threshold = fileProperties.getMemoryBufferThreshold();
    if (file.getSize() <= threshold) {
      return new InMemoryFileContentSource(file.getBytes());
    }
    return new TempFileContentSource(file);
  }

  /** 文件内容源抽象：支持多次打开 InputStream 读取同一内容，并在使用结束后释放资源 */
  private interface FileContentSource {

    /**
     * 打开内容输入流
     *
     * @return 内容输入流（调用方负责关闭）
     * @throws IOException IO 异常
     */
    InputStream openStream() throws IOException;

    /** 释放底层资源（内存引用或临时文件） */
    void close();
  }

  /** 内存缓冲内容源（小文件） */
  private static final class InMemoryFileContentSource implements FileContentSource {

    private final byte[] bytes;

    InMemoryFileContentSource(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public InputStream openStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void close() {
      // 无底层资源需要释放
    }
  }

  /** 临时文件内容源（大文件，避免 OOM） */
  private static final class TempFileContentSource implements FileContentSource {

    private final Path tempFile;

    TempFileContentSource(MultipartFile file) throws IOException {
      this.tempFile = Files.createTempFile("ydsz-upload-", ".tmp");
      file.transferTo(this.tempFile);
    }

    @Override
    public InputStream openStream() throws IOException {
      return new BufferedInputStream(
          Files.newInputStream(tempFile, StandardOpenOption.READ));
    }

    @Override
    public void close() {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        // 临时文件删除失败不影响业务，交由系统临时目录回收
        log.warn("[Storage] failed to delete temp file: {}", tempFile, e);
      }
    }
  }

  @Override
  public CompletableFuture<FileStorage> uploadAsync(
      String bucketName, String objectName, MultipartFile file) {
    ExecutorService executor = asyncUploadExecutor;
    if (executor != null) {
      return CompletableFuture.supplyAsync(
          () -> upload(bucketName, objectName, file, null), executor);
    }
    // 降级：ydsz-common-thread 不可用时使用默认 ForkJoinPool
    return CompletableFuture.supplyAsync(() -> upload(bucketName, objectName, file, null));
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
      if (fileMetrics != null) {
        fileMetrics.recordDelete();
      }
    } catch (Exception e) {
      log.error(
          "file delete failed, bucket={}, object={}, message={}",
          resolvedBucket,
          resolvedObjectName,
          e.getMessage(),
          e);
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  public BatchDeleteResult batchDelete(String bucketName, List<String> objectNames) {
    if (objectNames == null || objectNames.isEmpty()) {
      return BatchDeleteResult.allSuccess(Collections.emptyList());
    }
    ConcurrentLinkedQueue<String> successList = new ConcurrentLinkedQueue<>();
    Map<String, String> failedMap = new ConcurrentHashMap<>();
    // 使用 ydsz-common-thread 管理的线程池执行并行批量删除，未注入时降级为默认 ForkJoinPool
    ExecutorService executor = deleteExecutor;
    List<CompletableFuture<Void>> futures =
        objectNames.stream()
            .map(
                objectName -> {
                  Runnable task =
                      () -> {
                        try {
                          delete(bucketName, objectName);
                          successList.add(objectName);
                        } catch (Exception e) {
                          String errorMsg =
                              e.getMessage() != null
                                  ? e.getMessage()
                                  : e.getClass().getSimpleName();
                          failedMap.put(objectName, errorMsg);
                          log.error("batch delete failed, object={}", objectName, e);
                        }
                      };
                  return executor != null
                      ? CompletableFuture.runAsync(task, executor)
                      : CompletableFuture.runAsync(task);
                })
            .toList();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return new BatchDeleteResult(List.copyOf(successList), Map.copyOf(failedMap));
  }

  @Override
  public void download(String bucketName, String objectName, HttpServletResponse response) {
    download(bucketName, objectName, response, null, null);
  }

  @Override
  public void download(
      String bucketName,
      String objectName,
      HttpServletResponse response,
      Long offset,
      Long length) {
    String resolvedBucket = resolveBucketName(bucketName);
    String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

    // P1-7: Set response headers
    ObjectMetadata metadata = doGetMetadata(resolvedBucket, resolvedObjectName);
    if (metadata != null) {
      String contentType =
          metadata.getContentType() != null
              ? metadata.getContentType()
              : "application/octet-stream";
      response.setContentType(contentType);
      if (metadata.getSize() > 0) {
        response.setContentLengthLong(metadata.getSize());
      }
    }
    String fileName = resolvedObjectName;
    int lastSlash = fileName.lastIndexOf('/');
    if (lastSlash >= 0) {
      fileName = fileName.substring(lastSlash + 1);
    }
    String encodedFileName =
        URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    response.setHeader(
        "Content-Disposition",
        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
    response.setHeader("Accept-Ranges", "bytes");
    long dlStart = System.nanoTime();
    try (InputStream is =
            (retryHelper != null
                ? retryHelper.executeWithRetry(
                    () -> doGetObject(resolvedBucket, resolvedObjectName, offset, length),
                    "download")
                : doGetObject(resolvedBucket, resolvedObjectName, offset, length));
        OutputStream os = response.getOutputStream()) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = is.read(buffer)) != -1) {
        os.write(buffer, 0, read);
      }
      os.flush();
      if (fileMetrics != null) {
        fileMetrics.recordDownload(System.nanoTime() - dlStart);
      }
    } catch (BusinessException e) {
      if (response.isCommitted()) {
        log.error(
            "[Storage] file download failed after response committed, bucket={}, object={}, message={}",
            resolvedBucket,
            resolvedObjectName,
            e.getMessage(),
            e);
      } else {
        throw e;
      }
    } catch (Exception e) {
      if (response.isCommitted()) {
        log.error(
            "[Storage] file download failed after response committed, bucket={}, object={}, message={}",
            resolvedBucket,
            resolvedObjectName,
            e.getMessage(),
            e);
      } else {
        log.error(
            "[Storage] file download failed, bucket={}, object={}, message={}",
            resolvedBucket,
            resolvedObjectName,
            e.getMessage(),
            e);
        throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
      }
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
      log.error(
          "[Storage] downloadAsStream failed, bucket={}, object={}, message={}",
          resolvedBucket,
          resolvedObjectName,
          e.getMessage(),
          e);
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  public ChunkedUploadResult initiateChunkedUpload(String bucketName, String objectName) {
    String resolvedBucket = resolveBucketName(bucketName);
    String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

    makeBucket(resolvedBucket);
    ChunkedUploadResult result = doInitiateMultipartUpload(resolvedBucket, resolvedObjectName);

    multipartContextStore.save(
        result.getUploadId(),
        new MultipartContextStore.MultipartContextData(
            result.getUploadId(), resolvedBucket, resolvedObjectName),
        MULTIPART_CONTEXT_TTL_SECONDS);

    log.info(
        "[Storage] chunked upload initiated, bucket={}, object={}, uploadId={}",
        resolvedBucket,
        resolvedObjectName,
        result.getUploadId());
    return result;
  }

  @Override
  public void uploadChunk(
      String bucketName, String objectName, String uploadId, int partNumber, MultipartFile file) {
    String resolvedBucket = resolveBucketName(bucketName);
    String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

    validateUploadId(uploadId);
    validatePartNumber(partNumber);

    try {
      String chunkObjectName = buildChunkObjectName(resolvedObjectName, uploadId, partNumber);
      byte[] chunkData = null;
      String chunkMd5 = null;
      // P2-2: Stream when MD5 check is disabled
      if (!isChunkMd5CheckEnabled()) {
        try (InputStream uploadStream = file.getInputStream()) {
          doUploadPart(
              resolvedBucket, chunkObjectName, uploadId, partNumber, uploadStream, file.getSize());
        }
      } else {
        chunkData = file.getBytes();

        // 流式更新 MD5 摘要，仅缓存 MessageDigest 状态而非原始数据，避免 OOM
        if (isChunkMd5CheckEnabled()) {
          // 计算分片 MD5（用于校验）
          chunkMd5 = UploadCheckpoint.calculateMd5(chunkData);

          // 流式更新整体文件 MD5 摘要
          MessageDigest digest =
              chunkedMd5DigestMap.computeIfAbsent(uploadId, k -> createMessageDigest());
          digest.update(chunkData);
        }

        doUploadPart(
            resolvedBucket,
            chunkObjectName,
            uploadId,
            partNumber,
            new ByteArrayInputStream(chunkData),
            file.getSize());
      }

      MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
      if (context == null) {
        context =
            new MultipartContextStore.MultipartContextData(
                uploadId, resolvedBucket, resolvedObjectName);
      }
      Map<Integer, String> partChunkNames = new ConcurrentHashMap<>(context.partChunkNames());
      partChunkNames.put(partNumber, chunkObjectName);
      multipartContextStore.save(
          uploadId,
          new MultipartContextStore.MultipartContextData(
              context.uploadId(),
              context.bucketName(),
              context.objectName(),
              partChunkNames,
              context.createTime(),
              System.currentTimeMillis()),
          MULTIPART_CONTEXT_TTL_SECONDS);

      // 更新检查点中的分片 MD5
      if (isChunkMd5CheckEnabled() && chunkMd5 != null) {
        checkpointService.updateChunkMd5InCheckpoint(
            resolvedBucket,
            resolvedObjectName,
            partNumber,
            chunkMd5,
            chunkData != null ? chunkData.length : 0);
      }

      log.info(
          "[Storage] chunk uploaded, bucket={}, object={}, part={}",
          resolvedBucket,
          resolvedObjectName,
          partNumber);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "[Storage] uploadChunk failed, bucket={}, object={}, part={}, message={}",
          resolvedBucket,
          resolvedObjectName,
          partNumber,
          e.getMessage(),
          e);
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  @Override
  public void completeChunkedUpload(
      String bucketName, String objectName, String uploadId, List<Integer> partNumbers) {
    String resolvedBucket = resolveBucketName(bucketName);
    String resolvedObjectName = resolveObjectKey(resolvedBucket, objectName);

    validateUploadId(uploadId);
    validatePartNumbers(partNumbers);

    MultipartContextStore.MultipartContextData context = multipartContextStore.get(uploadId);
    if (context == null || !resolvedObjectName.equals(context.objectName())) {
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }

    Set<Integer> uniqueParts = new HashSet<>(partNumbers);
    List<Integer> sortedParts = new ArrayList<>(uniqueParts);
    Collections.sort(sortedParts);

    List<PartInfo> uploadedParts = listParts(resolvedBucket, resolvedObjectName, uploadId);

    for (Integer partNumber : sortedParts) {
      boolean found = uploadedParts.stream().anyMatch(p -> p.partNumber() == partNumber);
      if (!found) {
        throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
      }
    }

    try {
      doCompleteMultipartUpload(resolvedBucket, resolvedObjectName, uploadId, sortedParts);

      // 基于流式 MessageDigest 计算累积 MD5，避免缓存原始分片数据导致 OOM
      if (isChunkMd5CheckEnabled()) {
        MessageDigest digest = chunkedMd5DigestMap.get(uploadId);
        if (digest != null) {
          String accumulatedMd5 = HexUtils.encode(digest.digest());
          checkpointService.updateAccumulatedMd5(
              resolvedBucket, resolvedObjectName, accumulatedMd5);
          log.debug(
              "[Storage] computed accumulated MD5 via streaming digest, uploadId={}, md5={}",
              uploadId,
              accumulatedMd5);
        }
      }

      safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
      multipartContextStore.remove(uploadId);
      chunkedMd5DigestMap.remove(uploadId);

      // 分片上传完成后，基于 fileMd5 校验文件完整性
      if (isChunkMd5CheckEnabled()) {
        checkpointService.validateFileMd5(
            resolvedBucket,
            resolvedObjectName,
            true,
            this::computeMd5,
            (b, o) -> doGetObject(b, o, null, null));
      }

      log.info(
          "[Storage] chunked upload completed, bucket={}, object={}, parts={}",
          resolvedBucket,
          resolvedObjectName,
          sortedParts.size());
    } catch (BusinessException e) {
      safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
      multipartContextStore.remove(uploadId);
      chunkedMd5DigestMap.remove(uploadId);
      throw e;
    } catch (Exception e) {
      safeAbortMultipartUpload(resolvedBucket, resolvedObjectName, uploadId);
      multipartContextStore.remove(uploadId);
      chunkedMd5DigestMap.remove(uploadId);
      log.error(
          "[Storage] completeChunkedUpload failed, bucket={}, object={}, message={}",
          resolvedBucket,
          resolvedObjectName,
          e.getMessage(),
          e);
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  /**
   * 将存储桶名称解析为实际使用的值
   *
   * <p>当传入值为空时，使用配置文件中的默认桶名称
   *
   * @param bucketName 存储桶名称（可为 null）
   * @return 解析后的存储桶名称
   */
  protected String resolveBucketName(String bucketName) {
    return StringUtils.isNotBlank(bucketName) ? bucketName : defaultBucket;
  }

  private static final Pattern PATH_TRAVERSAL_PATTERN =
      Pattern.compile("(\\.\\.)|(%2e%2e)|(%2E%2E)");

  /**
   * 解析并校验对象路径，防止路径穿越攻击
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>路径不能为空
   *   <li>禁止包含空字节 {@code \0} 及控制字符
   *   <li>禁止包含 {@code ..} 路径穿越符（含 URL 编码形式）
   *   <li>规范化路径后禁止以 {@code ..} 作为路径段
   *   <li>使用 {@code Paths.normalize()} 进行二次校验
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
        log.warn(
            "[Storage] path traversal after canonical normalization, objectName={}, normalized={}, canonical={}",
            objectName,
            normalized,
            canonicalPath);
        throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
      }
    } catch (Exception e) {
      log.warn(
          "[Storage] path canonicalization failed, objectName={}, message={}",
          objectName,
          e.getMessage());
      throw new BusinessException(FileExceptionCode.FILE_PATH_EMPTY);
    }
    return normalizeObjectKey(normalized);
  }

  /**
   * 对已通过安全校验的对象键做存储侧格式适配，是 {@link #resolveObjectKey} 的最后一步。
   *
   * <p>默认原样返回（保留前导 {@code /}，适用于本地文件系统语义）。 S3 协议族的对象键不允许以 {@code /} 开头，此类子类需覆盖本方法去掉前导斜杠。
   *
   * <p><b>约束</b>：实现只允许做格式规范化，不得再引入 {@code ..} 等路径穿越可能， 因为本方法之后不再有任何安全校验。
   *
   * @param objectKey 已完成穿越校验与斜杠归一的对象键，非空
   * @return 适配存储实现后的最终对象键
   */
  protected String normalizeObjectKey(String objectKey) {
    return objectKey;
  }

  /**
   * 检测文件的 MIME Type
   *
   * <p>优先使用 MultipartFile.getContentType()，若为空则通过 URLConnection.guessContentTypeFromStream()
   * 基于文件头魔数检测。
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
      log.debug(
          "[Storage] MIME Type detection failed for file: {}, message={}",
          file.getOriginalFilename(),
          e.getMessage());
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
   *
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
    fileStorage.setType(inferFileType(suffix));
    fileStorage.setMimeType(detectMimeType(file));
    fileStorage.setUploadAt(LocalDateTime.now());
    return fileStorage;
  }

  /**
   * 根据文件后缀推断文件分类类型。
   *
   * <p>用于前端图标渲染和业务分类，返回统一的分类标识字符串。
   *
   * @param suffix 文件后缀（不含点，小写）
   * @return 文件分类（image / video / audio / office / code），未匹配时返回原始后缀
   */
  protected static String inferFileType(String suffix) {
    if (suffix == null) {
      return "unknown";
    }
    if (IMAGE_SUFFIXES.contains(suffix)) {
      return "image";
    }
    if (VIDEO_SUFFIXES.contains(suffix)) {
      return "video";
    }
    if (AUDIO_SUFFIXES.contains(suffix)) {
      return "audio";
    }
    if (OFFICE_SUFFIXES.contains(suffix)) {
      return "office";
    }
    if (CODE_SUFFIXES.contains(suffix)) {
      return "code";
    }
    return suffix;
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

  private static final Set<String> IMAGE_SUFFIXES =
      Set.of("png", "bmp", "jpg", "jpeg", "gif", "svg", "ico", "webp");

  private static final Set<String> VIDEO_SUFFIXES =
      Set.of("mp4", "flv", "avi", "mkv", "mov", "wmv", "3gp");

  private static final Set<String> AUDIO_SUFFIXES =
      Set.of("mp3", "wma", "wav", "flac", "aac", "ogg");

  private static final Set<String> OFFICE_SUFFIXES =
      Set.of("txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "csv");

  private static final Set<String> CODE_SUFFIXES =
      Set.of("java", "sql", "js", "py", "php", "vue", "sh", "css", "html", "htm", "xml", "json");

  /** 检查是否为图片文件后缀 */
  protected boolean isImageSuffix(String suffix) {
    return suffix != null && IMAGE_SUFFIXES.contains(suffix.toLowerCase());
  }

  /** 检查是否为视频文件后缀 */
  protected boolean isVideoSuffix(String suffix) {
    return suffix != null && VIDEO_SUFFIXES.contains(suffix.toLowerCase());
  }

  /** 检查是否为音频文件后缀 */
  protected boolean isAudioSuffix(String suffix) {
    return suffix != null && AUDIO_SUFFIXES.contains(suffix.toLowerCase());
  }

  /** 检查是否为办公文档后缀 */
  protected boolean isOfficeSuffix(String suffix) {
    return suffix != null && OFFICE_SUFFIXES.contains(suffix.toLowerCase());
  }

  /** 检查是否为代码文件后缀 */
  protected boolean isCodeSuffix(String suffix) {
    return suffix != null && CODE_SUFFIXES.contains(suffix.toLowerCase());
  }

  /** 校验上传 ID 格式 */
  protected void validateUploadId(String uploadId) {
    if (StringUtils.isBlank(uploadId) || uploadId.length() > 64) {
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  /** 校验分片编号 */
  protected void validatePartNumber(int partNumber) {
    if (partNumber <= 0) {
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
  }

  /** 校验分片编号列表 */
  protected void validatePartNumbers(List<Integer> partNumbers) {
    if (partNumbers == null || partNumbers.isEmpty()) {
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
    }
    for (Integer partNumber : partNumbers) {
      if (partNumber == null || partNumber <= 0) {
        throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
      }
    }
  }

  /** 构建分片对象名称 */
  protected String buildChunkObjectName(String objectName, String uploadId, int partNumber) {
    return CHUNK_DIR_PREFIX
        + FileConstant.DIR_SPLIT
        + objectName
        + FileConstant.DIR_SPLIT
        + uploadId
        + FileConstant.DIR_SPLIT
        + String.format(CHUNK_FILE_NAME_FORMAT, partNumber);
  }

  /** 安全中止分片上传（失败时清理资源） */
  protected void safeAbortMultipartUpload(String bucketName, String objectName, String uploadId) {
    if (StringUtils.isBlank(uploadId)) {
      return;
    }
    try {
      doAbortMultipartUpload(bucketName, objectName, uploadId);
    } catch (Exception e) {
      log.warn(
          "[Storage] abort multipart upload failed, bucket={}, object={}, uploadId={}, message={}",
          bucketName,
          objectName,
          uploadId,
          e.getMessage());
    }
  }

  /** 创建目录（以 / 结尾的 0 字节对象） */
  protected void createFolderByEmptyObject(String bucketName, String folderName) {
    try (InputStream emptyStream = new ByteArrayInputStream(new byte[] {})) {
      doPutObject(bucketName, folderName, emptyStream, 0L, "application/directory");
    } catch (Exception e) {
      throw new BusinessException(FileExceptionCode.FILE_OPERATE_FAILED);
    }
  }

  @Override
  public PolicyResult generateUploadPolicy(
      String bucketName, String objectNamePrefix, Integer expires) {
    log.warn("[Storage] generateUploadPolicy is not supported for this storage type");
    return null;
  }

  @Override
  public UploadCheckpoint initChunkedUploadWithCheckpoint(
      String bucketName, String objectName, MultipartFile file) {
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
        log.info(
            "[Storage] recovered existing checkpoint, bucket={}, object={}, uploadId={}, uploadedParts={}",
            resolvedBucket,
            resolvedObjectName,
            loadedCheckpoint.getUploadId(),
            loadedCheckpoint.getUploadedPartsCount());
        return loadedCheckpoint;
      }
    }

    ChunkedUploadResult chunkResult = initiateChunkedUpload(resolvedBucket, resolvedObjectName);

    long fileSize = file.getSize();
    long partSize = fileProperties.getPartSize() != null ? fileProperties.getPartSize() : 5242880L;

    UploadCheckpoint checkpoint = new UploadCheckpoint();
    checkpoint.setTaskId(IdGenerator.nextIdStr());
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
        log.warn(
            "[Storage] initChunkedUploadWithCheckpoint fileMd5 compute failed, message={}",
            e.getMessage());
      }
    }

    saveCheckpoint(checkpoint);

    return checkpoint;
  }

  @Override
  public FileStorage resumeChunkedUpload(
      UploadCheckpoint checkpoint, UploadProgressListener listener) {
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
      long uploadedBytes =
          checkpoint.getUploadedBytes() != null ? checkpoint.getUploadedBytes() : 0;
      if (listener != null) {
        listener.onProgress(uploadedBytes, checkpoint.getTotalSize());
      }

      completeChunkedUpload(
          bucketName,
          objectName,
          uploadId,
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
      throw new BusinessException(FileExceptionCode.MULTIPART_UPLOAD_FAILED);
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
  protected UploadCheckpoint validateAndRecoverCheckpoint(
      UploadCheckpoint checkpoint, MultipartFile file) {
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
   * @param objectKey 文件对象键
   * @param lockToken 锁令牌
   */
  protected void releaseConcurrencyLock(String objectKey, String lockToken) {
    if (concurrencyGuard != null && lockToken != null) {
      try {
        concurrencyGuard.release(objectKey, lockToken);
      } catch (Exception e) {
        log.warn(
            "[Storage] releaseConcurrencyLock failed, object={}, error={}",
            objectKey,
            e.getMessage());
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

  /** 计算输入流的 MD5（会消费流，调用者需自行重新获取流） */
  protected String computeMd5(InputStream inputStream) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        md.update(buffer, 0, read);
      }
      byte[] digest = md.digest();
      return HexUtils.encode(digest);
    } catch (Exception e) {
      log.warn("[Storage] computeMd5 failed, message={}", e.getMessage());
      return null;
    }
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
   * @param prefix 对象前缀过滤
   * @param cursor 分页游标
   * @param maxKeys 每页最大返回数量
   * @return 分页结果
   */
  protected abstract ListObjectsResult doListObjects(
      String bucketName, String prefix, String cursor, int maxKeys);

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
   * @param bucketName 存储桶名称
   * @param objectName 对象路径
   * @param inputStream 数据输入流
   * @param size 数据大小
   * @param contentType 内容类型
   */
  protected abstract void doPutObject(
      String bucketName, String objectName, InputStream inputStream, long size, String contentType);

  /**
   * 读取对象内容
   *
   * @param bucketName 存储桶名称
   * @param objectName 对象路径
   * @param offset 起始偏移（null 表示从 0 开始）
   * @param length 读取长度（null 表示读取全部）
   * @return 输入流
   */
  protected abstract InputStream doGetObject(
      String bucketName, String objectName, Long offset, Long length);

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
   * @param bucketName 存储桶名称
   * @param objectName 对象路径
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
  protected abstract ChunkedUploadResult doInitiateMultipartUpload(
      String bucketName, String objectName);

  /**
   * 上传单个分片
   *
   * @param bucketName 存储桶名称
   * @param chunkObjectName 分片对象名称
   * @param uploadId 分片任务 ID
   * @param partNumber 分片编号
   * @param inputStream 分片数据流
   * @param size 分片大小
   */
  protected abstract void doUploadPart(
      String bucketName,
      String chunkObjectName,
      String uploadId,
      int partNumber,
      InputStream inputStream,
      long size);

  /**
   * 完成分片上传并合并
   *
   * @param bucketName 存储桶名称
   * @param objectName 对象路径
   * @param uploadId 分片任务 ID
   * @param partNumbers 已上传的分片编号列表（升序）
   */
  protected abstract void doCompleteMultipartUpload(
      String bucketName, String objectName, String uploadId, List<Integer> partNumbers);

  /**
   * 中止分片上传（清理已上传的分片）
   *
   * @param bucketName 存储桶名称
   * @param objectName 对象路径
   * @param uploadId 分片任务 ID
   */
  protected abstract void doAbortMultipartUpload(
      String bucketName, String objectName, String uploadId);

  /**
   * 列举已上传的分片
   *
   * @param bucketName 存储桶名称
   * @param objectName 对象路径
   * @param uploadId 分片任务 ID
   * @return 已上传分片信息列表
   */
  protected abstract List<PartInfo> listParts(
      String bucketName, String objectName, String uploadId);
}
