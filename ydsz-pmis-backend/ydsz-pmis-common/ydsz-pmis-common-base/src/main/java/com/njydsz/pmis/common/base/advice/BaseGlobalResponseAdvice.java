package com.njydsz.pmis.common.base.advice;

import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.json.YdszJson;

/**
 * 全局响应包装基类（Web/App 共享）
 *
 * <p>自动将非 {@link BaseResponse} 类型的返回值包装为 {@link BaseResponse#success(Object)} 格式。
 *
 * <p>子类覆盖 {@link #wrapStringBody(String)} 处理 String 类型返回值的差异：
 * Web 端调用 {@code BaseResponse.success(msg)}，App 端调用 {@code BaseResponse.successMsg(msg)}。
 *
 * <p><b>优化说明：</b>
 * <p>支持通过构造器注入自定义 ObjectMapper，提升可测试性和灵活性。
 * 若未注入，则使用 YdszJson 的默认 ObjectMapper。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public abstract class BaseGlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * JSON 序列化器（可选注入）
     */
    private final ObjectMapper objectMapper;

    /**
     * 默认构造器（使用 YdszJson 的默认 ObjectMapper）
     */
    protected BaseGlobalResponseAdvice() {
        this.objectMapper = null;
    }

    /**
     * 构造器注入自定义 ObjectMapper
     *
     * @param objectMapper JSON 序列化器
     */
    protected BaseGlobalResponseAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return !returnType.getParameterType().equals(BaseResponse.class);
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                             @NonNull MethodParameter returnType,
                                             @NonNull MediaType selectedContentType,
                                             @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                             @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {
        if (body instanceof BaseResponse) {
            return body;
        }
        if (body instanceof String) {
            BaseResponse<String> result = wrapStringBody((String) body);
            try {
                ObjectMapper mapper = objectMapper != null ? objectMapper : YdszJson.getMapper();
                return YdszJson.toJson(result);
            } catch (Exception e) {
                return result;
            }
        }
        return BaseResponse.success((Serializable) body);
    }

    /**
     * 子类覆盖此方法处理 String 类型返回值的包装差异
     */
    protected abstract BaseResponse<String> wrapStringBody(String body);
}
