package com.njydsz.common.exception.config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.endpoint.ExceptionCodeDocEndpoint;
import com.njydsz.common.exception.health.ExceptionHealthIndicator;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

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
 * 错误码扫描注册（{@code ExceptionCodeScanner}）由核心装配 {@link YdszExceptionCoreAutoConfiguration}
 * 负责，与 Actuator 解耦，保证无 Actuator 依赖时错误码仍能正常注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration(after = YdszExceptionCoreAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@EnableConfigurationProperties(ExceptionProperties.class)
public class YdszExceptionActuatorAutoConfiguration {

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
     * @param messageSource 国际化消息源
     * @param properties 异常模块配置属性
     * @param errorCodeTable errorCodeTable 参数说明
     * @return 处理结果
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionCodeDocEndpoint.class)
    @ConditionalOnProperty(prefix = "ydsz.exception", name = "doc-endpoint-enabled", havingValue = "true", matchIfMissing = true)
    public ExceptionCodeDocEndpoint exceptionCodeDocEndpoint(MessageSource messageSource,
                                                              ExceptionProperties properties,
                                                              ErrorCodeTable errorCodeTable) {
        return new ExceptionCodeDocEndpoint(messageSource, properties, errorCodeTable);
    }

    // ==================== 健康检查 ====================

    /**
     * 创建异常模块健康指示器 Bean
     *
     * <p>向 Actuator 暴露异常体系的运行状态（异常计数、错误码注册数量等）。
     * @param properties 异常模块配置属性
     * @param metricsProvider metricsProvider 参数说明
     * @param errorCodeTableProvider errorCodeTableProvider 参数说明
     * @return 处理结果
     */
    @Bean
    @ConditionalOnClass(name = ACTUATOR_HEALTH_CLASS)
    @ConditionalOnMissingBean(ExceptionHealthIndicator.class)
    public ExceptionHealthIndicator exceptionHealthIndicator(
            ExceptionProperties properties,
            ObjectProvider<ExceptionMetrics> metricsProvider,
            ObjectProvider<ErrorCodeTable> errorCodeTableProvider) {
        return new ExceptionHealthIndicator(properties, metricsProvider,
                errorCodeTableProvider);
    }
}
