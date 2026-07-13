package com.njydsz.pmis.common.socket.auth;

import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.token.TokenService;
import com.njydsz.pmis.common.socket.constant.WebSocketConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * WebSocket 握手鉴权拦截器（通用版）。
 *
 * <p>从握手请求中提取 JWT token（优先查询参数 {@code token}，回退请求头
 * {@code Authorization: Bearer xxx}），通过 {@link TokenService} 校验合法性，
 * 并将 {@code userId} / {@code username} 写入握手属性，供后续 SessionListener 使用。
 *
 * <p>token 缺失 / 无效 / 过期时拒绝握手（返回 401），避免未认证连接消耗资源。
 *
 * <p>与旧版（message-server 中的 {@code WebSocketAuthHandshakeInterceptor}）不同，
 * 本类依赖 {@link TokenService} 接口而非具体的 {@code JwtTokenProvider}，
 * 可适配任何 TokenService 实现，解耦更彻底。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final TokenService tokenService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("[WS-Auth] 握手拒绝: 缺少 token, remote={}", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        UserInfo userInfo = tokenService.parseAccessToken(token);
        if (userInfo == null || !StringUtils.hasText(userInfo.getUserId())) {
            log.warn("[WS-Auth] 握手拒绝: token 无效或过期, remote={}", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(WebSocketConstants.WS_ATTR_USER_ID, userInfo.getUserId());
        attributes.put(WebSocketConstants.WS_ATTR_USERNAME, userInfo.getUsername());
        log.info("[WS-Auth] 握手成功: userId={}, username={}", userInfo.getUserId(), userInfo.getUsername());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后无需处理
    }

    /**
     * 从请求中提取 JWT token：优先查询参数 {@code token}，回退请求头 {@code Authorization}。
     *
     * @param request HTTP 请求
     * @return token 字符串，无则返回 null
     */
    private String extractToken(ServerHttpRequest request) {
        // ① 优先从查询参数提取（WebSocket 浏览器不支持自定义请求头）
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String param = servletRequest.getServletRequest().getParameter(WebSocketConstants.WS_TOKEN_PARAM);
            if (StringUtils.hasText(param)) {
                return param.trim();
            }
        }
        // ② 回退到 Authorization 请求头（Bearer xxx）
        String header = request.getHeaders().getFirst(WebSocketConstants.WS_TOKEN_HEADER);
        if (StringUtils.hasText(header)) {
            String trimmed = header.trim();
            if (trimmed.startsWith("Bearer ")) {
                return trimmed.substring("Bearer ".length()).trim();
            }
            return trimmed;
        }
        return null;
    }
}
