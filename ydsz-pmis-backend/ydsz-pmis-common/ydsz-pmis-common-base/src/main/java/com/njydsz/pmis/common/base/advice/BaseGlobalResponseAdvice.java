package com.njydsz.pmis.common.base.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.util.JsonUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装基类（Web/App 共享）
 *
 * <p>自动将非 {@link Result} 类型的返回值包装为 {@link Result#ok(Object)} 格式。
 *
 * <p>子类覆盖 {@link #wrapStringBody(String)} 处理 String 类型返回值的差异。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseGlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * JSON 序列化器（可选注入）
     */
    private final ObjectMapper objectMapper;

    /**
     * 默认构造器
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
        return !returnType.getParameterType().equals(Result.class);
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                             @NonNull MethodParameter returnType,
                                             @NonNull MediaType selectedContentType,
                                             @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                             @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {
        if (body instanceof Result) {
            return body;
        }
        if (body instanceof String) {
            Result<String> result = wrapStringBody((String) body);
            try {
                ObjectMapper mapper = objectMapper != null ? objectMapper : JsonUtils.getObjectMapper();
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                return result;
            }
        }
        return Result.ok(body);
    }

    /**
     * 子类覆盖此方法处理 String 类型返回值的包装差异
     *
     * @param body 字符串返回值
     * @return 包装后的 Result
     */
    protected abstract Result<String> wrapStringBody(String body);
}
