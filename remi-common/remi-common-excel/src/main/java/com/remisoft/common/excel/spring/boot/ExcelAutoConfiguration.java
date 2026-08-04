package com.remisoft.common.excel.spring.boot;

import java.util.zip.Deflater;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.remisoft.common.excel.core.config.ExcelConfig;
import com.remisoft.common.excel.core.metrics.ExcelMetrics;
import com.remisoft.common.excel.spring.ExcelTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Excel 导入导出自动配置。
 *
 * <p>基于 EasyExcel 注册 Excel 读写相关 Bean：模板工厂、错误收集器、异步导出执行器、监听器注册中心。
 *
 * <p>支持大数据量分页导出、流式读取、错误行收集、模板下拉框等高级特性。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(ExcelTemplate.class)
@ConditionalOnProperty(prefix = "remi.excel", name = "enabled", havingValue = "true", matchIfMissing = true)
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
        if (properties.getUse1904Windowing() != null) {
            config.setUse1904Windowing(properties.getUse1904Windowing());
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

    /**
     * 注册 Excel 健康检查指示器。
     *
     * <p>将全局 {@link ExcelConfig} 注入 {@link ExcelHealthIndicator}，用于暴露 Excel 读写链路的健康状态
     * （如临时目录可用性、配置合法性），供 Actuator 健康端点采集。仅在容器中不存在该类型 Bean 时创建，
     * 允许业务方提供自定义指示器实现以覆盖默认行为。</p>
     *
     * @param config Excel 全局配置，不可为 {@code null}
     * @return Excel 健康检查指示器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelHealthIndicator excelHealthIndicator(ExcelConfig config) {
        return new ExcelHealthIndicator(config);
    }
}
