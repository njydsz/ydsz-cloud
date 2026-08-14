package com.njydsz.common.json.jakarta;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.type.JsonType;

/**
 * YdszJson 对标准 JSON-B API（JSR 367 / Jakarta JSON Binding 3.0）的适配器。
 *
 * <p>实现 {@link Jsonb} 接口，底层委托给 {@link YdszJson}，
 * 使业务代码可使用标准 JSON-B API 而不绑定 YdszJson 具体实现。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 通过 JsonbBuilder 创建（标准方式）
 * Jsonb jsonb = YdszJsonbBuilder.create().build();
 * String json = jsonb.toJson(user);
 * User user = jsonb.fromJson(json, User.class);
 *
 * // 泛型支持
 * List&lt;User&gt; users = jsonb.fromJson(json, new JsonType&lt;List&lt;User&gt;&gt;() {}.getType());
 * </pre>
 *
 * <p><b>对标行业实现：</b></p>
 * <ul>
 *   <li>对标 Eclipse Yasson（Jakarta JSON-B 参考实现）</li>
 *   <li>对标 Jackson-databind 的 JsonB 适配器</li>
 *   <li>对标 FastJSON2 的 JSONB 适配器</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本实现无状态，线程安全，可单例使用。</p>
 *
 * @author ydsz-team
 * @since 1.2.1
 */
public class YdszJsonb implements Jsonb {

    /**
     * 默认单例（无配置场景直接使用）。
     */
    private static final YdszJsonb DEFAULT_INSTANCE = new YdszJsonb();

    /**
     * 获取默认 YdszJsonb 实例。
     *
     * @return 默认实例
     */
    public static YdszJsonb defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * 无参构造函数（使用 YdszJson 默认全局配置）。
     */
    public YdszJsonb() {
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) throws JsonbException {
        if (json == null) {
            return null;
        }
        try {
            return YdszJson.toObject(json, type);
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.fromJson failed for type: " + type.getName(), e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type runtimeType) throws JsonbException {
        if (json == null) {
            return null;
        }
        try {
            return YdszJson.toObject(json, new JsonType<T>() {
                @Override
                public Type getType() {
                    return runtimeType;
                }
            });
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.fromJson failed for type: " + runtimeType, e);
        }
    }

    @Override
    public <T> T fromJson(InputStream is, Class<T> type) throws JsonbException {
        if (is == null) {
            return null;
        }
        try {
            return YdszJson.toObject(is, type);
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.fromJson(InputStream) failed for type: " + type.getName(), e);
        }
    }

    @Override
    public <T> T fromJson(InputStream is, Type runtimeType) throws JsonbException {
        if (is == null) {
            return null;
        }
        try {
            return YdszJson.toObject(is, new JsonType<T>() {
                @Override
                public Type getType() {
                    return runtimeType;
                }
            });
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.fromJson(InputStream) failed for type: " + runtimeType, e);
        }
    }

    @Override
    public <T> T fromJson(Reader reader, Class<T> type) throws JsonbException {
        if (reader == null) {
            return null;
        }
        try {
            String json = toString(reader);
            return YdszJson.toObject(json, type);
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.fromJson(Reader) failed for type: " + type.getName(), e);
        }
    }

    @Override
    public <T> T fromJson(Reader reader, Type runtimeType) throws JsonbException {
        if (reader == null) {
            return null;
        }
        try {
            String json = toString(reader);
            return YdszJson.toObject(json, new JsonType<T>() {
                @Override
                public Type getType() {
                    return runtimeType;
                }
            });
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.fromJson(Reader) failed for type: " + runtimeType, e);
        }
    }

    @Override
    public String toJson(Object object) throws JsonbException {
        if (object == null) {
            return "null";
        }
        try {
            return YdszJson.toJson(object);
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.toJson failed for object: " + object.getClass().getName(), e);
        }
    }

    @Override
    public <T> String toJson(T object, Type runtimeType) throws JsonbException {
        // runtimeType 仅用于类型推断，YdszJson 序列化不需要显式类型
        return toJson(object);
    }

    @Override
    public void toJson(Object object, OutputStream os) throws JsonbException {
        if (os == null) {
            return;
        }
        try {
            String json = toJson(object);
            os.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.toJson(OutputStream) failed", e);
        }
    }

    @Override
    public <T> void toJson(T object, Type runtimeType, OutputStream os) throws JsonbException {
        toJson(object, os);
    }

    @Override
    public void toJson(Object object, Writer writer) throws JsonbException {
        if (writer == null) {
            return;
        }
        try {
            writer.write(toJson(object));
        } catch (Exception e) {
            throw new JsonbException("YdszJsonb.toJson(Writer) failed", e);
        }
    }

    @Override
    public <T> void toJson(T object, Type runtimeType, Writer writer) throws JsonbException {
        toJson(object, writer);
    }

    @Override
    public void close() {
        // 无资源需要释放（YdszJson 使用 ThreadLocal 池，由 clearThreadLocals 管理）
    }

    /**
     * 将 Reader 内容读取为字符串。
     */
    private static String toString(Reader reader) throws java.io.IOException {
        StringBuilder sb = new StringBuilder(1024);
        char[] buffer = new char[1024];
        int n;
        while ((n = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, n);
        }
        return sb.toString();
    }
}
