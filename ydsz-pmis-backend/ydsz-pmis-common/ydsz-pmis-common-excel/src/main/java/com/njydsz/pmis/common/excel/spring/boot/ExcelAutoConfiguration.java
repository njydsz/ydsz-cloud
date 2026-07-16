package com.njydsz.pmis.common.excel.spring.boot;

import java.util.zip.Deflater;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.excel.core.config.ExcelConfig;
import com.njydsz.pmis.common.excel.core.metrics.ExcelMetrics;
import com.njydsz.pmis.common.excel.spring.ExcelTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Excel 模块 Spring Boot 自动配置
 *
 * <p>自动注册 {@link ExcelConfig} 单例和 {@link ExcelTemplate} 模板类。
 * 通过 {@link ExcelProperties}（前缀 {@code ydsz.excel}）覆盖默认配置。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ExcelTemplate.class)
@ConditionalOnProperty(prefix = "ydsz.excel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExcelProperties.class)
public class ExcelAutoConfiguration {

    /**
     * 注册 ExcelConfig 单例，应用 Properties 配置
     *
     * @param properties 配置属性
     * @return ExcelConfig 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelConfig excelConfig(ExcelProperties properties) {
        ExcelConfig config = ExcelConfig.getInstance();

        if (properties.getReadBufferSize() != null) {
            config.setReadBufferSize(properties.getReadBufferSize());
        }
        if (properties.getWriteBufferSize() != null) {
            config.setWriteBufferSize(properties.getWriteBufferSize());
        }
        if (properties.getDefaultDateFormat() != null) {
            config.setDefaultDateFormat(properties.getDefaultDateFormat());
        }
        if (properties.getDefaultNumberFormat() != null) {
            config.setDefaultNumberFormat(properties.getDefaultNumberFormat());
        }
        if (properties.getAutomaticTrim() != null) {
            config.setAutomaticTrim(properties.getAutomaticTrim());
        }
        if (properties.getUseFastReader() != null) {
            config.setUseFastReader(properties.getUseFastReader());
        }
        if (properties.getUseFastWriter() != null) {
            config.setUseFastWriter(properties.getUseFastWriter());
        }
        if (properties.getStreamingParseThresholdMB() != null) {
            config.setStreamingParseThresholdMB(properties.getStreamingParseThresholdMB());
        }
        if (properties.getMaxReadFileSizeMB() != null) {
            config.setMaxReadFileSizeMB(properties.getMaxReadFileSizeMB());
        }
        if (properties.getMaxWriteFileSizeMB() != null) {
            config.setMaxWriteFileSizeMB(properties.getMaxWriteFileSizeMB());
        }
        if (properties.getCompressionLevel() != null) {
            config.setCompressionLevel(properties.getCompressionLevel());
        } else {
            // 默认使用 BEST_SPEED
            config.setCompressionLevel(Deflater.BEST_SPEED);
        }
        if (properties.getFormulaInjectionProtection() != null) {
            config.setFormulaInjectionProtection(properties.getFormulaInjectionProtection());
        }
        if (properties.getStrictNumberConversion() != null) {
            config.setStrictNumberConversion(properties.getStrictNumberConversion());
        }
        if (properties.getHeadRowNumber() != null) {
            config.setHeadRowNumber(properties.getHeadRowNumber());
        }
        if (properties.getWriteCacheSize() != null) {
            config.setWriteCacheSize(properties.getWriteCacheSize());
        }

        return config;
    }

    /**
     * 注册 ExcelTemplate 模板类
     *
     * @param config ExcelConfig 实例
     * @return ExcelTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelTemplate excelTemplate(ExcelConfig config) {
        return new ExcelTemplate(config);
    }

    /**
     * 注入 Micrometer MeterRegistry 到 ExcelMetrics
     *
     * <p>当 classpath 中存在 MeterRegistry 时自动注入，启用可观测性指标采集。</p>
     *
     * @param meterRegistry Micrometer 注册表（可选）
     * @return ExcelMetrics 初始化器 Bean
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "excelMetricsInitializer")
    public InitializingBean excelMetricsInitializer(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return () -> {
            MeterRegistry registry = meterRegistryProvider.getIfAvailable();
            if (registry != null) {
                ExcelMetrics.setRegistry(registry);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ExcelHealthIndicator excelHealthIndicator(ExcelConfig config) {
        return new ExcelHealthIndicator(config);
    }
}
