package com.njydsz.pmis.common.excel.spring.boot;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import com.njydsz.pmis.common.excel.core.config.ExcelConfig;

/**
 * Excel module health indicator.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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
