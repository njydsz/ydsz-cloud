package com.njydsz.common.exception.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.code.ErrorCodeTable;

/**
 * 异常模块 SpringDoc OpenAPI 自动配置。
 *
 * <p>仅在 classpath 中存在 springdoc-openapi 库时加载（{@link ConditionalOnClass}），
 * 自动为所有 OpenAPI 操作注入错误码响应模型。
 *
 * <p><b>关闭方式：</b>
 * <pre>{@code
 * ydsz.exception.openapi.enabled=false
 * }</pre>
 *
 * @author ydsz-team
 * @since 2.4.0
 */
@AutoConfiguration(after = com.njydsz.common.exception.config.YdszExceptionCoreAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.springdoc.core.customizers.OpenApiCustomizer",
        "io.swagger.v3.oas.models.OpenAPI"
})
@ConditionalOnProperty(prefix = "ydsz.exception", name = "openapi.enabled", havingValue = "true", matchIfMissing = true)
public class YdszExceptionOpenApiAutoConfiguration {

    /**
     * 注册错误码文档增强器
     *
     * @param errorCodeTable 错误码注册表
     * @param messageSource  国际化消息源
     * @return OpenApiCustomizer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenApiCustomizer exceptionCodeOpenApiCustomizer(
            ErrorCodeTable errorCodeTable, MessageSource messageSource) {
        return new ExceptionCodeOpenApiCustomizer(errorCodeTable, messageSource);
    }
}
