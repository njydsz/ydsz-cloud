package com.remisoft.common.excel.spring.boot;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.excel.core.config.ExcelConfig;

/**
 * Excel 模块健康检查指示器
 *
 * <p>用于 Spring Boot Actuator 的健康检查端点，展示 Excel 模块的运行状态和关键配置信息。</p>
 *
 * <h3>检查项</h3>
 * <ul>
 *   <li>快速读取器是否启用</li>
 *   <li>快速写入器是否启用</li>
 *   <li>公式注入防护是否启用</li>
 *   <li>最大读取/写入文件大小限制</li>
 *   <li>流式解析阈值</li>
 *   <li>临时目录是否可写</li>
 *   <li>ZIP 压缩级别</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ExcelConfig
 */
public class ExcelHealthIndicator implements HealthIndicator {

    private final ExcelConfig config;

    public ExcelHealthIndicator(ExcelConfig config) {
        this.config = config;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fastReader", config.isUseFastReader());
        details.put("fastWriter", config.isUseFastWriter());
        details.put("formulaInjectionProtection", config.isFormulaInjectionProtection());
        details.put("maxReadFileSizeMB", config.getMaxReadFileSizeMB());
        details.put("maxWriteFileSizeMB", config.getMaxWriteFileSizeMB());
        details.put("streamingParseThresholdMB", config.getStreamingParseThresholdMB());
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        details.put("tempDirWritable", tempDir.canWrite());
        details.put("compressionLevel", config.getCompressionLevel());
        return Health.up().withDetails(details).build();
    }
}
