package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
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
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/captcha",
                        "/api/v1/auth/register",
                        "/api/v1/health",
                        "/api/v1/health/**",
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/actuator/**"
                );
    }

    /**
     * 配置 CORS 跨域策略
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "X-Trace-Id", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
