package com.njydsz.common.excel.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.config.ExcelConfig;

/**
 * Excel 模块 Spring Boot 自动配置
 *
 * <p>通过 {@code ydsz.excel.*} 前缀绑定 application.yml 配置，自动注册 {@link ExcelTemplate} Bean。
 * 仅在 Spring Boot 环境下生效，核心模块不依赖 Spring。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // application.yml
 * ydsz:
 *   excel:
 *     default-date-format: "yyyy-MM-dd"
 *     use-fast-writer: true
 *     max-write-file-size-mb: 50
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ExcelFacade.class)
@ConditionalOnProperty(prefix = "ydsz.excel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExcelProperties.class)
public class ExcelAutoConfiguration {

    /**
     * 注册 ExcelTemplate Bean。
     *
     * @param config 基于配置属性构建的 ExcelConfig
     * @return ExcelTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelTemplate excelTemplate(ExcelConfig config) {
        return new ExcelTemplate(config);
    }

    /**
     * 基于配置属性构建 ExcelConfig。
     *
     * @param properties 配置属性
     * @return ExcelConfig 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelConfig excelConfig(ExcelProperties properties) {
        return ExcelConfig.builder()
                .readBufferSize(properties.getReadBufferSize())
                .writeBufferSize(properties.getWriteBufferSize())
                .defaultDateFormat(properties.getDefaultDateFormat())
                .defaultNumberFormat(properties.getDefaultNumberFormat())
                .automaticTrim(properties.getAutomaticTrim())
                .useFastReader(properties.getUseFastReader())
                .useFastWriter(properties.getUseFastWriter())
                .streamingParseThresholdMb(properties.getStreamingParseThresholdMb())
                .maxReadFileSizeMb(properties.getMaxReadFileSizeMb())
                .maxWriteFileSizeMb(properties.getMaxWriteFileSizeMb())
                .compressionLevel(properties.getCompressionLevel())
                .formulaInjectionProtection(properties.getFormulaInjectionProtection())
                .strictNumberConversion(properties.getStrictNumberConversion())
                .headRowNumber(properties.getHeadRowNumber())
                .writeCacheSize(properties.getWriteCacheSize())
                .build();
    }
}
