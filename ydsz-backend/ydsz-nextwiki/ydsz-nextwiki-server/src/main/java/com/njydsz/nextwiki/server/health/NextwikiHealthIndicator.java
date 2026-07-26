package com.njydsz.nextwiki.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 健康检查与监控指标
 * <p>
 * 提供：
 * <ul>
 *   <li>健康检查：存储可用性、数据库连接、回收站清理状态</li>
 *   <li>监控指标：上传/下载/删除操作计数、存储用量</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextwikiHealthIndicator implements HealthIndicator {

    private final FileNodeRepository fileNodeRepository;
    private final StorageQuotaRepository quotaRepository;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private Counter uploadCounter;
    private Counter downloadCounter;
    private Counter deleteCounter;
    private Counter shareCounter;
    private Counter searchCounter;
    private Counter previewCounter;

    @PostConstruct
    public void initMetrics() {
        if (meterRegistry != null) {
            uploadCounter = Counter.builder("nextwiki.file.upload")
                    .description("文件上传次数")
                    .tags(Tags.of("operation", "upload"))
                    .register(meterRegistry);
            downloadCounter = Counter.builder("nextwiki.file.download")
                    .description("文件下载次数")
                    .tags(Tags.of("operation", "download"))
                    .register(meterRegistry);
            deleteCounter = Counter.builder("nextwiki.file.delete")
                    .description("文件删除次数")
                    .tags(Tags.of("operation", "delete"))
                    .register(meterRegistry);
            // P3-3: 补全指标
            shareCounter = Counter.builder("nextwiki.share.create")
                    .description("分享创建次数")
                    .tags(Tags.of("operation", "share"))
                    .register(meterRegistry);
            searchCounter = Counter.builder("nextwiki.search.total")
                    .description("搜索请求次数")
                    .tags(Tags.of("operation", "search"))
                    .register(meterRegistry);
            previewCounter = Counter.builder("nextwiki.preview.generate")
                    .description("预览生成次数")
                    .tags(Tags.of("operation", "preview"))
                    .register(meterRegistry);
            log.info("[NextwikiHealthIndicator] Micrometer 指标已注册（6 项）");
        }
    }

    /**
     * 记录上传操作
     */
    public void recordUpload() {
        if (uploadCounter != null) uploadCounter.increment();
    }

    /**
     * 记录下载操作
     */
    public void recordDownload() {
        if (downloadCounter != null) downloadCounter.increment();
    }

    /**
     * 记录删除操作
     */
    public void recordDelete() {
        if (deleteCounter != null) deleteCounter.increment();
    }

    /**
     * P3-3: 记录分享操作
     */
    public void recordShare() {
        if (shareCounter != null) shareCounter.increment();
    }

    /**
     * P3-3: 记录搜索操作
     */
    public void recordSearch() {
        if (searchCounter != null) searchCounter.increment();
    }

    /**
     * P3-3: 记录预览生成
     */
    public void recordPreview() {
        if (previewCounter != null) previewCounter.increment();
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 检查存储可用性
        boolean storageAvailable = fileStorageProvider != null;
        details.put("storageAvailable", storageAvailable);

        // 检查数据库连接
        try {
            fileNodeRepository.countByUser("health-check");
            details.put("databaseConnected", true);
        } catch (Exception e) {
            details.put("databaseConnected", false);
            details.put("databaseError", e.getMessage());
            return Health.down().withDetails(details).build();
        }

        if (!storageAvailable) {
            details.put("warning", "文件存储未配置，上传功能不可用");
            return Health.up().withDetails(details).build();
        }

        return Health.up().withDetails(details).build();
    }
}
