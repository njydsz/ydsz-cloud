package com.remisoft.common.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.remisoft.common.exception.endpoint.ExceptionCodeDocEndpoint;
import com.remisoft.common.exception.registry.ResultCodeRegistry;
import com.remisoft.common.exception.registry.ResultCodeScanner;

/**
 * 异常码文档端点配置。
 *
 * <p>暴露 {@code /actuator/exception-codes} 端点，输出全量异常码与文档说明（HTTP code + 业务码 + 描述）。
 *
 * <p>便于前端/测试同学检索错误码、运维同学做异常字典管理。
 *
 * <p><b>安全加固：</b>支持通过 {@code remi.exception.doc-endpoint.filter-modules} 配置模块白名单，
 * 仅暴露指定模块的错误码，避免内部模块信息泄露。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@EnableConfigurationProperties(ExceptionProperties.class)
@ConditionalOnProperty(prefix = "remi.exception", name = "doc-endpoint-enabled", havingValue = "true", matchIfMissing = true)
public class ExceptionCodeDocEndpointAutoConfiguration {

    /**
     * 创建错误码文档端点 Bean
     *
     * @param messageSource 国际化消息源
     * @param properties 异常模块配置属性（不可为 null）
     * @return 错误码文档端点实例
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionCodeDocEndpoint.class)
    public ExceptionCodeDocEndpoint exceptionCodeDocEndpoint(MessageSource messageSource, ExceptionProperties properties) {
        return new ExceptionCodeDocEndpoint(messageSource, properties);
    }

    /**
     * 创建全局错误码注册表 Bean
     *
     * @return ResultCodeRegistry 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ResultCodeRegistry resultCodeRegistry() {
        return new ResultCodeRegistry();
    }

    /**
     * 创建错误码自动扫描注册器 Bean
     *
     * @param registry 全局错误码注册表
     * @return ResultCodeScanner 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ResultCodeScanner resultCodeScanner(ResultCodeRegistry registry) {
        return new ResultCodeScanner(registry);
    }
}
