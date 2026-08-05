package com.remisoft.common.json.ndjson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.exception.JsonException;

/**
 * NDJSON（Newline Delimited JSON）工具类。
 *
 * <p>提供标准化的 NDJSON 读写能力，每行一个完整 JSON 对象，符合 RFC 7464 标准。</p>
 *
 * <ul>
 *   <li><b>parse</b>：将 NDJSON 字符串/输入流解析为指定类型的 List 或 Stream</li>
 *   <li><b>write</b>：将对象集合序列化为 NDJSON 格式并输出到流</li>
 *   <li><b>writeAsStream</b>：将对象流序列化为 NDJSON 格式（低内存峰值）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 解析
 * List&lt;User&gt; users = NdjsonUtils.parse(jsonl, User.class);
 *
 * // 写入
 * NdjsonUtils.write(objects, outputStream);
 *
 * // 流式解析
 * try (Stream&lt;User&gt; stream = NdjsonUtils.parseStream(inputStream, User.class)) {
 *     stream.forEach(System.out::println);
 * }
 * </pre>
 *
 * @author remi-team
 * @since 1.1.0
 */
public final class NdjsonUtils {

    /** NDJSON 行分隔符（LF，符合 POSIX 标准） */
    private static final char LINE_FEED = '\n';

    /** NDJSON 回车符（CRLF 兼容） */
    private static final char CARRIAGE_RETURN = '\r';

    /** 默认缓冲区大小（8KB） */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private NdjsonUtils() {
        throw new UnsupportedOperationException("NdjsonUtils is a utility class and cannot be instantiated");
    }

    /**
     * 将 NDJSON 字符串解析为指定类型的 List。
     *
     * <p>空行自动跳过，尾部换行符自动移除。</p>
     *
     * @param jsonl NDJSON 字符串（每行一个 JSON 对象）
     * @param clazz 目标元素类型
     * @param <T>   元素类型参数
     * @return 解析后的 List，jsonl 为空时返回空 List
     */
    public static <T> List<T> parse(String jsonl, Class<T> clazz) {
        Objects.requireNonNull(clazz, "Target class must not be null");
        List<T> result = new ArrayList<>();
        if (jsonl == null || jsonl.isEmpty()) {
            return result;
        }

        String[] lines = jsonl.split("\n", -1);
        for (String line : lines) {
            String trimmed = stripCarriageReturn(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            T obj = RemiJson.toObject(trimmed, clazz);
            result.add(obj);
        }
        return result;
    }

    /**
     * 将 NDJSON 字符串解析为指定泛型类型的 List。
     *
     * @param jsonl   NDJSON 字符串
     * @param typeRef 类型引用
     * @param <T>     元素类型参数
     * @return 解析后的 List
     */
    public static <T> List<T> parse(String jsonl, com.remisoft.common.json.type.JsonType<T> typeRef) {
        Objects.requireNonNull(typeRef, "TypeRef must not be null");
        List<T> result = new ArrayList<>();
        if (jsonl == null || jsonl.isEmpty()) {
            return result;
        }

        String[] lines = jsonl.split("\n", -1);
        for (String line : lines) {
            String trimmed = stripCarriageReturn(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            T obj = RemiJson.toObject(trimmed, typeRef);
            result.add(obj);
        }
        return result;
    }

    /**
     * 从 InputStream 流式解析 NDJSON（大文件友好）。
     *
     * <p>逐行读取并解析，避免将整个文件载入内存。
     * 调用方负责关闭 Stream。</p>
     *
     * @param inputStream 输入流（UTF-8 编码）
     * @param clazz       目标元素类型
     * @param <T>         元素类型参数
     * @return 元素流（需关闭以释放资源）
     */
    public static <T> Stream<T> parseStream(InputStream inputStream, Class<T> clazz) {
        Objects.requireNonNull(inputStream, "InputStream must not be null");
        Objects.requireNonNull(clazz, "Target class must not be null");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8), DEFAULT_BUFFER_SIZE);

        return reader.lines()
                .filter(line -> !stripCarriageReturn(line).isEmpty())
                .map(line -> RemiJson.toObject(line, clazz))
                .onClose(() -> {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                        // ignore on close
                    }
                });
    }

    /**
     * 将对象集合序列化为 NDJSON 格式并写入输出流。
     *
     * <p>每个对象单独一行，使用 LF 分隔。性能敏感场景建议使用
     * {@link #writeStream(Iterable, OutputStream)} 变体。</p>
     *
     * @param objects 要序列化的对象集合
     * @param out     输出流（UTF-8 编码）
     * @param <T>     对象类型参数
     * @throws JsonException 如果序列化或写入失败
     */
    public static <T> void write(Iterable<T> objects, OutputStream out) {
        Objects.requireNonNull(objects, "Objects must not be null");
        Objects.requireNonNull(out, "OutputStream must not be null");
        try {
            for (T obj : objects) {
                byte[] bytes = RemiJson.toJsonBytes(obj);
                out.write(bytes);
                out.write(LINE_FEED);
            }
            out.flush();
        } catch (IOException e) {
            throw new JsonException("Failed to write NDJSON", e);
        }
    }

    /**
     * 将对象集合序列化为 NDJSON 格式并写入 Writer。
     *
     * @param objects 要序列化的对象集合
     * @param writer  字符输出流
     * @param <T>     对象类型参数
     * @throws JsonException 如果序列化或写入失败
     */
    public static <T> void write(Iterable<T> objects, Writer writer) {
        Objects.requireNonNull(objects, "Objects must not be null");
        Objects.requireNonNull(writer, "Writer must not be null");
        try {
            for (T obj : objects) {
                String json = RemiJson.toJson(obj);
                writer.write(json);
                writer.write(LINE_FEED);
            }
            writer.flush();
        } catch (IOException e) {
            throw new JsonException("Failed to write NDJSON", e);
        }
    }

    /**
     * 将对象 Stream 序列化为 NDJSON 格式（低内存峰值）。
     *
     * <p>适合大数据量流处理场景，避免中间 List 缓存。</p>
     *
     * @param objects 对象流
     * @param out     输出流（UTF-8 编码）
     * @param <T>     对象类型参数
     * @throws JsonException 如果序列化或写入失败
     */
    public static <T> void writeStream(Stream<T> objects, OutputStream out) {
        Objects.requireNonNull(objects, "Objects must not be null");
        Objects.requireNonNull(out, "OutputStream must not be null");
        objects.forEach(obj -> {
            try {
                byte[] bytes = RemiJson.toJsonBytes(obj);
                synchronized (out) {
                    out.write(bytes);
                    out.write(LINE_FEED);
                    out.flush();
                }
            } catch (IOException e) {
                throw new JsonException("Failed to write NDJSON", e);
            }
        });
    }

    /**
     * 验证字符串是否为合法 NDJSON（每行必须是合法 JSON）。
     *
     * @param jsonl 待验证字符串
     * @return true 如果每行都是合法 JSON（或空行）
     */
    public static boolean isValidNdjson(String jsonl) {
        if (jsonl == null || jsonl.isEmpty()) {
            return true;
        }
        String[] lines = jsonl.split("\n", -1);
        for (String line : lines) {
            String trimmed = stripCarriageReturn(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!RemiJson.isValidJson(trimmed)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 移除行尾回车符（兼容 CRLF 和 LF）。
     */
    private static String stripCarriageReturn(String line) {
        if (line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }
}
