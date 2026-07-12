package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.app.util.RequestIdGenerator;
import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.auth.model.AuthenticationProvider;
import com.njydsz.pmis.common.auth.filter.BaseAuthFilter;
import com.njydsz.pmis.common.core.constant.HeaderConstants;
import com.njydsz.pmis.common.auth.handler.AuthHandler;
import com.njydsz.pmis.common.util.auth.AuthInfo;
import com.njydsz.pmis.common.util.auth.RequestHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 移动端 App 认证过滤器
 *
 * <p>作为移动端请求的入口过滤器，负责解析请求头中的认证信息并写入 {@link RequestHolder} 上下文。
 *
 * <p>认证策略解耦：
 * <ul>
 *   <li>默认使用注入的 AuthHandler 进行认证</li>
 *   <li>业务方可注入自定义 {@link AuthenticationProvider} 覆盖默认策略</li>
 *   <li>通过 Spring {@code @ConditionalOnBean} 或 SPI 实现可插拔认证</li>
 * </ul>
 *
 * <p>请求追踪：
 * <ul>
 *   <li>自动生成请求追踪 ID（Request ID）</li>
 *   <li>将请求 ID 写入 MDC 日志上下文，便于日志聚合分析</li>
 *   <li>将请求 ID 注入响应头 {@code X-Request-Id}</li>
 * </ul>
 *
 * @see AuthHandler
 * @see AuthInfo
 * @see AuthenticationProvider
 * @see RequestHolder
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class AppAuthFilter extends BaseAuthFilter {

    /** 业务异常：当 AuthHandler 与 AuthenticationProvider 都未配置时抛出 */
    private static final String AUTH_HANDLER_NULL_MESSAGE = "AuthHandler or AuthenticationProvider must be configured";

    /** 请求追踪 ID 请求头名称 */
    private static final String REQUEST_ID_HEADER = HeaderConstants.X_REQUEST_ID;

    /** 认证处理器，负责解析请求头中的认证信息 */
    private final AuthHandler authHandler;
    /** 自定义认证提供者，优先于 AuthHandler 使用 */
    private final AuthenticationProvider authenticationProvider;

    /**
     * 构造方法（兼容版本：仅注入 AuthHandler）
     *
     * @param applicationName        应用名称
     * @param authFilterConfiguration 通用鉴权过滤器配置
     * @param authHandler            认证处理器
     */
    public AppAuthFilter(String applicationName, AuthFilterConfiguration authFilterConfiguration, AuthHandler authHandler) {
        this(applicationName, authFilterConfiguration, authHandler, null);
    }

    /**
     * 构造方法（完整版本）
     *
     * @param applicationName        应用名称
     * @param authFilterConfiguration 通用鉴权过滤器配置
     * @param authHandler            认证处理器
     * @param authenticationProvider 自定义认证提供者（可为空，为空时使用 AuthHandler）
     */
    public AppAuthFilter(String applicationName,
                         AuthFilterConfiguration authFilterConfiguration,
                         AuthHandler authHandler,
                         AuthenticationProvider authenticationProvider) {
        super(applicationName, authFilterConfiguration);
        this.authHandler = authHandler;
        this.authenticationProvider = authenticationProvider;
    }

    /**
     * 鉴权前的预处理
     *
     * <p>生成或复用当前请求的 RequestId，并写入 {@link RequestHolder} 上下文，
     * 供后续过滤器（如 {@link AppRequestLogInterceptor}）使用。
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
     * 若两者均为空则抛出 {@link NullPointerException}。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 解析得到的认证信息
     * @throws NullPointerException 当 AuthHandler 与 AuthenticationProvider 都未配置时抛出
     */
    @Override
    protected AuthInfo resolveAuthInfo(HttpServletRequest request, HttpServletResponse response) {
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
}
