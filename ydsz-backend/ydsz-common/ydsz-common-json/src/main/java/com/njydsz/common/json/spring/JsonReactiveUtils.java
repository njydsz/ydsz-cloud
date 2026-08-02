package com.njydsz.common.json.spring;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;

import com.njydsz.common.json.YdszJson;

import com.njydsz.common.json.provider.SerializationProvider;
/**
 * YdszJson Reactive 工具类（用于 WebFlux 响应式场景）。
 *
 * <p>提供将对象序列化为 {@link DataBuffer} 的工具方法，
 * 供 WebFlux 应用在自定义 Encoder 或 Handler 中直接调用。</p>
 *
 * <p>使用 DataBuffer 直接写入 UTF-8 字节，避免中间 String 分配。</p>
 *
 * <p>示例：</p>
 * <pre><code>
 * public Mono&lt;DataBuffer&gt; encode(Object obj, DataBufferFactory factory) {
 *     return Mono.fromCallable(() -> JsonReactiveUtils.encode(obj, factory));
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JsonReactiveUtils {

    private JsonReactiveUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * 将对象序列化为 JSON DataBuffer。
     *
     * @param obj 要序列化的对象
     * @param bufferFactory DataBuffer 工厂
     * @return 包含 JSON UTF-8 字节的 DataBuffer
     */
    public static DataBuffer encode(Object obj, DataBufferFactory bufferFactory) {
        byte[] bytes = YdszJson.toJsonBytes(obj);
        DataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
        buffer.write(bytes, 0, bytes.length);
        return buffer;
    }

    /**
     * 将对象序列化为 JSON DataBuffer（带视图过滤）。
     *
     * @param obj 要序列化的对象
     * @param bufferFactory DataBuffer 工厂
     * @param viewClass 视图类（用于 @JsonView 过滤）
     * @return 包含 JSON UTF-8 字节的 DataBuffer
     * @since 1.0.0
     */
    public static DataBuffer encodeWithView(Object obj, DataBufferFactory bufferFactory,
            Class<?> viewClass) {
        String json = SerializationProvider
                .serializeWithView(obj, viewClass);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
        buffer.write(bytes, 0, bytes.length);
        return buffer;
    }
}
