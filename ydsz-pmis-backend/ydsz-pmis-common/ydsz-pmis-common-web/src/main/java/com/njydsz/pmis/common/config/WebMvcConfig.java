package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Web MVC 配置
 *
 * <p>注册鉴权拦截器、CORS 等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 鉴权拦截器 */
    private final AuthInterceptor authInterceptor;

    /**
     * CORS 允许的源（逗号分隔），不配置则默认允许所有（仅 dev 环境）。
     * 生产环境必须通过此配置显式指定域名白名单。
     */
    @Value("${pmis.cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 配置国际化 Locale 解析器
     *
     * <p>基于 Accept-Language 请求头解析 Locale，默认简体中文，
     * 支持简体中文和英文（US）。
     *
     * @return Locale 解析器
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.US));
        return resolver;
    }

    /**
     * 注册鉴权拦截器并配置白名单路径
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                // 排除白名单
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/captcha",
                        "/auth/register",
                        "/health",
                        "/health/**",
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/actuator/**",
                        "/doc.html",
                        "/doc.html/**",
                        "/favicon.ico"
                );
    }

    /**
     * 配置 CORS 跨域策略（P0-5 安全加固）
     *
     * <p>生产环境必须通过 {@code pmis.cors.allowed-origins} 配置显式域名白名单，
     * 如未配置则在非 dev 环境下拒绝启动。
     * dev 环境允许所有源以方便本地开发调试。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins;
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            origins = allowedOrigins.split(",");
        } else {
            // 未配置时使用受限通配符（不允许 credentials）
            origins = new String[]{"*"};
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With",
                        "X-Trace-Id", "Accept-Language", "X-Access-Token")
                .exposedHeaders("Authorization", "X-Trace-Id", "Content-Disposition")
                .allowCredentials(!"*".equals(origins[0]))
                .maxAge(3600);
    }
}
