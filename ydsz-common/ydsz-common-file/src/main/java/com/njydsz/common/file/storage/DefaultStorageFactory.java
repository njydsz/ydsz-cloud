package com.njydsz.common.file.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.config.FileProperties;
import com.njydsz.common.file.config.FileUploadProperties;
import com.njydsz.common.file.exception.FileExceptionCode;
import com.njydsz.common.file.metrics.FileMetrics;
import com.njydsz.common.file.service.FileDedupService;
import com.njydsz.common.file.storage.platform.CosStorage;
import com.njydsz.common.file.storage.platform.LocalStorage;
import com.njydsz.common.file.storage.platform.MinioStorage;
import com.njydsz.common.file.storage.platform.ObsStorage;
import com.njydsz.common.file.storage.platform.OssStorage;
import com.njydsz.common.file.storage.platform.QiniuStorage;
import com.njydsz.common.file.storage.platform.S3Storage;
import com.njydsz.common.file.util.FileTypeValidator;
import com.njydsz.common.file.virus.VirusScanner;

/**
 * 默认文件存储工厂（实现 {@link IFileStorageProvider}）
 *
 * <p>根据配置的存储类型（local/minio/s3/oss/cos/obs/qiniu）创建对应的存储实例，
 * 并将共享依赖（分片上下文存储、检查点服务、并发保护器、去重服务、病毒扫描、监控指标、重试助手） 注入到每个存储实例中。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>单例缓存：同一存储类型只创建一次实例
 *   <li>支持 SPI 扩展：通过 {@link #register} 注册自定义存储类型
 *   <li>依赖注入：创建实例后自动注入所有可选依赖
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DefaultStorageFactory implements IFileStorageProvider {

  /** 文件存储配置 */
  private final FileProperties serverProperties;

  /** 分片上传配置 */
  private final FileUploadProperties fileUploadProperties;

  /** 存储实例缓存（存储类型 → 实例） */
  private final ConcurrentMap<String, IFileStorage> storageCache = new ConcurrentHashMap<>();

  /** 自定义存储提供者（SPI 扩展点） */
  private final ConcurrentMap<String, Function<DefaultStorageFactory, IFileStorage>>
      customProviders = new ConcurrentHashMap<>();

  /** 分片上传上下文存储 */
  private MultipartContextStore multipartContextStore;

  /** 并发上传保护器 */
  private UploadConcurrencyGuard concurrencyGuard;

  /** 检查点服务 */
  private CheckpointService checkpointService;

  /** 文件去重服务 */
  private FileDedupService fileDedupService;

  /** 病毒扫描接口 */
  private VirusScanner virusScanner;

  /** 监控指标收集器 */
  private FileMetrics fileMetrics;

  /** 重试助手 */
  private StorageRetryHelper retryHelper;

  /** 文件类型校验器 */
  private FileTypeValidator fileTypeValidator;

  /** 批量删除专用线程池（ydsz-common-thread 管理） */
  private ExecutorService deleteExecutor;

  /** 异步上传专用线程池（ydsz-common-thread 管理） */
  private ExecutorService asyncUploadExecutor;

  /**
   * 构造存储工厂
   *
   * @param props 文件存储配置
   * @param uploadProps 分片上传配置
   */
  public DefaultStorageFactory(FileProperties props, FileUploadProperties uploadProps) {
    this.serverProperties = props;
    this.fileUploadProperties = uploadProps;
  }

  /**
   * 设置分片上传上下文存储，并同步更新已创建的存储实例
   *
   * @param store 分片上传上下文存储实例
   */
  public void setMultipartContextStore(MultipartContextStore store) {
    this.multipartContextStore = store;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs && store != null) {
                afs.setMultipartContextStore(store);
              }
            });
  }

  /**
   * 设置并发上传保护器，并同步更新已创建的存储实例
   *
   * @param guard 并发上传保护器
   */
  public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
    this.concurrencyGuard = guard;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setConcurrencyGuard(guard);
              }
            });
  }

  /**
   * 设置检查点服务，并同步更新已创建的存储实例
   *
   * @param service 检查点服务
   */
  public void setCheckpointService(CheckpointService service) {
    this.checkpointService = service;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setCheckpointService(service);
              }
            });
  }

  /**
   * 设置文件去重服务，并同步更新已创建的存储实例
   *
   * @param service 文件去重服务
   */
  public void setFileDedupService(FileDedupService service) {
    this.fileDedupService = service;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setFileDedupService(service);
              }
            });
  }

  /**
   * 设置病毒扫描接口，并同步更新已创建的存储实例
   *
   * @param scanner 病毒扫描接口
   */
  public void setVirusScanner(VirusScanner scanner) {
    this.virusScanner = scanner;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setVirusScanner(scanner);
              }
            });
  }

  /**
   * 设置监控指标收集器，并同步更新已创建的存储实例
   *
   * @param metrics 监控指标收集器
   */
  public void setFileMetrics(FileMetrics metrics) {
    this.fileMetrics = metrics;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setFileMetrics(metrics);
              }
            });
  }

  /**
   * 设置重试助手，并同步更新已创建的存储实例
   *
   * @param helper 重试助手
   */
  public void setRetryHelper(StorageRetryHelper helper) {
    this.retryHelper = helper;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setRetryHelper(helper);
              }
            });
  }

  /**
   * 设置文件类型校验器，并同步更新已创建的存储实例
   *
   * @param validator 文件类型校验器
   */
  public void setFileTypeValidator(FileTypeValidator validator) {
    this.fileTypeValidator = validator;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setFileTypeValidator(validator);
              }
            });
  }

  /**
   * 设置批量删除专用线程池，并同步更新已创建的存储实例
   *
   * @param executor 批量删除线程池（ydsz-common-thread 管理的 Bean）
   */
  public void setDeleteExecutor(ExecutorService executor) {
    this.deleteExecutor = executor;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setDeleteExecutor(executor);
              }
            });
  }

  /**
   * 设置异步上传专用线程池，并同步更新已创建的存储实例
   *
   * @param executor 异步上传线程池（ydsz-common-thread 管理的 Bean）
   */
  public void setAsyncUploadExecutor(ExecutorService executor) {
    this.asyncUploadExecutor = executor;
    storageCache
        .values()
        .forEach(
            s -> {
              if (s instanceof AbstractFileStorage afs) {
                afs.setAsyncUploadExecutor(executor);
              }
            });
  }

  /**
   * 获取当前配置对应的文件存储实例（单例）
   *
   * @return 文件存储实例
   * @throws BusinessException 存储类型未配置或不支持时抛出
   */
  @Override
  public IFileStorage getStorage() {
    String storageType = serverProperties.getType();
    if (storageType == null || storageType.isBlank()) {
      throw new BusinessException(FileExceptionCode.CONFIG_INVALID);
    }
    return storageCache.computeIfAbsent(storageType, this::createStorage);
  }

  /**
   * 注册自定义存储类型（SPI 扩展）
   *
   * @param type 存储类型标识（如 "custom-oss"）
   * @param provider 存储实例创建函数
   * @throws IllegalArgumentException type 或 provider 为 null 时抛出
   */
  public void register(String type, Function<DefaultStorageFactory, IFileStorage> provider) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Storage type cannot be null or empty");
    }
    if (provider == null) {
      throw new IllegalArgumentException("Storage provider cannot be null");
    }
    customProviders.put(type, provider);
  }

  /**
   * 根据存储类型创建存储实例
   *
   * @param storageType 存储类型标识
   * @return 文件存储实例
   * @throws BusinessException 不支持的存储类型时抛出
   */
  private IFileStorage createStorage(String storageType) {
    IFileStorage storage;
    Function<DefaultStorageFactory, IFileStorage> custom = customProviders.get(storageType);
    if (custom != null) {
      storage = custom.apply(this);
      return inject(storage);
    }
    switch (storageType) {
      case "local":
        storage = new LocalStorage(serverProperties, fileUploadProperties);
        break;
      case "minio":
        storage = new MinioStorage(serverProperties, fileUploadProperties);
        break;
      case "s3":
      case "aws-s3":
        storage = new S3Storage(serverProperties, fileUploadProperties);
        break;
      case "oss":
      case "aliyun":
        storage = new OssStorage(serverProperties, fileUploadProperties);
        break;
      case "cos":
      case "tencent-cos":
        storage = new CosStorage(serverProperties, fileUploadProperties);
        break;
      case "obs":
      case "huawei-obs":
        storage = new ObsStorage(serverProperties, fileUploadProperties);
        break;
      case "qiniu":
        storage = new QiniuStorage(serverProperties, fileUploadProperties);
        break;
      default:
        throw new BusinessException(FileExceptionCode.CONFIG_INVALID);
    }
    return inject(storage);
  }

  /**
   * 向存储实例注入所有可选依赖
   *
   * @param storage 文件存储实例
   * @return 注入后的存储实例
   */
  private IFileStorage inject(IFileStorage storage) {
    if (storage instanceof AbstractFileStorage afs) {
      if (multipartContextStore != null) {
        afs.setMultipartContextStore(multipartContextStore);
      }
      if (checkpointService != null) {
        afs.setCheckpointService(checkpointService);
      }
      if (concurrencyGuard != null) {
        afs.setConcurrencyGuard(concurrencyGuard);
      }
      if (fileDedupService != null) {
        afs.setFileDedupService(fileDedupService);
      }
      if (virusScanner != null) {
        afs.setVirusScanner(virusScanner);
      }
      if (fileMetrics != null) {
        afs.setFileMetrics(fileMetrics);
      }
      if (retryHelper != null) {
        afs.setRetryHelper(retryHelper);
      }
      if (fileTypeValidator != null) {
        afs.setFileTypeValidator(fileTypeValidator);
      }
      if (deleteExecutor != null) {
        afs.setDeleteExecutor(deleteExecutor);
      }
      if (asyncUploadExecutor != null) {
        afs.setAsyncUploadExecutor(asyncUploadExecutor);
      }
    }
    return storage;
  }
}
