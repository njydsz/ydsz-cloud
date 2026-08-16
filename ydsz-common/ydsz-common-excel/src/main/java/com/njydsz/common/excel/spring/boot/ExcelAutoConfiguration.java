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
     * 注册 {@link ExcelConfig} 单例，通过 {@link ExcelProperties#toExcelConfig()} 构建不可变配置。
     *
     * @param properties ydsz.excel.* 配置属性
     * @return 已装配的 {@link ExcelConfig} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelConfig excelConfig(ExcelProperties properties) {
        return properties.toExcelConfig();
    }

    /**
     * 注册 ExcelTemplate 模板类。
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
     * 注入 Micrometer MeterRegistry 到 ExcelMetrics。
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
     * <p>将 {@link ExcelConfig} 注入 {@link ExcelHealthIndicator}，用于暴露 Excel 读写链路的健康状态
     * （如临时目录可用性、配置合法性），供 Actuator 健康端点采集。</p>
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
