package com.njydsz.pmis.common.file.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.config.FileProperties;
import com.njydsz.pmis.common.file.config.FileUploadProperties;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import com.njydsz.pmis.common.file.metrics.FileMetrics;
import com.njydsz.pmis.common.file.storage.platform.CosStorage;
import com.njydsz.pmis.common.file.storage.platform.LocalStorage;
import com.njydsz.pmis.common.file.storage.platform.MinioStorage;
import com.njydsz.pmis.common.file.storage.platform.ObsStorage;
import com.njydsz.pmis.common.file.storage.platform.OssStorage;
import com.njydsz.pmis.common.file.storage.platform.QiniuStorage;
import com.njydsz.pmis.common.file.storage.platform.S3Storage;
import com.njydsz.pmis.common.file.service.FileDedupService;
import com.njydsz.pmis.common.file.virus.VirusScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultStorageFactory implements IFileStorageProvider {

    private final FileProperties serverProperties;
    private final FileUploadProperties fileUploadProperties;
    private final ConcurrentMap<String, IFileStorage> storageCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Function<DefaultStorageFactory, IFileStorage>> customProviders = new ConcurrentHashMap<>();
    private MultipartContextStore multipartContextStore;
    private CheckpointStore checkpointStore;
    private UploadConcurrencyGuard concurrencyGuard;
    private CheckpointService checkpointService;
    private FileDedupService fileDedupService;
    private VirusScanner virusScanner;
    private FileMetrics fileMetrics;
    private StorageRetryHelper retryHelper;

    public DefaultStorageFactory(FileProperties props, FileUploadProperties uploadProps) {
        this.serverProperties = props;
        this.fileUploadProperties = uploadProps;
    }

    public void setMultipartContextStore(MultipartContextStore store) {
        this.multipartContextStore = store;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs && store != null) afs.setMultipartContextStore(store); });
    }

    public void setCheckpointStore(CheckpointStore store) { this.checkpointStore = store; }

    public void setConcurrencyGuard(UploadConcurrencyGuard guard) {
        this.concurrencyGuard = guard;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs) afs.setConcurrencyGuard(guard); });
    }

    public void setCheckpointService(CheckpointService service) {
        this.checkpointService = service;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs) afs.setCheckpointService(service); });
    }

    public void setFileDedupService(FileDedupService service) {
        this.fileDedupService = service;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs) afs.setFileDedupService(service); });
    }

    public void setVirusScanner(VirusScanner scanner) {
        this.virusScanner = scanner;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs) afs.setVirusScanner(scanner); });
    }

    public void setFileMetrics(FileMetrics metrics) {
        this.fileMetrics = metrics;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs) afs.setFileMetrics(metrics); });
    }

    public void setRetryHelper(StorageRetryHelper helper) {
        this.retryHelper = helper;
        storageCache.values().forEach(s -> { if (s instanceof AbstractFileStorage afs) afs.setRetryHelper(helper); });
    }

    @Override
    public IFileStorage getStorage() {
        String storageType = serverProperties.getType();
        if (storageType == null || storageType.isBlank()) throw new BusinessException(FileExceptionCode.STORAGE_CONFIG_INVALID);
        return storageCache.computeIfAbsent(storageType, this::createStorage);
    }

    public void register(String type, Function<DefaultStorageFactory, IFileStorage> provider) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Storage type cannot be null or empty");
        if (provider == null) throw new IllegalArgumentException("Storage provider cannot be null");
        customProviders.put(type, provider);
    }

    private IFileStorage createStorage(String storageType) {
        IFileStorage storage;
        Function<DefaultStorageFactory, IFileStorage> custom = customProviders.get(storageType);
        if (custom != null) { storage = custom.apply(this); return inject(storage); }
        switch (storageType) {
            case "local": storage = new LocalStorage(serverProperties, fileUploadProperties); break;
            case "minio": storage = new MinioStorage(serverProperties, fileUploadProperties); break;
            case "s3": case "aws-s3": storage = new S3Storage(serverProperties, fileUploadProperties); break;
            case "oss": case "aliyun": storage = new OssStorage(serverProperties, fileUploadProperties); break;
            case "cos": case "tencent-cos": storage = new CosStorage(serverProperties, fileUploadProperties); break;
            case "obs": case "huawei-obs": storage = new ObsStorage(serverProperties, fileUploadProperties); break;
            case "qiniu": storage = new QiniuStorage(serverProperties, fileUploadProperties); break;
            default: throw new BusinessException(FileExceptionCode.STORAGE_CONFIG_INVALID);
        }
        return inject(storage);
    }

    private IFileStorage inject(IFileStorage storage) {
        if (storage instanceof AbstractFileStorage afs) {
            if (multipartContextStore != null) afs.setMultipartContextStore(multipartContextStore);
            if (checkpointService != null) afs.setCheckpointService(checkpointService);
            if (concurrencyGuard != null) afs.setConcurrencyGuard(concurrencyGuard);
            if (fileDedupService != null) afs.setFileDedupService(fileDedupService);
            if (virusScanner != null) afs.setVirusScanner(virusScanner);
            if (fileMetrics != null) afs.setFileMetrics(fileMetrics);
            if (retryHelper != null) afs.setRetryHelper(retryHelper);
        }
        return storage;
    }
}
