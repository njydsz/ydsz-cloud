package com.njydsz.common.excel.spring.boot;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metrics.ExcelMetrics;
import com.njydsz.common.excel.spring.ExcelTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Excel 导入导出自动配置。
 *
 * <p>基于 EasyExcel 注册 Excel 读写相关 Bean：模板工厂、错误收集器、异步导出执行器、监听器注册中心。
 *
 * <p>支持大数据量分页导出、流式读取、错误行收集、模板下拉框等高级特性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(ExcelTemplate.class)
@ConditionalOnProperty(prefix = "ydsz.excel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExcelProperties.class)
public class ExcelAutoConfiguration {

    /**
     * 注册 {@link ExcelConfig} 单例，并应用 {@link ExcelProperties} 中的配置覆盖。
     *
     * <p>装配流程：
     * <ol>
     *   <li>以 {@link ExcelConfig#builder()} 构造 Builder，此时所有字段为 ExcelConfig 默认值；</li>
     *   <li>对 {@link ExcelProperties} 中每个非 null 属性，通过链式调用覆盖 Builder 字段；</li>
     *   <li>调用 {@link ExcelConfig.Builder#build()} 构建{@code ExcelConfig} 实例，
     *       该方法内部复用 {@link ExcelConfig#setReadBufferSize(int)} 等同步 setter，
     *       参数校验、volatile 写入的 happens-before 保证与原路径完全等价；</li>
     *   <li>通过 {@link ExcelConfig#setInstance} 注入全局单例。</li>
     * </ol>
     *
     * <p>Builder 用法把"装配阶段一次性赋值"语义显式化，避免启动后无意间改动字段；
     * 默认compressionLevel 已在 Builder 初始化阶段使用 {@link Deflater#BEST_SPEED}，
     * 与旧逻辑一致。
     *
     * @param properties ydsz.excel.* 配置属性
     * @return 已装配单例的 {@link ExcelConfig} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelConfig excelConfig(ExcelProperties properties) {
        ExcelConfig.Builder builder = ExcelConfig.builder();

        if (properties.getReadBufferSize() != null) {
            builder.readBufferSize(properties.getReadBufferSize());
        }
        if (properties.getWriteBufferSize() != null) {
            builder.writeBufferSize(properties.getWriteBufferSize());
        }
        if (properties.getDefaultDateFormat() != null) {
            builder.defaultDateFormat(properties.getDefaultDateFormat());
        }
        if (properties.getDefaultNumberFormat() != null) {
            builder.defaultNumberFormat(properties.getDefaultNumberFormat());
        }
        if (properties.getAutomaticTrim() != null) {
            builder.automaticTrim(properties.getAutomaticTrim());
        }
        if (properties.getMaxReadCacheSize() != null) {
            builder.maxReadCacheSize(properties.getMaxReadCacheSize());
        }
        if (properties.getUseFastReader() != null) {
            builder.useFastReader(properties.getUseFastReader());
        }
        if (properties.getUseFastWriter() != null) {
            builder.useFastWriter(properties.getUseFastWriter());
        }
        if (properties.getStreamingParseThresholdMB() != null) {
            builder.streamingParseThresholdMB(properties.getStreamingParseThresholdMB());
        }
        if (properties.getMaxReadFileSizeMB() != null) {
            builder.maxReadFileSizeMB(properties.getMaxReadFileSizeMB());
        }
        if (properties.getMaxWriteFileSizeMB() != null) {
            builder.maxWriteFileSizeMB(properties.getMaxWriteFileSizeMB());
        }
        if (properties.getCompressionLevel() != null) {
            builder.compressionLevel(properties.getCompressionLevel());
        }
        if (properties.getFormulaInjectionProtection() != null) {
            builder.formulaInjectionProtection(properties.getFormulaInjectionProtection());
        }
        if (properties.getStrictNumberConversion() != null) {
            builder.strictNumberConversion(properties.getStrictNumberConversion());
        }
        if (properties.getUse1904Windowing() != null) {
            builder.use1904Windowing(properties.getUse1904Windowing());
        }
        if (properties.getHeadRowNumber() != null) {
            builder.headRowNumber(properties.getHeadRowNumber());
        }
        if (properties.getWriteCacheSize() != null) {
            builder.writeCacheSize(properties.getWriteCacheSize());
        }
        if (properties.getValidationMode() != null) {
            builder.validationMode(properties.getValidationMode());
        }

        ExcelConfig config = builder.build();
        ExcelConfig.setInstance(config);
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
