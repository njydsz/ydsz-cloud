package com.njydsz.common.web.config;

import jakarta.servlet.MultipartConfigElement;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.util.unit.DataSize;

/**
 * Web 端 Multipart 自动配置
 *
 * <p>覆盖 Spring Boot 默认的 {@link MultipartAutoConfiguration}，提供更合理的默认值：
 * <ul>
 *   <li>{@code max-file-size}：1MB → <b>50MB</b>（适配企业级业务场景）</li>
 *   <li>{@code max-request-size}：10MB → <b>100MB</b></li>
 * </ul>
 *
 * <p><b>覆盖关系：</b>
 * <ul>
 *   <li>本配置在 {@link MultipartAutoConfiguration} 之前生效（{@link AutoConfigureBefore}）；</li>
 *   <li>使用 {@code @ConditionalOnMissingBean(MultipartConfigElement.class)} 避免覆盖用户自定义；</li>
 *   <li>用户可通过 {@code ydsz.web.multipart.enabled=false} 显式禁用，回退到 Spring Boot 默认。</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see WebMultipartProperties
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ MultipartConfigElement.class, MultipartConfigFactory.class })
@ConditionalOnProperty(prefix = "ydsz.web.multipart", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(MultipartAutoConfiguration.class)
@EnableConfigurationProperties(WebMultipartProperties.class)
public class WebMultipartAutoConfiguration {

    /**
     * 注册 MultipartConfigElement，覆盖 Spring Boot 默认的 1MB / 10MB 限制。
     *
     * <p>使用 {@link MultipartConfigFactory} 创建，便于通过 {@link DataSize} 设置大小。
     *
     * @param properties multipart 配置属性
     * @return MultipartConfigElement 实例
     */
    @Bean
    @ConditionalOnMissingBean(MultipartConfigElement.class)
    public MultipartConfigElement multipartConfigElement(WebMultipartProperties properties) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(properties.getMaxFileSize());
        factory.setMaxRequestSize(properties.getMaxRequestSize());
        factory.setFileSizeThreshold(properties.getFileSizeThreshold());
        if (properties.getLocation() != null && !properties.getLocation().isEmpty()) {
            factory.setLocation(properties.getLocation());
        }
        return factory.createMultipartConfig();
    }
}
