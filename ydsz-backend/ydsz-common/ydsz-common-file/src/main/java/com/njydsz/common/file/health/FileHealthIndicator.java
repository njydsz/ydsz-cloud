package com.njydsz.common.file.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.file.config.FileProperties;
import com.njydsz.common.file.metrics.FileMetrics;
import com.njydsz.common.file.service.FileDedupService;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.file.storage.StorageRetryHelper;
import com.njydsz.common.file.virus.VirusScanner;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 存储后端健康检查指示器
 * <p>注册到 Spring Boot Actuator 健康端点（/actuator/health），
 * 检查存储连接可用性、bucket 是否存在，并报告去重/病毒扫描/重试等组件状态。
 *
 * <p>仅在引入 spring-boot-actuator 依赖时生效（通过 @ConditionalOnClass 控制）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
public class FileHealthIndicator implements HealthIndicator {

    private final IFileStorageProvider fileStorageProvider;
    private final FileProperties fileProperties;
    private final ObjectProvider<FileDedupService> dedupProvider;
    private final ObjectProvider<VirusScanner> virusScannerProvider;
    private final ObjectProvider<StorageRetryHelper> retryHelperProvider;
    private final ObjectProvider<FileMetrics> metricsProvider;

    public FileHealthIndicator(IFileStorageProvider fileStorageProvider,
                               FileProperties fileProperties,
                               ObjectProvider<FileDedupService> dedupProvider,
                               ObjectProvider<VirusScanner> virusScannerProvider,
                               ObjectProvider<StorageRetryHelper> retryHelperProvider,
                               ObjectProvider<FileMetrics> metricsProvider) {
        this.fileStorageProvider = fileStorageProvider;
        this.fileProperties = fileProperties;
        this.dedupProvider = dedupProvider;
        this.virusScannerProvider = virusScannerProvider;
        this.retryHelperProvider = retryHelperProvider;
        this.metricsProvider = metricsProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            IFileStorage storage = fileStorageProvider.getStorage();
            details.put("storageType", storage.getClass().getSimpleName());
            details.put("storageTypeConfig", fileProperties.getType());

            String bucketName = fileProperties.getBucket();
            if (StringUtils.isBlank(bucketName)) {
                details.put("bucketConfigured", false);
            } else {
                details.put("bucketConfigured", true);
                boolean bucketExists = storage.bucketExists(bucketName);
                details.put("bucketExists", bucketExists);
                if (!bucketExists) {
                    return Health.down().withDetails(details).build();
                }
            }

            FileDedupService dedup = dedupProvider.getIfAvailable();
            details.put("dedupEnabled", dedup != null);

            VirusScanner scanner = virusScannerProvider.getIfAvailable();
            details.put("virusScanner", scanner != null ? scanner.getClass().getSimpleName() : "none");
            details.put("virusScannerAvailable", scanner != null && scanner.isAvailable());

            StorageRetryHelper retryHelper = retryHelperProvider.getIfAvailable();
            details.put("retryEnabled", retryHelper != null);
            if (retryHelper != null) {
                details.put("retryMaxRetries", retryHelper.getMaxRetries());
            }

            FileMetrics metrics = metricsProvider.getIfAvailable();
            details.put("metricsEnabled", metrics != null && metrics.isAvailable());

            details.put("magicNumberCheck", fileProperties.isCheckMagicNumber());
            details.put("maxFileSize", fileProperties.getMaxFileSize());

            return Health.up().withDetails(details).build();
        } catch (Exception e) {
            log.warn("[FileHealthIndicator] storage health check failed: {}", e.getMessage());
            details.put("error", e.getMessage());
            return Health.down().withDetails(details).build();
        }
    }
}
