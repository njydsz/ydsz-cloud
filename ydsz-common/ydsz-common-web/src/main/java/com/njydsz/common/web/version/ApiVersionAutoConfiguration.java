package com.njydsz.common.web.version;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API 版本路由自动配置
 *
 * <p>通过 {@link WebMvcRegistrations} 机制替换默认的 {@link RequestMappingHandlerMapping}，
 * 使用自定义的 {@link ApiVersionRequestMappingHandlerMapping} 支持基于版本的接口路由。
 *
 * <p><b>P2-17 (1.2.0)</b>：添加启动时校验 Bean，在所有的单例 Bean 创建完成后扫描并校验
 * 所有已注册的 {@code @ApiVersion} 注解，发现违规时抛出 {@link ApiVersionViolationException} 阻止启动。
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   web:
 *     api-version:
 *       enabled: true
 *       default-version: "1.0"
 *       strategy: URL  # URL / HEADER / ACCEPT
 *       validate: true  # 启动时校验 @ApiVersion 注解合法性
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ApiVersionRequestMappingHandlerMapping
 * @see ApiVersionProperties
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ydsz.web.api-version", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiVersionProperties.class)
public class ApiVersionAutoConfiguration implements WebMvcRegistrations {

    private final ApiVersionProperties properties;

    public ApiVersionAutoConfiguration(ApiVersionProperties properties) {
        this.properties = properties;
    }

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new ApiVersionRequestMappingHandlerMapping(properties);
    }

    /**
     * 注册启动时 API 版本校验器。
     *
     * <p>使用 {@link SmartInitializingSingleton} 在所有单例 Bean 创建完成后执行校验，
     * 确保所有的 Controller 都已注册到 HandlerMapping 中。
     *
     * @param handlerMapping Spring MVC 请求映射处理器
     * @return 启动时校验器
     * @since 1.2.0
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.api.version", name = "validate", havingValue = "true", matchIfMissing = true)
    public SmartInitializingSingleton apiVersionValidator(RequestMappingHandlerMapping handlerMapping) {
        return () -> ApiVersionChecker.validate(handlerMapping, properties);
    }
}
