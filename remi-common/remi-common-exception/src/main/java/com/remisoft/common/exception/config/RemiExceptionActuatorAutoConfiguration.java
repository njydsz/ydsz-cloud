package com.remisoft.common.exception.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.remisoft.common.exception.code.ErrorCodeTable;
import com.remisoft.common.exception.endpoint.ExceptionCodeDocEndpoint;
import com.remisoft.common.exception.health.ExceptionHealthIndicator;
import com.remisoft.common.exception.metrics.ExceptionMetrics;
import com.remisoft.common.exception.registry.ResultCodeRegistry;
import com.remisoft.common.exception.registry.ResultCodeScanner;

/**
 * 异常模块 Actuator / 观测能力自动配置
 *
 * <p>合并了原有的 2 个配置类：
 * <ul>
 *   <li>错误码文档端点：{@code /actuator/exception-codes}</li>
 *   <li>健康检查指示器：{@code exception}</li>
 * </ul>
 *
 * <p>仅在 Spring Boot Actuator 存在于类路径时激活，无 Actuator 依赖时自动跳过。
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration(after = RemiExceptionCoreAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@EnableConfigurationProperties(ExceptionProperties.class)
public class RemiExceptionActuatorAutoConfiguration {

    /**
     * Actuator Endpoint 核心类名条件
     */
    private static final String ACTUATOR_ENDPOINT_CLASS = "org.springframework.boot.actuate.endpoint.annotation.Endpoint";

    private static final String ACTUATOR_HEALTH_CLASS = "org.springframework.boot.actuate.health.HealthIndicator";

    // ==================== 错误码文档端点 ====================

    /**
     * 创建错误码文档端点 Bean
     *
     * <p>暴露 {@code /actuator/exception-codes} 端点，输出全量异常码与文档说明，
     * 便于前端/测试同学检索错误码、运维同学做异常字典管理。
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionCodeDocEndpoint.class)
    @ConditionalOnProperty(prefix = "remi.exception", name = "doc-endpoint-enabled", havingValue = "true", matchIfMissing = true)
    public ExceptionCodeDocEndpoint exceptionCodeDocEndpoint(MessageSource messageSource, ExceptionProperties properties) {
        return new ExceptionCodeDocEndpoint(messageSource, properties);
    }

    /**
     * 创建全局错误码注册表 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ResultCodeRegistry resultCodeRegistry() {
        return new ResultCodeRegistry();
    }

    /**
     * 创建错误码自动扫描注册器 Bean
     *
     * <p>注入 ErrorCodeTable 以同时注册到统一注册表。
     */
    @Bean
    @ConditionalOnMissingBean
    public ResultCodeScanner resultCodeScanner(ResultCodeRegistry registry,
                                               ObjectProvider<ErrorCodeTable> errorCodeTableProvider) {
        ErrorCodeTable table = errorCodeTableProvider.getIfAvailable();
        return new ResultCodeScanner(registry, table);
    }

    // ==================== 健康检查 ====================

    /**
     * 创建异常模块健康指示器 Bean
     *
     * <p>向 Actuator 暴露异常体系的运行状态（异常计数、错误码注册数量等）。
     */
    @Bean
    @ConditionalOnClass(name = ACTUATOR_HEALTH_CLASS)
    @ConditionalOnMissingBean(ExceptionHealthIndicator.class)
    public ExceptionHealthIndicator exceptionHealthIndicator(
            ExceptionProperties properties,
            ObjectProvider<ExceptionMetrics> metricsProvider,
            ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider) {
        return new ExceptionHealthIndicator(properties, metricsProvider,
                resultCodeRegistryProvider);
    }
}
