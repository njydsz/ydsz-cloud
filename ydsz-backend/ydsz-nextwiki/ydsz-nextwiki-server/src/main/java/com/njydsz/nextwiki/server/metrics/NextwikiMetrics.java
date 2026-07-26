package com.njydsz.nextwiki.server.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * NextWiki Micrometer 指标采集（P1-R4: 从 NextwikiHealthIndicator 拆分）
 * <p>
 * 职责分离：HealthIndicator 仅报告健康状态，Metrics 仅采集指标。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Component
public class NextwikiMetrics {

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
            log.info("[NextwikiMetrics] Micrometer 指标已注册（6 项）");
        }
    }

    public void recordUpload() {
        if (uploadCounter != null) uploadCounter.increment();
    }

    public void recordDownload() {
        if (downloadCounter != null) downloadCounter.increment();
    }

    public void recordDelete() {
        if (deleteCounter != null) deleteCounter.increment();
    }

    public void recordShare() {
        if (shareCounter != null) shareCounter.increment();
    }

    public void recordSearch() {
        if (searchCounter != null) searchCounter.increment();
    }

    public void recordPreview() {
        if (previewCounter != null) previewCounter.increment();
    }
}
