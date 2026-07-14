package com.njydsz.pmis.common.json.stream;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

/**
 * 流式 JSON 生成器
 *
 * <p>对标 Jackson JsonGenerator，提供基于事件的流式写入。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * try (JsonGenerator gen = JsonGenerator.of(System.out)) {
 *     gen.writeStartObject();
 *     gen.writeName("name");
 *     gen.writeString("John");
 *     gen.writeName("age");
 *     gen.writeNumber(30);
 *     gen.writeEndObject();
 * }
 * // 输出：{"name":"John","age":30}
 * </pre>
 *
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>零分配：直接写入 Writer，无中间结构</li>
 *   <li>流式处理：适合大 JSON 输出</li>
 *   <li>内存占用：仅 O(1) 内存</li>
 * </ul>
 *
 * @since 1.3.0
 * @since 1.3.0
 */
public final class JsonGenerator implements Closeable {

    private final Writer writer;

    private final boolean prettyPrint;

    private int indentLevel = 0;

    private boolean firstElement = true;

    private boolean closed = false;

    private static final ThreadLocal<StringBuilder> SB_POOL =
        ThreadLocal.withInitial(() -> new StringBuilder(256));

    /**
     * 创建流式生成器
     *
     * @param writer 输出写入器
     * @return JsonGenerator 实例
     */
    public static JsonGenerator of(Writer writer) {
        return new JsonGenerator(writer, false);
    }

    /**
     * 创建流式生成器（格式化输出）
     *
     * @param writer 输出写入器
     * @param pretty 是否格式化输出
     * @return JsonGenerator 实例
     */
    public static JsonGenerator of(Writer writer, boolean pretty) {
        return new JsonGenerator(writer, pretty);
    }

    private JsonGenerator(Writer writer, boolean prettyPrint) {
        this.writer = writer;
        this.prettyPrint = prettyPrint;
    }

    /**
     * 写入对象起始
     *
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeStartObject() throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writeIndent();
        writer.write('{');
        indentLevel++;
        firstElement = true;
        return this;
    }

    /**
     * 写入对象结束
     *
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeEndObject() throws IOException {
        ensureOpen();
        indentLevel--;
        if (prettyPrint) {
            writer.write('\n');
            writeIndent();
        }
        writer.write('}');
        firstElement = false;
        return this;
    }

    /**
     * 写入数组起始
     *
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeStartArray() throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writeIndent();
        writer.write('[');
        indentLevel++;
        firstElement = true;
        return this;
    }

    /**
     * 写入数组结束
     *
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeEndArray() throws IOException {
        ensureOpen();
        indentLevel--;
        if (prettyPrint) {
            writer.write('\n');
            writeIndent();
        }
        writer.write(']');
        firstElement = false;
        return this;
    }

    /**
     * 写入字段名
     *
     * @param name 字段名
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeName(String name) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writeIndent();
        writer.write('"');
        writer.write(escapeString(name));
        writer.write('"');
        writer.write(':');
        if (prettyPrint) {
            writer.write(' ');
        }
        firstElement = true;
        return this;
    }

    /**
     * 写入字符串值
     *
     * @param value 字符串值
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeString(String value) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        if (value == null) {
            writeNull();
        } else {
            writer.write('"');
            writer.write(escapeString(value));
            writer.write('"');
        }
        firstElement = false;
        return this;
    }

    /**
     * 写入整数值
     *
     * @param value 整数值
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeNumber(int value) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writer.write(Integer.toString(value));
        firstElement = false;
        return this;
    }

    /**
     * 写入长整数值
     *
     * @param value 长整数值
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeNumber(long value) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writer.write(Long.toString(value));
        firstElement = false;
        return this;
    }

    /**
     * 写入浮点数值
     *
     * @param value 浮点数值
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeNumber(double value) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            writer.write("null");
        } else {
            writer.write(Double.toString(value));
        }
        firstElement = false;
        return this;
    }

    /**
     * 写入布尔值
     *
     * @param value 布尔值
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeBoolean(boolean value) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writer.write(value ? "true" : "false");
        firstElement = false;
        return this;
    }

    /**
     * 写入 null 值
     *
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeNull() throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writer.write("null");
        firstElement = false;
        return this;
    }

    /**
     * 写入原始 JSON
     *
     * @param json 原始 JSON 字符串
     * @return 当前生成器实例（链式调用）
     * @throws IOException 如果写入失败
     */
    public JsonGenerator writeRaw(String json) throws IOException {
        ensureOpen();
        writeCommaIfNeeded();
        writer.write(json);
        firstElement = false;
        return this;
    }

    /**
     * 刷新输出缓冲区
     *
     * @throws IOException 如果刷新失败
     */
    public void flush() throws IOException {
        ensureOpen();
        writer.flush();
    }

    private void writeCommaIfNeeded() throws IOException {
        if (!firstElement) {
            writer.write(',');
            if (prettyPrint) {
                writer.write('\n');
            }
        }
    }

    private void writeIndent() throws IOException {
        if (prettyPrint && indentLevel > 0) {
            writer.write('\n');
            for (int i = 0; i < indentLevel; i++) {
                writer.write("  ");
            }
        }
    }

    private String escapeString(String str) {
        StringBuilder sb = SB_POOL.get();
        sb.setLength(0);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Generator is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            writer.flush();
            writer.close();
        }
    }
}
