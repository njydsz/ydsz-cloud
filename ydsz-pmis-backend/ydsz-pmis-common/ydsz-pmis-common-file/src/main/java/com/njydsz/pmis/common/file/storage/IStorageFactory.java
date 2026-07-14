package com.njydsz.pmis.common.file.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.config.FileUploadProperties;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.file.storage.platform.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认文件存储平台工厂实现
 * <p>根据 {@link FileProperties#type} 配置创建对应的存储实现实例，
 * 并对实例进行缓存（同类型多次获取不重复创建）。
 *
 * <p>支持的存储类型由 {@link StorageType} 常量定义，
 * 每次调用 {@link #getStorage()} 时使用 Double-Checked Locking 懒汉式缓存。
 *
 * <p>支持通过 {@link #register(String, IFileStorage)} 动态注册自定义存储类型。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see StorageType
 * @see IFileStorageProvider
 */
@Slf4j
public class IStorageFactory implements IFileStorageProvider {

    /** 文件存储基础配置 */
    private final FileProperties serverProperties;
    /** 文件上传配置 */
    private final FileUploadProperties fileUploadProperties;
    /** 存储实例缓存（按存储类型缓存，避免重复创建） */
    private final ConcurrentMap<String, IFileStorage> storageCache = new ConcurrentHashMap<>();
    /** 自定义存储类型注册表 */
    private final ConcurrentMap<String, Function<IStorageFactory, IFileStorage>> customProviders = new ConcurrentHashMap<>();
    /** 分片上传上下文存储 */
    private MultipartContextStore multipartContextStore;
    /** 断点续传检查点存储 */
    private CheckpointStore checkpointStore;
    /** 并发上传保护器 */
    private UploadConcurrencyGuard concurrencyGuard;
    /** 检查点服务 */
    private CheckpointService checkpointService;

    /**
     * 构造文件存储工厂
     *
     * @param serverProperties     文件存储基础配置
     * @param fileUploadProperties 文件上传配置
     */
    public IStorageFactory(FileProperties serverProperties, FileUploadProperties fileUploadProperties) {
        this.serverProperties = serverProperties;
        this.fileUploadProperties = fileUploadProperties;
    }

    /**
     * 设置分片上传上下文存储
     */
    public void setMultipartContextStore(MultipartContextStore store) {
        this.multipartContextStore = store;
        // 更新已缓存的存储实例（通过 Store 层注入）
        storageCache.values().forEach(storage -> {
            if (storage instanceof AbstractFileStorage afs && multipartContextStore != null) {
                afs.setMultipartContextStore(multipartContextStore);
            }
        });
    }

    /**
     * 设置检查点存储
     */
    public void setCheckpointStore(CheckpointStore store) {
        this.checkpointStore = store;
        // 更新已缓存的存储实例（通过 Service 层注入）
        storageCache.values().forEach(storage -> {
            if (storage instanceof AbstractFileStorage afs && checkpointService != null) {
                afs.setCheckpointService(checkpointService);
            }
        });
    }

    /**
     * 设置并发上传保护器
     */
    public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
        this.concurrencyGuard = guard;
        // 更新已缓存的存储实例
        storageCache.values().forEach(storage -> {
            if (storage instanceof AbstractFileStorage afs) {
                afs.setConcurrencyGuard(guard);
            }
        });
    }

    /**
     * 设置检查点服务
     */
    public void setCheckpointService(CheckpointService service) {
        this.checkpointService = service;
        // 更新已缓存的存储实例
        storageCache.values().forEach(storage -> {
            if (storage instanceof AbstractFileStorage afs) {
                afs.setCheckpointService(service);
            }
        });
    }

    /**
     * 根据配置获取对应的文件存储实现
     * <p>若当前存储类型已创建过实例，则直接返回缓存实例；
     * 否则创建新实例并存入缓存。
     *
     * @return 文件存储实现类
     * @throws BusinessException 若 storage.type 未配置（STORAGE_CONFIG_INVALID）
     */
    @Override
    public IFileStorage getStorage() {
        String storageType = serverProperties.getType();
        if (storageType == null || storageType.isBlank()) {
            throw new BusinessException(FileExceptionCode.STORAGE_CONFIG_INVALID);
        }
        return storageCache.computeIfAbsent(storageType, this::createStorage);
    }

    /**
     * 注册自定义存储类型
     * <p>通过 SPI 机制扩展新的存储类型，在配置文件中指定 type 值即可使用。
     *
     * @param type 存储类型标识（如 "my-custom-storage"）
     * @param provider 存储实例创建函数
     */
    public void register(String type, Function<IStorageFactory, IFileStorage> provider) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Storage type cannot be null or empty");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Storage provider cannot be null");
        }
        customProviders.put(type, provider);
        log.info("[StorageFactory] Registered custom storage type: {}", type);
    }

    /**
     * 根据存储类型创建对应的存储实现实例
     *
     * @param storageType 存储类型
     * @return 对应的 IFileStorage 实现
     * @throws BusinessException 若类型未知或实例创建失败
     */
    private IFileStorage createStorage(String storageType) {
        IFileStorage storage;

        // 优先查找自定义注册的存储类型
        Function<IStorageFactory, IFileStorage> customProvider = customProviders.get(storageType);
        if (customProvider != null) {
            storage = customProvider.apply(this);
            return injectCommonDependencies(storage);
        }

        // 内置存储类型
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
                throw new BusinessException(FileExceptionCode.STORAGE_CONFIG_INVALID);
        }

        return injectCommonDependencies(storage);
    }

    /**
     * 注入公共依赖到存储实例
     */
    private IFileStorage injectCommonDependencies(IFileStorage storage) {
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
        }
        return storage;
    }
}
