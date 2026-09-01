package com.njydsz.nextwiki.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.njydsz.common.web.interceptor.ApiVersionInterceptor;

/**
 * NextWiki Web MVC 配置。
 *
 * <p>注册 API 版本拦截器，自动将 {@code X-Api-Version} 等版本相关响应头写入所有接口响应。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
public class NextwikiWebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new ApiVersionInterceptor()).addPathPatterns("/api/**");
  }
}
