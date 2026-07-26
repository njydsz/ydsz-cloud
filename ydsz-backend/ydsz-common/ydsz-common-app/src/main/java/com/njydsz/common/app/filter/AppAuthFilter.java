package com.njydsz.common.app.filter;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.app.util.RequestIdGenerator;
import com.njydsz.common.auth.config.AuthFilterConfiguration;
import com.njydsz.common.auth.filter.BaseAuthFilter;
import com.njydsz.common.auth.handler.AuthHandler;
import com.njydsz.common.auth.metrics.AuthMetrics;
import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.util.auth.AuthInfo;
import com.njydsz.common.util.auth.RequestHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * 移动端 App 认证过滤器
 *
 * <p>作为移动端请求的入口过滤器，负责解析请求头中的认证信息并写入 {@link RequestHolder} 上下文。
 *
 * <p>认证策略解耦：
 * <ul>
 *   <li>默认使用注入的 AuthHandler 进行认证</li>
 *   <li>业务方可注入自定义 {@link AuthenticationProvider} 覆盖默认策略</li>
 * </ul>
 *
 * <p>认证埋点：通过 {@link AuthMetrics} 接口（默认由 {@code AppMetrics} 实现）
 * 上报认证成功/失败耗时指标到 Micrometer，便于监控 App 端认证质量。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AuthHandler
 * @see AuthInfo
 * @see AuthenticationProvider
 * @see AuthMetrics
 * @see RequestHolder
 */
@Slf4j
public class AppAuthFilter extends BaseAuthFilter {

    /** 业务异常：当 AuthHandler 与 AuthenticationProvider 都未配置时抛出 */
    private static final String AUTH_HANDLER_NULL_MESSAGE = "AuthHandler or AuthenticationProvider must be configured";

    /** 请求追踪 ID 请求头名称 */
    private static final String REQUEST_ID_HEADER = HeaderConstants.X_REQUEST_ID;

    /** App 端用户类型标识，作为指标 userType 标签值 */
    private static final String USER_TYPE_APP = "app";

    /** 认证处理器，负责解析请求头中的认证信息 */
    private final AuthHandler authHandler;
    /** 自定义认证提供者，优先于 AuthHandler 使用 */
    private final AuthenticationProvider authenticationProvider;
    /** 认证指标采集器（可选依赖，为 null 时不采集指标） */
    private final AuthMetrics authMetrics;

    /**
     * 构造方法
     *
     * @param applicationName        应用名称
     * @param authFilterConfiguration 通用鉴权过滤器配置
     * @param authHandler            认证处理器
     * @param authenticationProvider 自定义认证提供者（可为空，为空时使用 AuthHandler）
     * @param authMetrics            认证指标采集器（可为空，为空时不采集指标）
     */
    public AppAuthFilter(String applicationName,
                         AuthFilterConfiguration authFilterConfiguration,
                         AuthHandler authHandler,
                         AuthenticationProvider authenticationProvider,
                         AuthMetrics authMetrics) {
        super(applicationName, authFilterConfiguration);
        this.authHandler = authHandler;
        this.authenticationProvider = authenticationProvider;
        this.authMetrics = authMetrics;
    }

    /**
     * 鉴权前的预处理
     *
     * <p>生成或复用当前请求的 RequestId，并写入 {@link RequestHolder} 上下文。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     */
    @Override
    protected void doPreAuth(HttpServletRequest request, HttpServletResponse response) {
        String requestId = generateOrGetRequestId(request);
        RequestHolder.putExtraHeader(REQUEST_ID_HEADER, requestId);
    }

    /**
     * 解析当前请求的认证信息
     *
     * <p>优先使用 {@link AuthenticationProvider}，为空时降级到 {@link AuthHandler}。
     * 认证成功/失败均通过 {@link AuthMetrics} 上报耗时指标。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 解析得到的认证信息
     * @throws NullPointerException 当 AuthHandler 与 AuthenticationProvider 都未配置时抛出
     */
    @Override
    protected AuthInfo resolveAuthInfo(HttpServletRequest request, HttpServletResponse response) {
        long startNanos = System.nanoTime();
        try {
            AuthInfo authInfo = doResolveAuthInfo(request, response);
            if (authMetrics != null) {
                authMetrics.recordAuthSuccess(USER_TYPE_APP, System.nanoTime() - startNanos);
            }
            return authInfo;
        } catch (RuntimeException e) {
            if (authMetrics != null) {
                authMetrics.recordAuthFailure(USER_TYPE_APP, resolveFailureReason(e),
                        System.nanoTime() - startNanos);
            }
            throw e;
        }
    }

    /**
     * 实际的认证信息解析逻辑
     */
    private AuthInfo doResolveAuthInfo(HttpServletRequest request, HttpServletResponse response) {
        if (authenticationProvider != null) {
            return authenticationProvider.authenticate(request, response);
        }
        Objects.requireNonNull(authHandler, AUTH_HANDLER_NULL_MESSAGE);
        return authHandler.getAuthInfo(request, response);
    }

    /**
     * 是否跳过当前应用的服务调用
     *
     * @return true 表示当前请求应跳过服务调用逻辑，false 表示正常处理
     */
    @Override
    protected boolean shouldSkipService() {
        return isServiceIgnored(applicationName);
    }

    /**
     * 返回 App 端日志前缀
     *
     * @return 固定返回 {@code "【App端】"}
     */
    @Override
    protected String getLogPrefix() {
        return "【App端】";
    }

    /**
     * 生成或获取请求追踪 ID
     *
     * <p>优先从请求头中获取已有的 RequestId，若不存在则通过雪花算法生成新的 ID。
     *
     * @param request 当前 HTTP 请求
     * @return 请求追踪 ID
     */
    private String generateOrGetRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdGenerator.generateId();
        }
        return requestId;
    }

    /**
     * 从异常类型推断认证失败原因标签
     *
     * <p>用于指标 {@code reason} 标签的取值规范化，便于按原因聚合告警。
     *
     * @param e 认证抛出的异常
     * @return 失败原因标签（missing_token/invalid_token/expired_token/unknown 等）
     */
    private static String resolveFailureReason(RuntimeException e) {
        String className = e.getClass().getSimpleName();
        if (className.contains("MissingToken") || className.contains("MissingHeader")) {
            return "missing_token";
        }
        if (className.contains("InvalidToken") || className.contains("BadCredentials")) {
            return "invalid_token";
        }
        if (className.contains("Expired") || className.contains("TokenExpired")) {
            return "expired_token";
        }
        if (className.contains("Revoked") || className.contains("Blacklist")) {
            return "revoked_token";
        }
        if (className.contains("Signature")) {
            return "signature_mismatch";
        }
        return "unknown";
    }
}
