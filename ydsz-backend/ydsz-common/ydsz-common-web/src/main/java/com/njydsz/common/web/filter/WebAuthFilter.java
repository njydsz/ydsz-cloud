package com.njydsz.common.web.filter;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.njydsz.common.auth.config.AuthFilterConfiguration;
import com.njydsz.common.auth.filter.BaseAuthFilter;
import com.njydsz.common.auth.handler.AuthHandler;
import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.domain.enums.ServiceType;
import com.njydsz.common.util.auth.AuthInfo;
import com.njydsz.common.util.auth.RequestHolder;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;
import com.njydsz.common.web.auth.AuthHandlerFactory;
import com.njydsz.common.web.metrics.WebMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Web 端认证过滤器
 *
 * <p>核心职责：
 * <ul>
 *   <li>解析请求头中的认证信息（Token、用户ID、租户ID、数据权限维度等）</li>
 *   <li>将认证上下文写入 {@link RequestHolder}，供下游链路使用</li>
 *   <li>支持请求路径白名单过滤，无需认证即可访问</li>
 *   <li>认证成功/失败埋点到 {@link WebMetrics}（可选依赖）</li>
 * </ul>
 *
 * <p>认证策略解耦：
 * <ul>
 *   <li>默认使用 {@link AuthHandlerFactory} 获取认证处理器</li>
 *   <li>业务方可注入自定义 {@link AuthenticationProvider} 覆盖默认策略</li>
 *   <li>通过 Spring {@code @ConditionalOnBean} 或 SPI 实现可插拔认证</li>
 * </ul>
 *
 * <p>过滤器优先级设置为 3，在 CORS 过滤器（优先级 0）之后执行。
 * 确保跨域预检请求可以正常通过，而认证逻辑在跨域处理之后执行。
 *
 * @author ydsz-team
 * @see AuthHandlerFactory
 * @see AuthenticationProvider
 * @see RequestHolder
 * @see WebMetrics
 * @since 1.0.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class WebAuthFilter extends BaseAuthFilter {

    private final AuthHandlerFactory authHandlerFactory;
    private final AuthenticationProvider authenticationProvider;
    private final WebMetrics webMetrics;

    public WebAuthFilter(String applicationName,
                         AuthFilterConfiguration authFilterConfiguration,
                         AuthHandlerFactory authHandlerFactory,
                         AuthenticationProvider authenticationProvider,
                         WebMetrics webMetrics) {
        super(applicationName, authFilterConfiguration);
        this.authHandlerFactory = authHandlerFactory;
        this.authenticationProvider = authenticationProvider;
        this.webMetrics = webMetrics;
    }

    @Override
    protected void doPreAuth(HttpServletRequest request, HttpServletResponse response) {
        TracerUtils.getOrCreateTraceId();
    }

    @Override
    protected AuthInfo resolveAuthInfo(HttpServletRequest request, HttpServletResponse response) {
        long startNanos = System.nanoTime();
        try {
            AuthInfo authInfo = doResolveAuthInfo(request, response);
            if (webMetrics != null) {
                webMetrics.recordAuthSuccess(System.nanoTime() - startNanos);
            }
            return authInfo;
        } catch (RuntimeException e) {
            if (webMetrics != null) {
                webMetrics.recordAuthFailure(System.nanoTime() - startNanos);
            }
            throw e;
        }
    }

    private AuthInfo doResolveAuthInfo(HttpServletRequest request, HttpServletResponse response) {
        if (authenticationProvider != null) {
            return authenticationProvider.authenticate(request, response);
        }

        Objects.requireNonNull(authHandlerFactory, "AuthHandlerFactory or AuthenticationProvider must be configured");
        String serviceType = request.getHeader(HeaderConstants.X_SERVICE_TYPE);
        log.debug("X_SERVICE_TYPE: {}", serviceType);

        AuthHandler authHandler = authHandlerFactory.getAuthHandler(resolveServiceType(serviceType));
        return authHandler.getAuthInfo(request, response);
    }

    @Override
    protected boolean shouldSkipService() {
        return isServiceIgnored(applicationName);
    }

    @Override
    protected void doPostAuth(HttpServletRequest request, HttpServletResponse response, long duration) {
        log.debug("{}认证耗时: {}ms", getLogPrefix(), duration);
    }

    @Override
    protected String getLogPrefix() {
        return "【Web端】";
    }

    private ServiceType resolveServiceType(String serviceType) {
        if (StringUtils.isEmpty(serviceType)) {
            return ServiceType.WEB_SERVICE;
        }
        try {
            return ServiceType.codeOf(serviceType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的 X-Service-Type: " + serviceType, e);
        }
    }
}
