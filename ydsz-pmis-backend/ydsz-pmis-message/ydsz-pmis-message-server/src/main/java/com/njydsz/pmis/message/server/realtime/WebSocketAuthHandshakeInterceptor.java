package com.njydsz.pmis.message.server.realtime;

import com.njydsz.pmis.common.auth.token.JwtTokenProvider;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * P0-4: WebSocket 握手鉴权拦截器�? *
 * <p>从握手请求中提取 JWT token（优先查询参�?{@code token}，回退请求�? * {@code Authorization: Bearer xxx}），通过 {@link JwtTokenProvider} 校验合法性，
 * 并将 {@code userId} / {@code username} 写入握手属性，供后�?SessionListener 使用�? *
 * <p>token 缺失 / 无效 / 过期时拒绝握手（返回 401），避免未认证连接消耗资源�? *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 握手前校�?JWT token：提取并校验 token，写�?userId / username 到属性�?     *
     * @param request    HTTP 握手请求
     * @param response   HTTP 握手响应
     * @param wsHandler  WebSocket 处理�?     * @param attributes 握手属性（会传递到 WebSocketSession�?     * @return true 允许握手；false 拒绝
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("[WS-Auth] 握手拒绝: 缺少 token, remote={}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("[WS-Auth] 握手拒绝: token 无效或过�? remote={}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            String userId = jwtTokenProvider.getUserId(token);
            String username = jwtTokenProvider.getUsername(token);
            attributes.put(MessageConstants.WS_ATTR_USER_ID, userId);
            attributes.put(MessageConstants.WS_ATTR_USERNAME, username);
            log.info("[WS-Auth] 握手成功: userId={}, username={}", userId, username);
            return true;
        } catch (Exception e) {
            log.warn("[WS-Auth] 握手拒绝: 解析 token 失败: {}", e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后无需处理
    }

    /**
     * 从请求中提取 JWT token：优先查询参�?{@code token}，回退请求�?{@code Authorization}�?     *
     * @param request HTTP 请求
     * @return token 字符串，无则返回 null
     */
    private String extractToken(ServerHttpRequest request) {
        // �?优先从查询参数提取（WebSocket 浏览器不支持自定义请求头�?        if (request instanceof ServletServerHttpRequest servletRequest) {
            String param = servletRequest.getServletRequest().getParameter(MessageConstants.WS_TOKEN_PARAM);
            if (StringUtils.hasText(param)) {
                return param.trim();
            }
        }
        // �?回退�?Authorization 请求头（Bearer xxx�?        String header = request.getHeaders().getFirst(MessageConstants.WS_TOKEN_HEADER);
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
