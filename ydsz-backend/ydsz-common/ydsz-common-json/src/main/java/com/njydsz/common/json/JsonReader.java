package com.njydsz.common.json;

import java.io.InputStream;
import java.lang.reflect.Type;

import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.metric.MetricsHelper;

/**
 * 绑定型 JSON 读取器（对标 Jackson {@code ObjectReader}）。
 *
 * <p>通过 {@link JsonMapper#readerFor(Class)} 创建，
 * 绑定目标类型后可重复使用，避免每次调用都传入类型参数。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * JsonReader&lt;User&gt; reader = mapper.readerFor(User.class);
 * User user1 = reader.read(json1);
 * User user2 = reader.read(json2);
 * </pre>
 *
 * @param <T> 目标类型
 * @author ydsz-team
 * @since 1.4.0
 */
public final class JsonReader<T> {

    private final JsonMapper mapper;
    private final Class<T> targetClass;
    private final Type targetType;

    /**
     * 创建绑定指定 Class 类型的读取器。
     *
     * @param mapper 所属 Mapper
     * @param targetClass 目标类型
     */
    JsonReader(JsonMapper mapper, Class<T> targetClass) {
        this.mapper = mapper;
        this.targetClass = targetClass;
        this.targetType = targetClass;
    }

    /**
     * 从 JSON 字符串读取。
     *
     * @param json JSON 字符串
     * @return 反序列化后的对象
     */
    public T read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        long maxSize = mapper.getConfig().getMaxJsonSize();
        if (json.length() > maxSize) {
            throw new JsonException(
                "JSON size exceeds limit: " + json.length() + " > " + maxSize);
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            mapper.getConfig().apply();
            return recordDeserialize(() -> DeserializationProvider.deserialize(json, targetType));
        } finally {
            snapshot.restore();
        }
    }

    /**
     * 从字节数组读取。
     *
     * @param bytes UTF-8 编码的 JSON 字节数组
     * @return 反序列化后的对象
     */
    public T read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        long maxSize = mapper.getConfig().getMaxJsonSize();
        if (bytes.length > maxSize) {
            throw new JsonException(
                "JSON size exceeds limit: " + bytes.length + " > " + maxSize);
        }
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        try {
            mapper.getConfig().apply();
            return recordDeserialize(() -> DeserializationProvider.deserialize(bytes, targetType));
        } finally {
            snapshot.restore();
        }
    }

    /**
     * 从 InputStream 读取。
     *
     * @param in 输入流
     * @return 反序列化后的对象
     */
    public T read(InputStream in) {
        if (in == null) {
            return null;
        }
        try {
            byte[] bytes = in.readAllBytes();
            return read(bytes);
        } catch (Exception e) {
            if (e instanceof JsonException) throw (JsonException) e;
            throw new JsonException("Failed to read from InputStream", e);
        }
    }

    private static <T> T recordDeserialize(MetricsHelper.ThrowingSupplier<T> supplier) {
        return MetricsHelper.recordDeserialize(supplier, YdszJson.getMetricsCallback());
    }
}
