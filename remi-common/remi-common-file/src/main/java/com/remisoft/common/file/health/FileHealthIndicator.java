package com.remisoft.common.file.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.file.config.FileProperties;
import com.remisoft.common.file.metrics.FileMetrics;
import com.remisoft.common.file.service.FileDedupService;
import com.remisoft.common.file.storage.IFileStorage;
import com.remisoft.common.file.storage.IFileStorageProvider;
import com.remisoft.common.file.storage.StorageRetryHelper;
import com.remisoft.common.file.virus.VirusScanner;
import com.remisoft.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 存储后端健康检查指示器
 * <p>注册到 Spring Boot Actuator 健康端点（/actuator/health），
 * 检查存储连接可用性、bucket 是否存在，并报告去重/病毒扫描/重试等组件状态。
 *
 * <p>仅在引入 spring-boot-actuator 依赖时生效（通过 @ConditionalOnClass 控制）。
 *
 * @author remi-team
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

    /**
     * 执行一次存储后端连通性探测，并汇总文件模块各可选组件的启用状态。
     *
     * <p><b>判定规则</b>（决定 Actuator 整体健康状态）：
     * <ol>
     *   <li>配置了 bucket 但云端不存在 → {@code DOWN}，因为此时所有上传必然失败；</li>
     *   <li>探测过程抛出任何异常（网络不通、鉴权失败等）→ {@code DOWN}，
     *       异常信息写入 {@code error} 明细，同时打 warn 日志而非 error，
     *       避免健康检查高频轮询刷屏；</li>
     *   <li>未配置 bucket 时不判定为异常，仅以 {@code bucketConfigured=false} 提示，
     *       因为本地存储（local）模式本就不需要 bucket。</li>
     * </ol>
     *
     * <p>去重、病毒扫描、重试、指标四个组件均通过 {@link ObjectProvider} 惰性获取，
     * 缺失时只记录为未启用，<b>不影响健康状态</b>——它们都是可选增强能力。
     *
     * <p><b>性能注意：</b>{@code bucketExists} 会发起一次真实的远端请求，
     * Actuator 健康端点若被高频轮询会产生额外的存储 API 调用与费用。
     *
     * @return 健康检查结果；{@code UP} 表示存储可用，{@code DOWN} 表示不可用，
     *         两种情况均携带 storageType、bucket、各组件开关等诊断明细
     */
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
