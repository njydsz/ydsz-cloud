package com.njydsz.pmis.common.docs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档处理模块配置属性
 * <p>
 * 所有配置统一前缀 {@code ydsz.docs.*}。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.docs")
public class DocsProperties {

    /** 是否启用文档处理模块（默认启用） */
    private boolean enabled = true;

    /** 最大文件大小（MB），超过则拒绝解析 */
    private int maxFileSizeMb = 50;

    /** 解析超时时间（秒） */
    private int parseTimeoutSeconds = 60;

    /** 是否启用安全扫描 */
    private boolean securityScanEnabled = true;

    /** 是否启用 PII 检测 */
    private boolean piiDetectionEnabled = true;

    /** 是否启用预处理流水线 */
    private boolean preprocessEnabled = true;

    /** 是否启用水印功能 */
    private boolean watermarkEnabled = true;

    /** 是否启用脱敏功能 */
    private boolean redactEnabled = true;

    /** 异步解析线程池大小 */
    private int asyncPoolSize = 4;

    /** 异步解析队列容量 */
    private int asyncQueueCapacity = 100;

    /** 文本分块最大大小（字符数） */
    private int maxChunkSize = 2000;

    /** 文本分块重叠大小（字符数） */
    private int chunkOverlap = 200;
}
