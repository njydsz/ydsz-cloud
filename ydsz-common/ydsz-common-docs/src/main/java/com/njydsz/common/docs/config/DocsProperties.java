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
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.docs")
public class DocsProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(500)
    private int maxFileSizeMb = 50;

    @Min(1)
    @Max(600)
    private int parseTimeoutSeconds = 60;

    private boolean securityScanEnabled = true;
    private boolean piiDetectionEnabled = true;
    private boolean preprocessEnabled = true;
    private boolean watermarkEnabled = true;
    private boolean redactEnabled = true;

    @Min(1)
    @Max(64)
    private int asyncPoolSize = 4;

    @Min(1)
    @Max(10000)
    private int asyncQueueCapacity = 100;

    @Min(100)
    @Max(100000)
    private int maxChunkSize = 2000;

    @Min(0)
    @Max(10000)
    private int chunkOverlap = 200;

    @Min(0)
    @Max(500)
    private int securityMaxScanPages = 50;

    private boolean blockOnHighRisk = false;

    /** 水印自定义字体路径（配置后优先使用） */
    private String watermarkFontPath;

    /** 文档分类规则（JSON 格式：[{"category":"合同文档","keywords":["合同","协议","条款"]}]） */
    private String classifierRules;
}
