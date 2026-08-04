package com.remisoft.common.base.advice;

import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.json.YdszJson;

/**
 * 全局响应包装基类（Web/App 共享）
 *
 * <p>自动将非 {@link BaseResponse} 类型的返回值包装为 {@link BaseResponse#success(Object)} 格式。
 *
 * <p><b>跳过包装的类型：</b>
 * <ul>
 *   <li>{@link BaseResponse} — 已是标准响应</li>
 *   <li>{@code void} — 无返回值（如文件下载、204 No Content）</li>
 *   <li>{@link ResponseEntity} — Spring MVC 特殊处理，包装会丢失原始状态码和 Header</li>
 *   <li>{@link HttpEntity} — 同 ResponseEntity</li>
 *   <li>{@link Resource} — 文件下载场景</li>
 * </ul>
 *
 * <p>子类覆盖 {@link #wrapStringBody(String)} 处理 String 类型返回值的差异：
 * Web 端调用 {@code BaseResponse.success(msg)}，App 端调用 {@code BaseResponse.successMsg(msg)}。
 *
 * @author remi-team
 * @since 1.0.0
 */
public abstract class BaseGlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> paramType = returnType.getParameterType();
        // 跳过已包装类型、void、ResponseEntity/HttpEntity、Resource
        if (paramType == BaseResponse.class
                || paramType == void.class
                || paramType == Void.class
                || ResponseEntity.class.isAssignableFrom(paramType)
                || HttpEntity.class.isAssignableFrom(paramType)
                || Resource.class.isAssignableFrom(paramType)) {
            return false;
        }
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                             @NonNull MethodParameter returnType,
                                             @NonNull MediaType selectedContentType,
                                             @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                             @NonNull ServerHttpRequest request,
                                             @NonNull ServerHttpResponse response) {
        if (body instanceof BaseResponse) {
            return body;
        }
        if (body instanceof String) {
            // String 返回值默认 Content-Type 为 text/plain，需修正为 application/json
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            BaseResponse<String> result = wrapStringBody((String) body);
            try {
                return YdszJson.toJson(result);
            } catch (Exception e) {
                return result;
            }
        }
        if (body == null) {
            return BaseResponse.success();
        }
        return BaseResponse.success((Serializable) body);
    }

    /**
     * 子类覆盖此方法处理 String 类型返回值的包装差异
     *
     * @param body 原始 String 返回值
     * @return 包装后的 BaseResponse
     */
    protected abstract BaseResponse<String> wrapStringBody(String body);
}
