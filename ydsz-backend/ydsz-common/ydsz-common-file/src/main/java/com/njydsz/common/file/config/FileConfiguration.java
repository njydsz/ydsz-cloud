package com.njydsz.common.file.config;

import java.util.Collections;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.file.health.FileHealthIndicator;
import com.njydsz.common.file.lifecycle.FileLifecycleManager;
import com.njydsz.common.file.metrics.FileMetrics;
import com.njydsz.common.file.service.FileDedupService;
import com.njydsz.common.file.storage.CheckpointService;
import com.njydsz.common.file.storage.CheckpointStore;
import com.njydsz.common.file.storage.DefaultCheckpointService;
import com.njydsz.common.file.storage.DelegatingCheckpointStore;
import com.njydsz.common.file.storage.DelegatingMultipartContextStore;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.file.storage.InMemoryMultipartContextStore;
import com.njydsz.common.file.storage.LocalCheckpointStore;
import com.njydsz.common.file.storage.MultipartContextStore;
import com.njydsz.common.file.storage.RedisCheckpointStore;
import com.njydsz.common.file.storage.RedisMultipartContextStore;
import com.njydsz.common.file.storage.StorageRetryHelper;
import com.njydsz.common.file.storage.UploadConcurrencyGuard;
import com.njydsz.common.file.storage.DefaultStorageFactory;
import com.njydsz.common.file.util.FileTypeValidator;
import com.njydsz.common.file.virus.NoOpVirusScanner;
import com.njydsz.common.file.virus.VirusScanner;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * File storage auto-configuration.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties({FileProperties.class, FileUploadProperties.class, FileLifecycleProperties.class})
@ConditionalOnProperty(prefix = "ydsz.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileConfiguration {

    private static final int MULTIPART_CONTEXT_TIMEOUT_MINUTES = 60;
    private final MultipartContextStore multipartContextStore;

    public FileConfiguration(MultipartContextStore multipartContextStore) {
        this.multipartContextStore = multipartContextStore;
    }

    @Bean
    @ConditionalOnMissingBean(MultipartContextStore.class)
    public MultipartContextStore multipartContextStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        StringRedisTemplate template = redisProvider.getIfAvailable();
        if (template != null) {
            return new DelegatingMultipartContextStore(new RedisMultipartContextStore(template), null);
        }
        log.warn("Redis not available, falling back to InMemoryMultipartContextStore.");
        return new InMemoryMultipartContextStore();
    }

    @Bean
    @ConditionalOnMissingBean(CheckpointStore.class)
    public CheckpointStore checkpointStore(FileProperties props, ObjectProvider<StringRedisTemplate> redisProvider) {
        StringRedisTemplate template = redisProvider.getIfAvailable();
        CheckpointStore fallback = new LocalCheckpointStore(props.getCheckpointDir());
        if (template != null) {
            return new DelegatingCheckpointStore(new RedisCheckpointStore(template), fallback);
        }
        log.warn("Redis not available, falling back to LocalCheckpointStore.");
        return fallback;
    }

    @Bean
    @ConditionalOnMissingBean(CheckpointService.class)
    public CheckpointService checkpointService(CheckpointStore store, MultipartContextStore multipartStore) {
        return new DefaultCheckpointService(store, (b, o, u) -> Collections.emptyList(), 24 * 3600L);
    }

    @Bean
    @ConditionalOnMissingBean(FileMetrics.class)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public FileMetrics fileMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        return new FileMetrics(registryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(VirusScanner.class)
    public VirusScanner virusScanner() {
        log.info("No VirusScanner bean found, registering NoOpVirusScanner.");
        return new NoOpVirusScanner();
    }

    @Bean
    @ConditionalOnMissingBean(StorageRetryHelper.class)
    public StorageRetryHelper storageRetryHelper(FileProperties props) {
        int retryCount = props.getRetryCount() != null ? props.getRetryCount() : 3;
        return new StorageRetryHelper(retryCount, 500L);
    }

    @Bean
    @ConditionalOnBean({RedisStringOps.class})
    @ConditionalOnMissingBean(FileDedupService.class)
    public FileDedupService fileDedupService(RedisStringOps redisStringOps, IFileStorageProvider provider) {
        return new FileDedupService(redisStringOps, provider.getStorage());
    }

    @Bean
    @ConditionalOnProperty(prefix = "ydsz.file.lifecycle", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(FileLifecycleManager.class)
    public FileLifecycleManager fileLifecycleManager(FileLifecycleProperties props, IFileStorageProvider provider) {
        return new FileLifecycleManager(props, provider);
    }

    @Bean
    @ConditionalOnMissingBean(IFileStorageProvider.class)
    public IFileStorageProvider fileStorageProvider(FileProperties fileProperties, FileUploadProperties fileUploadProperties, MultipartContextStore multipartContextStore, CheckpointService checkpointService, ObjectProvider<StringRedisTemplate> redisProvider, ObjectProvider<FileDedupService> dedupProvider, ObjectProvider<VirusScanner> virusScannerProvider, ObjectProvider<FileMetrics> metricsProvider, ObjectProvider<StorageRetryHelper> retryHelperProvider) {
        FileTypeValidator.init(fileProperties.isCheckMagicNumber());
        DefaultStorageFactory factory = new DefaultStorageFactory(fileProperties, fileUploadProperties);
        factory.setMultipartContextStore(multipartContextStore);
        factory.setCheckpointService(checkpointService);
        UploadConcurrencyGuard guard = buildConcurrencyGuardIfEnabled(fileProperties, redisProvider.getIfAvailable());
        if (guard != null) factory.setConcurrencyGuard(guard);
        FileDedupService dedup = dedupProvider.getIfAvailable();
        if (dedup != null) factory.setFileDedupService(dedup);
        VirusScanner scanner = virusScannerProvider.getIfAvailable();
        if (scanner != null) factory.setVirusScanner(scanner);
        FileMetrics metrics = metricsProvider.getIfAvailable();
        if (metrics != null) factory.setFileMetrics(metrics);
        StorageRetryHelper retryHelper = retryHelperProvider.getIfAvailable();
        if (retryHelper != null) factory.setRetryHelper(retryHelper);
        return factory;
    }

    private UploadConcurrencyGuard buildConcurrencyGuardIfEnabled(FileProperties props, StringRedisTemplate redis) {
        if (redis == null) return null;
        var config = props.getConcurrencyControl();
        if (config == null || !config.isEnabled()) return null;
        return new UploadConcurrencyGuard(redis, config);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean(FileHealthIndicator.class)
    public FileHealthIndicator storageHealthIndicator(IFileStorageProvider provider, FileProperties props,
                                                       ObjectProvider<FileDedupService> dedupProvider,
                                                       ObjectProvider<VirusScanner> virusScannerProvider,
                                                       ObjectProvider<StorageRetryHelper> retryHelperProvider,
                                                       ObjectProvider<FileMetrics> metricsProvider) {
        return new FileHealthIndicator(provider, props, dedupProvider, virusScannerProvider, retryHelperProvider, metricsProvider);
    }

    @Scheduled(fixedRate = 3_600_000)
    public void cleanExpiredMultipartContexts() {
        if (multipartContextStore != null) {
            multipartContextStore.cleanExpired(MULTIPART_CONTEXT_TIMEOUT_MINUTES);
            log.debug("Cleaned expired multipart contexts.");
        }
    }
}
