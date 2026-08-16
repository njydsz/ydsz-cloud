package com.njydsz.common.docs.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 文档处理模块配置属性
 * <p>
 * 所有配置统一前缀 {@code ydsz.docs.*}.
 *
 * <p><b>设计原则：</b>仅保留核心配置项，业务特定参数下沉到业务模块配置，
 * 避免 common 模块承担过多场景化职责。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.docs")
public class DocsProperties {

    /** 是否启用文档处理模块 */
    private boolean enabled = true;

    /** 文件大小上限（MB），超出应在上游网关拦截 */
    @Min(1)
    @Max(500)
    private int maxFileSizeMb = 50;

    /** 解析超时时间（秒） */
    @Min(1)
    @Max(600)
    private int parseTimeoutSeconds = 60;

    /** 是否启用安全扫描 */
    private boolean securityScanEnabled = true;

    /** 是否启用 PII 检测 */
    private boolean piiDetectionEnabled = true;

    /** 是否启用预处理流水线 */
    private boolean preprocessEnabled = true;

    /** 异步解析线程池大小 */
    @Min(1)
    @Max(64)
    private int asyncPoolSize = 4;

    /** 异步解析队列容量 */
    @Min(1)
    @Max(10000)
    private int asyncQueueCapacity = 100;

    /** 高风险时是否阻止解析 */
    private boolean blockOnHighRisk = false;
}
