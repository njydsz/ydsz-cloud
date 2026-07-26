package com.njydsz.nextwiki.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 健康检查（P1-R4: 仅负责健康状态报告，指标采集委托 NextwikiMetrics）
 * <p>
 * 职责：报告存储可用性、数据库连接状态。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextwikiHealthIndicator implements HealthIndicator {

    private final FileNodeRepository fileNodeRepository;
    private final NextwikiMetrics nextwikiMetrics;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 委托 NextwikiMetrics 记录上传操作
     */
    public void recordUpload() {
        nextwikiMetrics.recordUpload();
    }

    /**
     * 委托 NextwikiMetrics 记录下载操作
     */
    public void recordDownload() {
        nextwikiMetrics.recordDownload();
    }

    /**
     * 委托 NextwikiMetrics 记录删除操作
     */
    public void recordDelete() {
        nextwikiMetrics.recordDelete();
    }

    /**
     * 委托 NextwikiMetrics 记录分享操作
     */
    public void recordShare() {
        nextwikiMetrics.recordShare();
    }

    /**
     * 委托 NextwikiMetrics 记录搜索操作
     */
    public void recordSearch() {
        nextwikiMetrics.recordSearch();
    }

    /**
     * 委托 NextwikiMetrics 记录预览生成
     */
    public void recordPreview() {
        nextwikiMetrics.recordPreview();
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        boolean storageAvailable = fileStorageProvider != null;
        details.put("storageAvailable", storageAvailable);

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
