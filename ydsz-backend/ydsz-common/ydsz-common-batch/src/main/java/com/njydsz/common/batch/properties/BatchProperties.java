package com.njydsz.common.batch.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 批处理模块配置属性
 *
 * <p>前缀：{@code ydsz.batch}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.batch")
public class BatchProperties {

    /** 是否启用批处理模块 */
    private boolean enabled = true;

    /** 默认 Chunk 大小 */
    private int defaultCommitInterval = 100;

    /** 默认最大重试次数 */
    private int defaultMaxRetries = 3;

    /** 默认重试退避（毫秒） */
    private long defaultRetryBackoffMillis = 1000L;

    /** 默认最大跳过数 */
    private int defaultMaxSkipCount = 1000;

    /** 默认线程池大小 */
    private int defaultThreadPoolSize = 8;

    /** 是否启用指标埋点 */
    private boolean metricsEnabled = true;
}
