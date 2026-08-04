package com.remisoft.common.feign.codec;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.core.response.IResponse;

import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

/**
 * Feign 响应自动解包解码器
 *
 * <p>自动解包 {@link IResponse} / {@link BaseResponse} 类型的响应，
 * 直接返回内部 data 字段，简化 Feign 客户端接口定义。
 *
 * <p><b>使用场景：</b>
 * <p>当 Feign 客户端接口方法声明返回类型为业务对象（如 {@code User}），
 * 而实际服务端返回的是统一包装格式（如 {@code Result<User>}）时，
 * 此解码器会自动提取 data 字段并反序列化为目标类型。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Feign 客户端接口
 * public interface UserClient {
 *     // 服务端返回 Result<User>，但接口可直接声明返回 User
 *     @GetMapping("/users/{id}")
 *     User getUser(@PathVariable("id") Long id);
 *
 *     // 如果需要完整响应，仍可使用 IResponse<User>
 *     @GetMapping("/users/{id}")
 *     IResponse<User> getUserWithWrapper(@PathVariable("id") Long id);
 * }
 * }</pre>
 *
 * <p><b>解包规则：</b>
 * <ul>
 *   <li>目标类型为 {@link IResponse} 或其子类 → 不解包，返回完整响应</li>
 *   <li>目标类型为普通业务类型 → 先反序列化为 {@link BaseResponse}，再提取 data</li>
 *   <li>响应 code 不等于成功码 → 抛出 {@link FeignBusinessException}</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 * @see JsonDecoder
 */
public class ResponseUnwrapDecoder implements Decoder {

    private static final Logger log = LoggerFactory.getLogger(ResponseUnwrapDecoder.class);

    /** 委托的实际解码器 */
    private final Decoder delegate;

    /**
     * 使用指定的委托解码器构造
     *
     * @param delegate 委托解码器，用于实际的反序列化
     */
    public ResponseUnwrapDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    /**
     * 使用默认的 {@link JsonDecoder} 构造
     */
    public ResponseUnwrapDecoder() {
        this(new JsonDecoder());
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException {
        // 1. 判断目标类型是否为 IResponse 或其子类
        if (isResponseType(type)) {
            // 目标类型是 IResponse，不解包，直接解码
            log.debug("目标类型为 IResponse，不解包: {}", type);
            return delegate.decode(response, type);
        }

        // 2. 目标类型是普通业务类型，先解码为 BaseResponse
        Type wrapperType = buildWrapperType(type);
        Object decoded = delegate.decode(response, wrapperType);

        // 3. 提取 data 字段
        if (decoded == null) {
            return null;
        }

        if (decoded instanceof IResponse) {
            IResponse<?> wrapper = (IResponse<?>) decoded;

            // 检查响应码
            if (!isSuccess(wrapper)) {
                String code = wrapper.getCode();
                String msg = wrapper.getMsg();
                log.warn("Feign 响应业务失败, code: {}, msg: {}", code, msg);
                throw new FeignBusinessException(code, msg, response.request().url());
            }

            Object data = wrapper.getData();
            log.debug("响应解包成功, 目标类型: {}, data 类型: {}", type,
                    data != null ? data.getClass().getSimpleName() : "null");
            return data;
        }

        // 4. 解码结果不是 IResponse，直接返回
        log.debug("解码结果非 IResponse，直接返回: {}", decoded.getClass());
        return decoded;
    }

    /**
     * 判断目标类型是否为 IResponse 或其子类
     *
     * @param type 目标类型
     * @return true 表示是 IResponse 类型
     */
    private boolean isResponseType(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            return IResponse.class.isAssignableFrom(clazz);
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                return IResponse.class.isAssignableFrom((Class<?>) rawType);
            }
        }
        return false;
    }

    /**
     * 构建包装类型 BaseResponse<T>
     *
     * @param innerType 内部数据类型
     * @return BaseResponse<innerType> 类型
     */
    private Type buildWrapperType(Type innerType) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{innerType};
            }

            @Override
            public Type getRawType() {
                return BaseResponse.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    /**
     * 判断响应是否成功
     *
     * @param response 响应对象
     * @return true 表示成功
     */
    private boolean isSuccess(IResponse<?> response) {
        return response.isSuccess();
    }

    /**
     * Feign 业务异常
     *
     * <p>当 Feign 调用返回的业务状态码非成功时抛出
     */
    public static class FeignBusinessException extends DecodeException {

        private static final long serialVersionUID = 1L;

        private final String code;
        private final String msg;
        private final String url;

        public FeignBusinessException(String code, String msg, String url) {
            super(500, String.format("Feign 业务失败, url: %s, code: %s, msg: %s", url, code, msg), null);
            this.code = code;
            this.msg = msg;
            this.url = url;
        }

        public String getCode() {
            return code;
        }

        public String getMsg() {
            return msg;
        }

        public String getUrl() {
            return url;
        }
    }
}
