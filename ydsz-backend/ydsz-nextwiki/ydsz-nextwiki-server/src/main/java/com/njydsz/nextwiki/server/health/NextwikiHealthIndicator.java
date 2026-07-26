package com.njydsz.nextwiki.server.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 健康检查。
 *
 * <p>职责：报告存储可用性、数据库连接状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextwikiHealthIndicator extends AbstractModuleHealthIndicator {

    private final FileNodeRepository fileNodeRepository;
    private final NextwikiMetrics nextwikiMetrics;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    public void recordUpload() {
        nextwikiMetrics.recordUpload();
    }

    public void recordDownload() {
        nextwikiMetrics.recordDownload();
    }

    public void recordDelete() {
        nextwikiMetrics.recordDelete();
    }

    public void recordShare() {
        nextwikiMetrics.recordShare();
    }

    public void recordSearch() {
        nextwikiMetrics.recordSearch();
    }

    public void recordPreview() {
        nextwikiMetrics.recordPreview();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        boolean storageAvailable = fileStorageProvider != null;
        builder.withDetail("storageAvailable", storageAvailable);

        // 数据库探针
        checkTableProbe(builder, "databaseConnected", () -> fileNodeRepository.countByUser("health-check"));

        if (!storageAvailable) {
            builder.withDetail("warning", "文件存储未配置，上传功能不可用");
        }
    }
}
