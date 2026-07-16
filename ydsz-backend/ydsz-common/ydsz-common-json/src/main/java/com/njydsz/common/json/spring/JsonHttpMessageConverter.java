package com.njydsz.common.json.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import com.njydsz.common.json.Json;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * Json HTTP 消息转换器。
 *
 * <p>通用 JSON 消息转换器，支持所有 Java 对象类型的 JSON 序列化/反序列化。
 * 自动注册到 Spring MVC 的 {@code HttpMessageConverter} 链中。
 *
 * <p>支持 {@code application/json} 和 {@code application/*+json} 媒体类型。
 *
 * <p><b>优化：</b></p>
 * <ul>
 *   <li>写入时直接输出 UTF-8 字节并设置 Content-Length，避免 chunked 编码开销</li>
 *   <li>读取时在读取前校验 Content-Length，防止超大 payload DoS 攻击</li>
 *   <li>不手动 flush，由 Spring 框架统一管理输出流生命周期</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class JsonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    /** 默认最大请求体大小（10MB），超过此值的请求将被拒绝 */
    private static final long MAX_REQUEST_BODY_SIZE = 10L * 1024 * 1024;

    /**
     * Spring 的 {@code MappingJacksonValue} 在 Spring 7.0 已被标记为待删除（{@code @Deprecated(since="7.0", forRemoval=true)}），
     * 其 class 文件字节码内部仍引用 {@code com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY} 等 Jackson 枚举常量。
     * 本模块刻意不引入 Jackson 依赖，因此不能直接 import 引用该类（否则编译器会同时报告 5 个
     * "未知的枚举常量 JsonInclude.Include.NON_EMPTY" 警告）。
     * <p>此处用反射按需探测：找到则按既有语义解包；找不到（Spring 7.x 移除该类后）则降级为原样输出，
     * 届时 controller 侧应改用 {@code @JsonView} 注解由框架消息转换器处理视图过滤。
     */
    private static final Class<?> MAPPING_JACKSON_VALUE_CLASS = loadClassOrNull(
            "org.springframework.http.converter.json.MappingJacksonValue");
    private static final Method GET_SERIALIZATION_VIEW_METHOD = lookupMethodOrNull(
            MAPPING_JACKSON_VALUE_CLASS, "getSerializationView");
    private static final Method GET_VALUE_METHOD = lookupMethodOrNull(
            MAPPING_JACKSON_VALUE_CLASS, "getValue");

    /** 可配置的最大请求体大小（默认与 MAX_REQUEST_BODY_SIZE 相同） */
    private long maxRequestBodySize = MAX_REQUEST_BODY_SIZE;

    private static Class<?> loadClassOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method lookupMethodOrNull(Class<?> owner, String methodName) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 构造函数，注册支持的媒体类型。
     */
    public JsonHttpMessageConverter() {
        super(StandardCharsets.UTF_8,
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        // 通用转换器，支持所有非 CharSequence 类型
        return !CharSequence.class.isAssignableFrom(clazz);
    }

    /**
     * 设置最大请求体大小。
     *
     * @param maxRequestBodySize 最大请求体大小（字节）
     * @since 1.4.0
     */
    public void setMaxRequestBodySize(long maxRequestBodySize) {
        this.maxRequestBodySize = maxRequestBodySize;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try {
            // 读取前校验 Content-Length，防止超大 payload DoS
            long contentLength = inputMessage.getHeaders().getContentLength();
            if (contentLength > maxRequestBodySize) {
                throw new IOException("Request body too large: " + contentLength
                        + " > " + maxRequestBodySize);
            }

            byte[] body = inputMessage.getBody().readAllBytes();
            if (body.length == 0) {
                return null;
            }
            String json = new String(body, getDefaultCharset());
            return Json.toObject(json, clazz);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("JSON 解析失败：" + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try {
            // 检查是否带有 @JsonView 视图过滤（通过反射探测 Spring 的 MappingJacksonValue 包装类）
            Class<?> viewClass = extractViewClass(o);
            Object value = extractValue(o);

            byte[] bytes;
            if (viewClass != null) {
                // 使用视图过滤序列化
                bytes = SerializationProvider.serializeWithView(value, viewClass)
                        .getBytes(StandardCharsets.UTF_8);
            } else {
                bytes = Json.toJsonBytes(value);
            }
            // 设置 Content-Length，避免 HTTP chunked 编码开销
            outputMessage.getHeaders().setContentLength(bytes.length);
            OutputStream out = outputMessage.getBody();
            out.write(bytes);
            // 不手动 flush，由 Spring 框架统一管理输出流生命周期
        } catch (Exception e) {
            throw new HttpMessageNotWritableException("JSON 序列化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从对象中提取 @JsonView 视图类。
     *
     * <p>支持 Spring 的 {@code MappingJacksonValue} 包装类（反射探测，避免直接引用已废弃类），
     * 当控制器方法使用 {@code @JsonView} 注解时，Spring 会将返回值
     * 包装在 {@code MappingJacksonValue} 中，其中包含视图类信息。</p>
     *
     * @param obj 待序列化对象
     * @return 视图类，如果没有视图过滤则返回 null
     * @since 1.4.0
     */
    private Class<?> extractViewClass(Object obj) {
        if (MAPPING_JACKSON_VALUE_CLASS == null || GET_SERIALIZATION_VIEW_METHOD == null) {
            return null;
        }
        if (MAPPING_JACKSON_VALUE_CLASS.isInstance(obj)) {
            try {
                return (Class<?>) GET_SERIALIZATION_VIEW_METHOD.invoke(obj);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 从对象中提取实际值（反射解包 MappingJacksonValue）。
     *
     * @param obj 待序列化对象
     * @return 实际值
     * @since 1.4.0
     */
    private Object extractValue(Object obj) {
        if (MAPPING_JACKSON_VALUE_CLASS == null || GET_VALUE_METHOD == null) {
            return obj;
        }
        if (MAPPING_JACKSON_VALUE_CLASS.isInstance(obj)) {
            try {
                return GET_VALUE_METHOD.invoke(obj);
            } catch (ReflectiveOperationException e) {
                return obj;
            }
        }
        return obj;
    }

}
