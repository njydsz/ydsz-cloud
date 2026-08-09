package com.njydsz.common.json;

import com.njydsz.common.json.exception.JsonException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JSON Lines (NDJSON) 流式读写支持。
 *
 * <p>JSON Lines 格式：每行一个 JSON 对象，使用换行符分隔。
 * 适用于日志导出、大数据批量导入、SSE (Server-Sent Events) 流 API。</p>
 *
 * <p><b>使用示例（写入）：</b></p>
 * <pre>{@code
 * try (JsonLines.Writer writer = JsonLines.writer(outputStream, User.class)) {
 *     for (User user : users) {
 *         writer.write(user); // 每行一个 JSON 对象
 *     }
 * }
 * }</pre>
 *
 * <p><b>使用示例（读取）：</b></p>
 * <pre>{@code
 * try (JsonLines.Reader reader = JsonLines.reader(inputStream, User.class)) {
 *     reader.forEach(user -> process(user));
 * }
 * }</pre>
 *
 * <p><b>使用示例（Stream API）：</b></p>
 * <pre>{@code
 * try (Stream<User> stream = JsonLines.stream(inputStream, User.class)) {
 *     stream.filter(u -> u.getAge() > 18).forEach(System.out::println);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see <a href="https://jsonlines.org/">JSON Lines 规范</a>
 */
public final class JsonLines {

    private JsonLines() {
        throw new UnsupportedOperationException("JsonLines is a utility class");
    }

    // ==================== Writer ====================

    /**
     * JSON Lines 写入器（每行一个 JSON 对象）。
     *
     * <p>实现 {@link AutoCloseable} 支持 try-with-resources，自动关闭底层流。</p>
     *
     * @param <T> 写入对象类型
     */
    public static class Writer<T> implements AutoCloseable {
        private final BufferedWriter writer;
        private final boolean flushPerLine;

        /**
         * @param out           目标输出流
         * @param flushPerLine  是否每行写入后立即 flush（适用于实时流如 SSE）
         */
        public Writer(OutputStream out, boolean flushPerLine) {
            this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            this.flushPerLine = flushPerLine;
        }

        /**
         * @param out 目标输出流
         */
        public Writer(OutputStream out) {
            this(out, false);
        }

        /**
         * 写入一个对象（追加换行符）。
         *
         * @param obj 要写入的对象
         * @throws JsonException 如果写入或序列化失败
         */
        public void write(T obj) {
            try {
                String json = YdszJson.toJson(obj);
                writer.write(json);
                writer.newLine();
                if (flushPerLine) {
                    writer.flush();
                }
            } catch (IOException e) {
                throw new JsonException("Failed to write JSON Lines", e);
            }
        }

        /**
         * 手动刷新缓冲区。
         *
         * @throws JsonException 如果刷新失败
         */
        public void flush() {
            try {
                writer.flush();
            } catch (IOException e) {
                throw new JsonException("Failed to flush JSON Lines writer", e);
            }
        }

        @Override
        public void close() {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                throw new JsonException("Failed to close JSON Lines writer", e);
            }
        }
    }

    // ==================== Reader ====================

    /**
     * JSON Lines 读取器（逐行解析 JSON 对象）。
     *
     * <p>实现 {@link AutoCloseable} 和 {@link Iterable}，支持 for-each 和 try-with-resources。</p>
     *
     * @param <T> 读取对象类型
     */
    public static class Reader<T> implements AutoCloseable, Iterable<T> {
        private final BufferedReader reader;
        private final Class<T> clazz;

        /**
         * @param in    输入流
         * @param clazz 目标类型
         */
        public Reader(InputStream in, Class<T> clazz) {
            this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            this.clazz = clazz;
        }

        /**
         * 逐行处理 JSON 对象。
         *
         * @param consumer 处理每一行的消费者
         * @throws JsonException 如果解析失败
         */
        public void forEach(Consumer<T> consumer) {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue; // 跳过空行
                    }
                    T obj = YdszJson.fromJson(line, clazz);
                    consumer.accept(obj);
                }
            } catch (IOException e) {
                throw new JsonException("Failed to read JSON Lines", e);
            }
        }

        @Override
        public java.util.Iterator<T> iterator() {
            try {
                return new Iterator();
            } catch (IOException e) {
                throw new JsonException("Failed to create JSON Lines iterator", e);
            }
        }

        /**
         * 读取所有行到 List（仅适用于可放入内存的小数据集）。
         *
         * @return 解析后的对象列表
         * @throws JsonException 如果读取或解析失败
         */
        public List<T> readAll() {
            List<T> result = new ArrayList<>();
            forEach(result::add);
            return result;
        }

        /**
         * 流式迭代器实现。
         */
        private class Iterator implements java.util.Iterator<T> {
            private T next;
            private boolean finished = false;

            Iterator() throws IOException {
                advance();
            }

            private void advance() throws IOException {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        try {
                            next = YdszJson.fromJson(line, clazz);
                            return;
                        } catch (Exception e) {
                            throw new IOException("Failed to parse JSON line: " + line, e);
                        }
                    }
                }
                finished = true;
            }

            @Override
            public boolean hasNext() {
                return !finished;
            }

            @Override
            public T next() {
                if (finished) {
                    throw new java.util.NoSuchElementException();
                }
                T current = next;
                try {
                    advance();
                } catch (IOException e) {
                    throw new JsonException("Failed to advance JSON Lines iterator", e);
                }
                return current;
            }
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (IOException e) {
                throw new JsonException("Failed to close JSON Lines reader", e);
            }
        }
    }

    // ==================== 便捷工厂方法 ====================

    /**
     * 创建 JSON Lines 写入器。
     *
     * @param out  目标输出流
     * @param <T>  写入对象类型
     * @return 写入器实例
     */
    public static <T> Writer<T> writer(OutputStream out) {
        return new Writer<>(out);
    }

    /**
     * 创建 JSON Lines 写入器。
     *
     * @param out          目标输出流
     * @param flushPerLine 是否每行写入后立即 flush
     * @param <T>          写入对象类型
     * @return 写入器实例
     */
    public static <T> Writer<T> writer(OutputStream out, boolean flushPerLine) {
        return new Writer<>(out, flushPerLine);
    }

    /**
     * 创建 JSON Lines 读取器。
     *
     * @param in    输入流
     * @param clazz 目标类型
     * @param <T>   读取对象类型
     * @return 读取器实例
     */
    public static <T> Reader<T> reader(InputStream in, Class<T> clazz) {
        return new Reader<>(in, clazz);
    }

    /**
     * 从 JSON Lines 输入流创建 Stream。
     *
     * <p>使用 try-with-resources 确保流正确关闭：</p>
     * <pre>{@code
     * try (Stream<User> stream = JsonLines.stream(inputStream, User.class)) {
     *     stream.forEach(System.out::println);
     * }
     * }</pre>
     *
     * @param in    输入流
     * @param clazz 目标类型
     * @param <T>   读取对象类型
     * @return Stream 实例
     */
    public static <T> Stream<T> stream(InputStream in, Class<T> clazz) {
        Reader<T> reader = new Reader<>(in, clazz);
        return StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(reader.iterator(),
                        java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL),
                false
        ).onClose(() -> {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 将对象列表序列化为 JSON Lines 字符串。
     *
     * @param items 对象列表
     * @param <T>   对象类型
     * @return JSON Lines 格式的字符串
     */
    public static <String> java.lang.String toJsonLines(List<?> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(items.size() * 128);
        for (Object item : items) {
            sb.append(YdszJson.toJson(item)).append('\n');
        }
        return sb.toString();
    }
}
