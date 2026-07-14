package com.njydsz.pmis.common.json.spring;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.MimeType;

import com.njydsz.pmis.common.json.Json;

/**
 * Json Reactive 编码器（用于 WebFlux 响应式场景）。
 *
 * <p>实现 Spring WebFlux 的 {@link org.springframework.core.codec.Encoder} 接口，
 * 使 Json 引擎能够用于响应式 Web 应用的 JSON 编码。</p>
 *
 * <p>支持 {@code application/json} 和 {@code application/*+json} 媒体类型。
 * 使用 DataBuffer 直接写入 UTF-8 字节，避免中间 String 分配。</p>
 *
 * @since 1.4.0
 */
public class JsonReactiveEncoder extends AbstractEncoder<Object> {

    /**
     * 构造函数，注册支持的媒体类型。
     */
    public JsonReactiveEncoder() {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
    }

    @Override
    public boolean canEncode(@NonNull ResolvableType elementType, MimeType mimeType) {
        if (CharSequence.class.isAssignableFrom(elementType.toClass())) {
            return false;
        }
        return super.canEncode(elementType, mimeType);
    }

    @Override
    @NonNull
    public DataBuffer encodeValue(
            @NonNull Object value, @NonNull DataBufferFactory bufferFactory,
            @NonNull ResolvableType valueType, MimeType mimeType,
            Map<String, Object> hints) {
        byte[] bytes = Json.toJsonBytes(value);
        DataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
        buffer.write(bytes, 0, bytes.length);
        return buffer;
    }

    @Override
    @NonNull
    public org.reactivestreams.Publisher<DataBuffer> encode(
            @NonNull Publisher<?> inputStream, @NonNull DataBufferFactory bufferFactory,
            @NonNull ResolvableType elementType, MimeType mimeType,
            Map<String, Object> hints) {
        return org.springframework.core.codec.DecoderStream.from(inputStream,
                value -> encodeValue(value, bufferFactory, elementType, mimeType, hints));
    }

    @Override
    @NonNull
    public List<MimeType> getEncodableMimeTypes() {
        return Collections.singletonList(MediaType.APPLICATION_JSON);
    }
}
