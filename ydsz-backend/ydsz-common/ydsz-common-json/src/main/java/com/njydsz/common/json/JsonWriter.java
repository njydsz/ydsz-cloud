package com.njydsz.common.json;

import java.io.OutputStream;
import java.io.Writer;

import com.njydsz.common.json.exception.JsonException;

/**
 * 绑定型 JSON 写入器（对标 Jackson {@code ObjectWriter}）。
 *
 * <p>通过 {@link JsonMapper#writerFor(Class)} 创建，
 * 绑定目标类型和 Mapper 配置后可重复使用。序列化时会校验对象类型与绑定类型的兼容性，
 * 并预查找 ASM 序列化器缓存以加速后续序列化。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * JsonWriter&lt;User&gt; writer = mapper.writerFor(User.class);
 * String json1 = writer.write(user1);
 * String json2 = writer.write(user2);
 * </pre>
 *
 * @param <T> 目标类型
 * @author ydsz-team
 * @since 1.4.0
 */
public final class JsonWriter<T> {

    private final JsonMapper mapper;
    private final Class<T> targetClass;

    /**
     * 创建绑定指定 Mapper 和目标类型的写入器。
     *
     * @param mapper 所属 Mapper
     * @param targetClass 绑定的目标类型（可为 null，表示不绑定特定类型）
     */
    JsonWriter(JsonMapper mapper, Class<T> targetClass) {
        this.mapper = mapper;
        this.targetClass = targetClass;
        // 预热 ASM 序列化器缓存，加速后续序列化
        if (targetClass != null) {
            YdszJson.warmup(targetClass);
        }
    }

    /**
     * 序列化对象为 JSON 字符串。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     */
    public String write(T obj) {
        validateType(obj);
        return mapper.toJson(obj);
    }

    /**
     * 序列化对象为 UTF-8 字节数组。
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     */
    public byte[] writeAsBytes(T obj) {
        validateType(obj);
        return mapper.toJsonBytes(obj);
    }

    /**
     * 序列化对象并写入 OutputStream。
     *
     * @param obj 要序列化的对象
     * @param out 输出流
     */
    public void write(T obj, OutputStream out) {
        byte[] bytes = writeAsBytes(obj);
        try {
            out.write(bytes);
        } catch (Exception e) {
            throw new JsonException("Failed to write to OutputStream", e);
        }
    }

    /**
     * 序列化对象并写入 Writer。
     *
     * @param obj    要序列化的对象
     * @param writer 字符输出流
     */
    public void write(T obj, Writer writer) {
        String json = write(obj);
        try {
            writer.write(json);
        } catch (Exception e) {
            throw new JsonException("Failed to write to Writer", e);
        }
    }

    /**
     * 获取绑定的目标类型。
     *
     * @return 绑定的目标类型，未绑定时返回 null
     * @since 1.4.0
     */
    public Class<T> getTargetClass() {
        return targetClass;
    }

    /**
     * 校验对象类型与绑定类型的兼容性。
     *
     * <p>当绑定了目标类型时，检查 {@code obj} 是否为该类型的实例，
     * 不匹配时抛出 {@link IllegalArgumentException}，避免类型错误传播到序列化深层。</p>
     *
     * @param obj 待序列化的对象
     */
    private void validateType(T obj) {
        if (targetClass != null && obj != null && !targetClass.isInstance(obj)) {
            throw new IllegalArgumentException(
                "Type mismatch: writer is bound to " + targetClass.getName()
                + " but got " + obj.getClass().getName());
        }
    }
}
