package com.remisoft.common.json.spring;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

import com.remisoft.common.json.YdszJson;
import com.remisoft.common.json.provider.DeserializationProvider;
import com.remisoft.common.json.provider.SerializationProvider;

/**
 * YdszJson HTTP 消息转换器。
 *
 * <p>通用 JSON 消息转换器，支持所有 Java 对象类型的 JSON 序列化/反序列化。
 * 自动注册到 Spring MVC 的 {@code HttpMessageConverter} 链中。
 *
 * <p>支持 {@code application/json} 和 {@code application/*+json} 媒体类型。
 *
 * <p><b>安全与性能：</b></p>
 * <ul>
 *   <li>读取时双重防护：Content-Length 预检 + 流式字节计数，覆盖 Content-Length 伪造与 chunked encoding 场景</li>
 *   <li>写入支持两种模式：
 *     <ul>
 *       <li>缓冲模式（默认）：序列化为 byte[] 后设置 Content-Length 一次性写出，避免 chunked 编码开销</li>
 *       <li>流式模式（{@link #setStreamingEnabled(boolean)}）: 直接序列化到 OutputStream，使用 chunked encoding，避免内存中持有完整 byte[]</li>
 *     </ul>
 *   </li>
 *   <li>不手动 flush，由 Spring 框架统一管理输出流生命周期</li>
 * </ul>
 *
 * <p><b>流式模式说明：</b></p>
 * <p>启用流式模式后，响应将使用 HTTP chunked transfer encoding（不设置 Content-Length）。
 * 适用场景：大响应体（如导出、批量查询），可降低内存峰值。注意：YdszJson 内部序列化链
 * 仍以 byte[] 形式生成 JSON，真正的零内存流式输出需要后续重构 YdszJson 序列化链。
 * 当前实现已将 byte[] 直接写入 OutputStream，避免了 byte[] 在 Converter 层再次拷贝。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class JsonHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    /** 默认最大请求体大小（10MB），超过此值的请求将被拒绝 */
    private static final long MAX_REQUEST_BODY_SIZE = 10L * 1024 * 1024;

    /** 读取缓冲区大小（8KB，平衡内存占用与系统调用次数） */
    private static final int READ_BUFFER_SIZE = 8192;

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

    /** 是否启用流式输出（默认 false，使用缓冲模式设置 Content-Length） */
    private boolean streamingEnabled = false;

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
     * @since 1.0.0
     */
    public void setMaxRequestBodySize(long maxRequestBodySize) {
        this.maxRequestBodySize = maxRequestBodySize;
    }

    /**
     * 设置是否启用流式输出。
     *
     * <p>启用后响应使用 HTTP chunked transfer encoding（不设置 Content-Length），
     * 适用于大响应体场景，可降低内存峰值。默认关闭，保持与 Jackson/FastJSON2 一致的
     * 「序列化为 byte[] + 设置 Content-Length」缓冲模式。</p>
     *
     * @param streamingEnabled 是否启用流式输出
     * @since 1.0.0
     */
    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    /**
     * 读取泛型类型请求体（P1-5: 支持 @RequestBody List&lt;User&gt;等泛型类型）。
     *
     * <p>重写父类的 {@code read(Type, Class, HttpInputMessage)} 方法，当 {@code type}
     * 为 {@link ParameterizedType} 时委托 {@link DeserializationProvider#deserialize(byte[], Type)}
     * 处理泛型类型。</p>
     *
     * @param type 目标类型（可能是 Class 或 ParameterizedType）
     * @param contextClass 上下文类
     * @param inputMessage HTTP 输入消息
     * @return 反序列化后的对象
     * @throws IOException 读取失败
     * @throws HttpMessageNotReadableException JSON 解析失败
     * @since 1.0.0
     */
    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        return read(clazz, null, inputMessage);
    }

    /**
     * 重写父类的 {@code read(Type, Class, HttpInputMessage)} 方法，当 {@code type}
     * 为 {@link ParameterizedType} 时委托 {@code DeserializationProvider.deserialize(byte[], Type)}
     * 处理泛型类型。
     */
    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try {
            long contentLength = inputMessage.getHeaders().getContentLength();
            if (contentLength > maxRequestBodySize) {
                throw new IOException("Request body too large (Content-Length): " + contentLength
                        + " > " + maxRequestBodySize);
            }
            // 预估容量：优先用 Content-Length 提示，避免 ByteArrayOutputStream 频繁扩容
            int estimatedSize = contentLength > 0
                    ? (int) Math.min(contentLength, maxRequestBodySize)
                    : READ_BUFFER_SIZE;
            byte[] body = readBoundedBytes(inputMessage.getBody(), maxRequestBodySize, estimatedSize);
            if (body.length == 0) {
                return null;
            }
            // 使用 ResolvableType 解析泛型类型
            if (type instanceof ParameterizedType) {
                return DeserializationProvider.deserialize(body, type);
            }
            // 非 ParameterizedType 退回常规路径
            Class<?> rawClass = type instanceof Class<?> c ? c : Object.class;
            return YdszJson.fromJsonBytes(body, rawClass);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new HttpMessageNotReadableException("JSON 解析失败：" + e.getMessage(), e, inputMessage);
        } finally {
            // 请求结束后清理 ThreadLocal 资源，防止 Tomcat 线程池泄漏
            SerializationProvider.clearThreadLocals();
        }
    }

    /**
     * 从输入流中读取最多 maxBytes 字节，超限时立即抛 IOException。
     *
     * <p>相比 {@link InputStream#readAllBytes()} 的无界读取，本方法在读取过程中实时
     * 累计已读字节数，超过阈值即抛异常，防止攻击者通过伪造 Content-Length 或使用
     * chunked encoding 绕过预检。</p>
     *
     * @param input 原始输入流
     * @param maxBytes 最大允许读取字节数
     * @param estimatedSize 预估容量（用于 ByteArrayOutputStream 初始分配，避免频繁扩容）
     * @return 读取的字节数组（长度不超过 maxBytes）
     * @throws IOException 读取失败或超过大小限制
     * @since 1.0.0
     */
    private static byte[] readBoundedBytes(InputStream input, long maxBytes, int estimatedSize) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(estimatedSize);
        byte[] chunk = new byte[READ_BUFFER_SIZE];
        long totalRead = 0;
        int n;
        while ((n = input.read(chunk)) != -1) {
            totalRead += n;
            if (totalRead > maxBytes) {
                throw new IOException("Request body exceeds maximum size: " + maxBytes
                        + " (read " + totalRead + " bytes so far)");
            }
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try {
            // 检查是否带有 @JsonView 视图过滤（通过反射探测 Spring 的 MappingJacksonValue 包装类）
            Class<?> viewClass = extractViewClass(o);
            Object value = extractValue(o);

            OutputStream out = outputMessage.getBody();

            if (streamingEnabled) {
                // 流式模式：直接写入 OutputStream，使用 chunked transfer encoding
                // 不设置 Content-Length，避免在 Converter 层持有完整 byte[]
                writeStreaming(value, viewClass, out);
            } else {
                // 缓冲模式：序列化为 byte[] 后设置 Content-Length 一次性写出
                // 与 Jackson/FastJSON2 行为一致，适用于大多数 REST API 响应
                writeBuffered(value, viewClass, outputMessage, out);
            }
            // 不手动 flush，由 Spring 框架统一管理输出流生命周期
        } catch (Exception e) {
            throw new HttpMessageNotWritableException("JSON 序列化失败：" + e.getMessage(), e);
        } finally {
            // 请求结束后清理 ThreadLocal 资源（StringBuilder/JSONWriter/循环引用检测集），防止 Tomcat 线程池泄漏
            com.remisoft.common.json.provider.SerializationProvider.clearThreadLocals();
        }
    }

    /**
     * 缓冲模式：序列化为 byte[] 后设置 Content-Length 一次性写出
     */
    private void writeBuffered(Object value, Class<?> viewClass,
                                HttpOutputMessage outputMessage, OutputStream out) throws IOException {
        byte[] bytes;
        if (viewClass != null) {
            bytes = SerializationProvider.serializeWithView(value, viewClass)
                    .getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = YdszJson.toJsonBytes(value);
        }
        // 设置 Content-Length，避免 HTTP chunked 编码开销
        outputMessage.getHeaders().setContentLength(bytes.length);
        StreamUtils.copy(bytes, out);
    }

    @Override
    protected void writeInternal(Object o, Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        writeInternal(o, outputMessage);
    }

    /**
     * 流式模式：直接写入 OutputStream，不设置 Content-Length（使用 chunked encoding）
     *
     * <p>使用 Spring 的 {@link StreamUtils#copy(String, java.nio.charset.Charset, OutputStream)}
     * 以 UTF-8 编码流式写入。相对于直接 {@code out.write(bytes)} 的优势：避免 byte[] 在
     * Converter 层被持有为局部变量，可以让 GC 更早回收。</p>
     */
    private void writeStreaming(Object value, Class<?> viewClass, OutputStream out) throws IOException {
        if (viewClass != null) {
            // 视图过滤场景：必须先序列化为 String 以应用视图
            String json = SerializationProvider.serializeWithView(value, viewClass);
            StreamUtils.copy(json, StandardCharsets.UTF_8, out);
        } else {
            // 普通场景：直接序列化到 OutputStream
            YdszJson.toJson(value, out);
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
     * @since 1.0.0
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
     * @since 1.0.0
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
