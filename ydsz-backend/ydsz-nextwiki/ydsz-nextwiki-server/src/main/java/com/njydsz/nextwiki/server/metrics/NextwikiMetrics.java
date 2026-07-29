package com.njydsz.nextwiki.server.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki Micrometer 指标采集。
 *
 * <p>P0-2 架构优化：继承 {@link AbstractModuleMetrics}，统一指标前缀 {@code ydsz_nextwiki_}，
 * 消除手动 Counter 创建和 {@code @PostConstruct} 样板代码。
 *
 * <p>暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 * <ul>
 *   <li>{@code ydsz_nextwiki_file_upload_total} — 文件上传次数</li>
 *   <li>{@code ydsz_nextwiki_file_download_total} — 文件下载次数</li>
 *   <li>{@code ydsz_nextwiki_file_delete_total} — 文件删除次数</li>
 *   <li>{@code ydsz_nextwiki_share_create_total} — 分享创建次数</li>
 *   <li>{@code ydsz_nextwiki_search_total} — 搜索请求次数</li>
 *   <li>{@code ydsz_nextwiki_preview_generate_total} — 预览生成次数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class NextwikiMetrics extends AbstractModuleMetrics {

    public NextwikiMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "ydsz_nextwiki_");
        log.info("[NextwikiMetrics] 初始化完成，指标前缀 ydsz_nextwiki_");
    }

    public void recordUpload() {
        incrementCounter("file_upload_total");
    }

    public void recordDownload() {
        incrementCounter("file_download_total");
    }

    public void recordDelete() {
        incrementCounter("file_delete_total");
    }

    public void recordShare() {
        incrementCounter("share_create_total");
    }

    public void recordSearch() {
        incrementCounter("search_total");
    }

    public void recordPreview() {
        incrementCounter("preview_generate_total");
    }
}
