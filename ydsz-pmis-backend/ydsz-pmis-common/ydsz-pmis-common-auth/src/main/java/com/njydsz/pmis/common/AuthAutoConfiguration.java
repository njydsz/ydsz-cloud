package com.njydsz.pmis.common;

import com.njydsz.pmis.common.aspect.DataScopeAspect;
import com.njydsz.pmis.common.aspect.PermissionAspect;
import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import com.njydsz.pmis.common.permission.PermissionCodeValidator;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 认证授权层自动配置
 *
 * <p>聚合 auth 模块所有组件，通过 Spring Boot 3 自动装配机制注册。
 *
 * <p>包含：
 * <ul>
 *   <li>鉴权拦截器 {@link AuthInterceptor}</li>
 *   <li>JWT 令牌签发/解析 {@link JwtTokenProvider}</li>
 *   <li>数据权限切面 {@link DataScopeAspect}</li>
 *   <li>权限校验切面 {@link PermissionAspect}</li>
 *   <li>权限校验器 {@link PermissionCodeValidator}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
    AuthInterceptor.class,
    JwtTokenProvider.class,
    DataScopeAspect.class,
    PermissionAspect.class,
    PermissionCodeValidator.class
})
public class AuthAutoConfiguration {
}
