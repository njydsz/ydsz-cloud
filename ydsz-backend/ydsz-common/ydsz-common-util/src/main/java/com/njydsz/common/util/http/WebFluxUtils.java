package com.njydsz.common.util.http;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.njydsz.common.core.constant.TokenConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.string.StringUtils;

import reactor.core.publisher.Mono;

/**
 * WebFlux 环境下的 HTTP 工具类
 *
 * <p>提供 Spring WebFlux 响应式编程模型下的常用工具方法，
 * 仅在 classpath 中存在 WebFlux 依赖时可用。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public final class WebFluxUtils {

    private WebFluxUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取 WebFlux 请求中的 Token
     *
     * @param request WebFlux 请求
     * @return Token 值，不含前缀
     */
    public static String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(TokenConstants.SUPPLY_AUTHORIZATION);
        if (StringUtils.isEmpty(token)) {
            token = request.getHeaders().getFirst(TokenConstants.AUTHENTICATION);
        }
        return replaceTokenPrefix(token);
    }

    /**
     * WebFlux 响应成功
     *
     * @param response WebFlux 响应
     * @param data     响应数据
     * @return Mono 完成信号
     */
    public static Mono<Void> successResponse(ServerHttpResponse response, Object data) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        BaseResponse<Serializable> result = BaseResponse.success(toSerializable(data));
        DataBuffer dataBuffer = response.bufferFactory()
                .wrap(YdszJson.toJson(result).getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(dataBuffer));
    }

    /**
     * WebFlux 响应失败处理
     *
     * @param response WebFlux 响应
     * @param msg      错误消息
     * @param code     错误码
     * @return Mono 完成信号
     */
    public static Mono<Void> errorResponse(ServerHttpResponse response, String msg, int code) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        BaseResponse<Serializable> result = BaseResponse.error(String.valueOf(code), msg);
        DataBuffer dataBuffer = response.bufferFactory()
                .wrap(YdszJson.toJson(result).getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(dataBuffer));
    }

    private static Serializable toSerializable(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Serializable) {
            return (Serializable) data;
        }
        return YdszJson.toJson(data);
    }

    private static String replaceTokenPrefix(String token) {
        if (StringUtils.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
            return token.replaceFirst(TokenConstants.PREFIX, "").trim();
        }
        return token;
    }
}
