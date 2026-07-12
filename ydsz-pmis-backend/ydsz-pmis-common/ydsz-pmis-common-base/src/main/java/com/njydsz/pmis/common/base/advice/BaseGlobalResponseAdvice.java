package com.njydsz.pmis.common.base.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.core.response.BaseResponse;

import java.io.Serializable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 鍏ㄥ眬鍝嶅簲鍖呰鍩虹被锛圵eb/App 鍏变韩锛?
 *
 * <p>鑷姩灏嗛潪 {@link BaseResponse} 绫诲瀷鐨勮繑鍥炲€煎寘瑁呬负 {@link BaseResponse#success(Object)} 鏍煎紡銆?
 *
 * <p>瀛愮被瑕嗙洊 {@link #wrapStringBody(String)} 澶勭悊 String 绫诲瀷杩斿洖鍊肩殑宸紓锛?
 * Web 绔皟鐢?{@code BaseResponse.success(msg)}锛孉pp 绔皟鐢?{@code BaseResponse.successMsg(msg)}銆?
 *
 * <p><b>浼樺寲璇存槑锛?/b>
 * <p>鏀寔閫氳繃鏋勯€犲櫒娉ㄥ叆鑷畾涔?ObjectMapper锛屾彁鍗囧彲娴嬭瘯鎬у拰鐏垫椿鎬с€?
 * 鑻ユ湭娉ㄥ叆锛屽垯浣跨敤 JsonUtils 鐨勯粯璁?ObjectMapper銆?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public abstract class BaseGlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * JSON 搴忓垪鍖栧櫒锛堝彲閫夋敞鍏ワ級
     */
    private final ObjectMapper objectMapper;

    /**
     * 榛樿鏋勯€犲櫒锛堜娇鐢?JsonUtils 鐨勯粯璁?ObjectMapper锛?
     */
    protected BaseGlobalResponseAdvice() {
        this.objectMapper = null;
    }

    /**
     * 鏋勯€犲櫒娉ㄥ叆鑷畾涔?ObjectMapper
     *
     * @param objectMapper JSON 搴忓垪鍖栧櫒
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
                ObjectMapper mapper = objectMapper != null ? objectMapper : JsonUtils.getMapper();
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                return result;
            }
        }
        return BaseResponse.success((Serializable) body);
    }

    /**
     * 瀛愮被瑕嗙洊姝ゆ柟娉曞鐞?String 绫诲瀷杩斿洖鍊肩殑鍖呰宸紓
     */
    protected abstract BaseResponse<String> wrapStringBody(String body);
}
