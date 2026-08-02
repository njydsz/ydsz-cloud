package com.njydsz.common.json;

import java.io.OutputStream;
import java.io.Writer;

import com.njydsz.common.json.exception.YdszJsonException;

/**
 * 绑定型 JSON 写入器（对标 Jackson {@code ObjectWriter}）。
 *
 * <p>通过 {@link YdszJsonMapper#writerFor(Class)} 创建，
 * 绑定 Mapper 配置后可重复使用。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * YdszJsonWriter&lt;User&gt; writer = mapper.writerFor(User.class);
 * String json1 = writer.write(user1);
 * String json2 = writer.write(user2);
 * </pre>
 *
 * @param <T> 目标类型
 * @author ydsz-team
 * @since 1.4.0
 */
public final class YdszJsonWriter<T> {

    private final YdszJsonMapper mapper;

    /**
     * 创建绑定指定 Mapper 的写入器。
     *
     * @param mapper 所属 Mapper
     */
    YdszJsonWriter(YdszJsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 序列化对象为 JSON 字符串。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     */
    public String write(T obj) {
        return mapper.toJson(obj);
    }

    /**
     * 序列化对象为 UTF-8 字节数组。
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     */
    public byte[] writeAsBytes(T obj) {
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
            throw new YdszJsonException("Failed to write to OutputStream", e);
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
            throw new YdszJsonException("Failed to write to Writer", e);
        }
    }
}
