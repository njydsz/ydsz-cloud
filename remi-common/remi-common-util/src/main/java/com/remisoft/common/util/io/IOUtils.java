package com.remisoft.common.util.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>IO 工具类 - 增强版（零第三方依赖）</p>
 *
 * <p>参考 Apache Commons IO、Google Guava、Spring Framework、Hutool 等互联网大厂工具类设计，
 * 提供全面、高性能的 IO 操作方法。</p>
 *
 * <p><b>主要功能模块：</b></p>
 * <ul>
 *   <li><b>流复制与转换：</b>支持 InputStream/OutputStream/Reader/Writer 之间的高效复制</li>
 *   <li><b>字节数组操作：</b>字节数组与流的相互转换、读取</li>
 *   <li><b>字符串操作：</b>字符串与流的相互转换、编码处理</li>
 *   <li><b>NIO 高性能操作：</b>FileChannel、MappedByteBuffer、DirectBuffer</li>
 *   <li><b>流包装与装饰：</b>BufferedStream、Datastream、自动关闭等</li>
 *   <li><b>资源安全管理：</b>静默关闭、异常处理、超时控制</li>
 *   <li><b>特殊流操作：</b>管道流、TeeStream（一流双写）、LimitStream（自 1.3.0 起拆分至独立类）</li>
 * </ul>
 *
 * <p><b>拆分说明（1.3.0）：</b></p>
 * <ul>
 *   <li>文件操作（copyFileFast、copyFileMapped、readFileToByteArray、writeByteArrayToFile）
 *       已拆分至 {@link FileUtils}</li>
 *   <li>TeeStream 已提取为独立类 {@link TeeOutputStream}</li>
 *   <li>LimitStream 已提取为独立类 {@link LimitInputStream}</li>
 *   <li>对象序列化方法已移除，序列化请使用 {@code com.remisoft.common.json.RemiJson}</li>
 * </ul>
 *
 * <p><b>相比 Apache Commons IO / Hutool 的增强：</b></p>
 * <ul>
 *   <li>零第三方依赖，纯 JDK 实现</li>
 *   <li>支持 NIO.2 API（Files、Path、FileChannel）</li>
 *   <li>提供 DirectBuffer 高性能操作</li>
 *   <li>支持一流双写（TeeStream）</li>
 *   <li>支持流内容限制（LimitStream）</li>
 *   <li>更完善的异常处理和资源管理</li>
 *   <li>完整的 JavaDoc 文档和使用示例</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 复制流
 * InputStream is = new FileInputStream("source.txt");
 * OutputStream os = new FileOutputStream("dest.txt");
 * IOUtils.copy(is, os);
 *
 * // 高性能复制（NIO）
 * IOUtils.copyFast(new FileInputStream("source.txt"),
 *                  new FileOutputStream("dest.txt"));
 *
 * // 读取流为字符串
 * String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
 *
 * // 写入字符串到流
 * IOUtils.write("Hello World", outputStream, StandardCharsets.UTF_8);
 *
 * // 一流双写（同时写入两个输出流）
 * TeeOutputStream tee = new TeeOutputStream(outputStream1, outputStream2);
 * IOUtils.copy(inputStream, tee);
 *
 * // 限制读取字节数
 * LimitInputStream limited = new LimitInputStream(inputStream, 1024);
 * byte[] data = IOUtils.toByteArray(limited);
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class IOUtils {

    private IOUtils() {
        throw new UnsupportedOperationException("IOUtils is a utility class and cannot be instantiated");
    }

    /**
     * 默认缓冲区大小（8KB）
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * 文件拷贝默认缓冲区大小（64KB）
     */
    public static final int COPY_BUFFER_SIZE = 65536;

    /**
     * 表示文件结束的常量
     */
    public static final int EOF = -1;

    /**
     * 空 byte 数组
     */
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * 缓冲区池（ThreadLocal 复用，避免每次 copy 热路径分配新数组）。
     *
     * <p>同一线程内 copy 调用复用同一缓冲区，减少 GC 压力。缓冲区大小固定为
     * {@link #DEFAULT_BUFFER_SIZE}（8KB），平衡内存占用与吞吐量。
     *
     * <p><b>虚拟线程兼容说明：</b>
     * 在 JDK 21+ 虚拟线程场景下，每个虚拟线程都拥有独立的 ThreadLocal 条目。
     * {@link #acquireBuffer()} 方法检测当前是否为虚拟线程：
     * <ul>
     *   <li>平台线程：使用 ThreadLocal 池化复用（零分配）</li>
     *   <li>虚拟线程：每次分配新数组（避免百万级虚拟线程导致 OOM），不缓存</li>
     * </ul>
     */
    private static final ThreadLocal<byte[]> BUFFER_POOL = ThreadLocal.withInitial(() -> new byte[DEFAULT_BUFFER_SIZE]);

    /**
     * 标记是否已检测过虚拟线程支持（用于热点路径性能优化）。
     */
    private static final boolean IS_VIRTUAL_THREAD_SUPPORTED;

    static {
        boolean vtSupported = false;
        try {
            Thread.ofVirtual();
            vtSupported = true;
        } catch (Exception | Error ignored) {
            // 当前 JVM 不支持虚拟线程
        }
        IS_VIRTUAL_THREAD_SUPPORTED = vtSupported;
    }

    /**
     * 获取 IO 缓冲区。
     *
     * <p>平台线程从 ThreadLocal 缓冲池获取（零分配）；
     * 虚拟线程每次分配新数组，避免虚拟线程数过多时 ThreadLocal 条目膨胀导致 OOM。
     *
     * @return 大小为 {@link #DEFAULT_BUFFER_SIZE} 的字节数组
     */
    private static byte[] acquireBuffer() {
        if (IS_VIRTUAL_THREAD_SUPPORTED && Thread.currentThread().isVirtual()) {
            return new byte[DEFAULT_BUFFER_SIZE];
        }
        return BUFFER_POOL.get();
    }

    // ==================== 流复制方法 ====================

    /**
     * 复制 InputStream 到 OutputStream。
     *
     * <p>使用 {@link #DEFAULT_BUFFER_SIZE}（8KB）缓冲区，通过 {@link ThreadLocal} 缓冲区池复用，
     * 减少每次调用分配新数组的 GC 开销。
     *
     * @param input  输入流
     * @param output 输出流
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        byte[] buffer = acquireBuffer();
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != EOF) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 复制 InputStream 到 OutputStream（指定缓冲区大小）。
     *
     * <p>注意：指定非默认缓冲区大小不会使用缓冲区池，每次调用会分配新数组。
     *
     * @param input      输入流
     * @param output     输出流
     * @param bufferSize 缓冲区大小
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize 必须 > 0");
        }

        byte[] buffer = new byte[bufferSize];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != EOF) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 高性能复制 InputStream 到 OutputStream（使用 NIO）
     * 使用 DirectByteBuffer 和 Channel，适合大文件传输
     *
     * @param input  输入流
     * @param output 输出流
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copyFast(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        ReadableByteChannel inputChannel = Channels.newChannel(input);
        WritableByteChannel outputChannel = Channels.newChannel(output);

        ByteBuffer buffer = ByteBuffer.allocateDirect(COPY_BUFFER_SIZE);
        long count = 0;
        int n;
        while ((n = inputChannel.read(buffer)) != EOF) {
            buffer.flip();
            outputChannel.write(buffer);
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }
            buffer.clear();
            count += n;
        }
        return count;
    }

    /**
     * 复制 Reader 到 Writer
     *
     * @param input  输入 Reader
     * @param output 输出 Writer
     * @return 复制的字符数
     * @throws IOException IO 异常
     */
    public static long copy(Reader input, Writer output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 复制 Reader 到 Writer（指定缓冲区大小）
     *
     * @param input      输入 Reader
     * @param output     输出 Writer
     * @param bufferSize 缓冲区大小
     * @return 复制的字符数
     * @throws IOException IO 异常
     */
    public static long copy(Reader input, Writer output, int bufferSize) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize 必须 > 0");
        }

        char[] buffer = new char[bufferSize];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != EOF) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 复制 Reader 到 OutputStream（自动编码转换）
     *
     * @param input    输入 Reader
     * @param output   输出流
     * @param encoding 字符编码
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copy(Reader input, OutputStream output, Charset encoding) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        Writer writer = new OutputStreamWriter(output, encoding);
        long count = copy(input, writer);
        writer.flush();
        return count;
    }

    /**
     * 复制 InputStream 到 Writer（自动编码转换）
     *
     * @param input    输入流
     * @param output   输出 Writer
     * @param encoding 字符编码
     * @return 复制的字符数
     * @throws IOException IO 异常
     */
    public static long copy(InputStream input, Writer output, Charset encoding) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        Reader reader = new InputStreamReader(input, encoding);
        return copy(reader, output);
    }

    // ==================== 字节数组与流转换 ====================

    /**
     * 读取 InputStream 的所有字节到 byte 数组
     *
     * @param input 输入流
     * @return 字节数组
     * @throws IOException IO 异常
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        ByteArrayOutputStream output = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
        copy(input, output);
        return output.toByteArray();
    }

    /**
     * 读取 InputStream 的指定字节数到 byte 数组
     *
     * @param input  输入流
     * @param length 要读取的字节数
     * @return 字节数组
     * @throws IOException IO 异常
     */
    public static byte[] toByteArray(InputStream input, int length) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        if (length <= 0) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] buffer = new byte[length];
        int bytesRead = 0;
        while (bytesRead < length) {
            int read = input.read(buffer, bytesRead, length - bytesRead);
            if (read == EOF) {
                if (bytesRead == 0) {
                    return EMPTY_BYTE_ARRAY;
                }
                return Arrays.copyOf(buffer, bytesRead);
            }
            bytesRead += read;
        }
        return buffer;
    }

    /**
     * 将 byte 数组写入 OutputStream
     *
     * @param data   字节数组
     * @param output 输出流
     * @throws IOException IO 异常
     */
    public static void write(byte[] data, OutputStream output) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        output.write(data);
    }

    /**
     * 将 byte 数组的一部分写入 OutputStream
     *
     * @param data   字节数组
     * @param offset 起始偏移量
     * @param length 长度
     * @param output 输出流
     * @throws IOException IO 异常
     */
    public static void write(byte[] data, int offset, int length, OutputStream output) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        output.write(data, offset, length);
    }

    /**
     * 将 InputStream 转换为 ByteBuffer
     *
     * @param input 输入流
     * @return ByteBuffer
     * @throws IOException IO 异常
     */
    public static ByteBuffer toByteBuffer(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        byte[] bytes = toByteArray(input);
        return ByteBuffer.wrap(bytes);
    }

    /**
     * 将 ByteBuffer 转换为 byte 数组
     *
     * @param buffer ByteBuffer
     * @return 字节数组
     */
    public static byte[] toByteArray(ByteBuffer buffer) {
        if (buffer == null) {
            return EMPTY_BYTE_ARRAY;
        }
        // 不破坏性修改入参 buffer 的 position/limit 状态，使用 duplicate 读取剩余字节
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    /**
     * 将 ByteBuffer 写入 OutputStream
     *
     * @param buffer ByteBuffer
     * @param output 输出流
     * @throws IOException IO 异常
     */
    public static void write(ByteBuffer buffer, OutputStream output) throws IOException {
        Objects.requireNonNull(buffer, "buffer cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        // 不破坏性修改入参 buffer 的 position/limit 状态，使用 duplicate 读取剩余字节
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        output.write(bytes);
    }

    // ==================== 字符串与流转换 ====================

    /**
     * 读取 InputStream 为字符串（UTF-8 编码）
     *
     * @param input 输入流
     * @return 字符串
     * @throws IOException IO 异常
     */
    public static String toString(InputStream input) throws IOException {
        return toString(input, StandardCharsets.UTF_8);
    }

    /**
     * 读取 InputStream 为字符串（指定编码）
     *
     * @param input    输入流
     * @param encoding 字符编码
     * @return 字符串
     * @throws IOException IO 异常
     */
    public static String toString(InputStream input, Charset encoding) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(encoding, "encoding cannot be null");

        Reader reader = new InputStreamReader(input, encoding);
        CharArrayWriter writer = new CharArrayWriter(DEFAULT_BUFFER_SIZE);
        copy(reader, writer);
        return writer.toString();
    }

    /**
     * 将字符串写入 OutputStream（UTF-8 编码）
     *
     * @param data   字符串
     * @param output 输出流
     * @throws IOException IO 异常
     */
    public static void write(String data, OutputStream output) throws IOException {
        write(data, output, StandardCharsets.UTF_8);
    }

    /**
     * 将字符串写入 OutputStream（指定编码）
     *
     * @param data     字符串
     * @param output   输出流
     * @param encoding 字符编码
     * @throws IOException IO 异常
     */
    public static void write(String data, OutputStream output, Charset encoding) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        Objects.requireNonNull(encoding, "encoding cannot be null");

        Writer writer = new OutputStreamWriter(output, encoding);
        writer.write(data);
        writer.flush();
    }

    /**
     * 将字符串转换为 InputStream（UTF-8 编码）
     *
     * @param data 字符串
     * @return InputStream
     */
    public static InputStream toInputStream(String data) {
        return toInputStream(data, StandardCharsets.UTF_8);
    }

    /**
     * 将字符串转换为 InputStream（指定编码）
     *
     * @param data     字符串
     * @param encoding 字符编码
     * @return InputStream
     */
    public static InputStream toInputStream(String data, Charset encoding) {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(encoding, "encoding cannot be null");
        byte[] bytes = data.getBytes(encoding);
        return new ByteArrayInputStream(bytes);
    }

    /**
     * 读取 Reader 为字符串
     *
     * @param input 输入 Reader
     * @return 字符串
     * @throws IOException IO 异常
     */
    public static String toString(Reader input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        CharArrayWriter writer = new CharArrayWriter(DEFAULT_BUFFER_SIZE);
        copy(input, writer);
        return writer.toString();
    }

    // ==================== 流包装与装饰 ====================

    /**
     * 创建 BufferedInputStream（使用默认缓冲区大小）
     *
     * @param input InputStream
     * @return BufferedInputStream
     */
    public static BufferedInputStream toBufferedInputStream(InputStream input) {
        Objects.requireNonNull(input, "input cannot be null");
        return input instanceof BufferedInputStream ? (BufferedInputStream) input
                : new BufferedInputStream(input);
    }

    /**
     * 创建 BufferedInputStream（指定缓冲区大小）
     *
     * @param input      InputStream
     * @param bufferSize 缓冲区大小
     * @return BufferedInputStream
     */
    public static BufferedInputStream toBufferedInputStream(InputStream input, int bufferSize) {
        Objects.requireNonNull(input, "input cannot be null");
        return input instanceof BufferedInputStream ? (BufferedInputStream) input
                : new BufferedInputStream(input, bufferSize);
    }

    /**
     * 创建 BufferedOutputStream（使用默认缓冲区大小）
     *
     * @param output OutputStream
     * @return BufferedOutputStream
     */
    public static BufferedOutputStream toBufferedOutputStream(OutputStream output) {
        Objects.requireNonNull(output, "output cannot be null");
        return output instanceof BufferedOutputStream ? (BufferedOutputStream) output
                : new BufferedOutputStream(output);
    }

    /**
     * 创建 BufferedOutputStream（指定缓冲区大小）
     *
     * @param output     OutputStream
     * @param bufferSize 缓冲区大小
     * @return BufferedOutputStream
     */
    public static BufferedOutputStream toBufferedOutputStream(OutputStream output, int bufferSize) {
        Objects.requireNonNull(output, "output cannot be null");
        return output instanceof BufferedOutputStream ? (BufferedOutputStream) output
                : new BufferedOutputStream(output, bufferSize);
    }

    /**
     * 创建 DataInputStream
     *
     * @param input InputStream
     * @return DataInputStream
     */
    public static DataInputStream toDataInputStream(InputStream input) {
        Objects.requireNonNull(input, "input cannot be null");
        return input instanceof DataInputStream ? (DataInputStream) input
                : new DataInputStream(input);
    }

    /**
     * 创建 DataOutputStream
     *
     * @param output OutputStream
     * @return DataOutputStream
     */
    public static DataOutputStream toDataOutputStream(OutputStream output) {
        Objects.requireNonNull(output, "output cannot be null");
        return output instanceof DataOutputStream ? (DataOutputStream) output
                : new DataOutputStream(output);
    }

    // ==================== 资源安全管理 ====================

    /**
     * 静默关闭 Closeable（不抛出异常）
     *
     * @param closeable 要关闭的资源
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Exception during close: {}", e.getMessage());
            }
        }
    }

    /**
     * 静默关闭多个 Closeable（不抛出异常）
     *
     * @param closeables 要关闭的资源数组
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        if (closeables == null || closeables.length == 0) {
            return;
        }
        for (AutoCloseable closeable : closeables) {
            closeQuietly(closeable);
        }
    }

    /**
     * 静默刷新 Flushable（不抛出异常）
     *
     * @param flushable 要刷新的资源
     */
    public static void flushQuietly(Flushable flushable) {
        if (flushable != null) {
            try {
                flushable.flush();
            } catch (IOException e) {
                log.debug("Exception during flush: {}", e.getMessage());
            }
        }
    }

    /**
     * 关闭 InputStream 并记录日志
     *
     * @param input 要关闭的流
     * @param label 日志标签（用于标识流的用途）
     */
    public static void close(InputStream input, String label) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException e) {
                log.warn("Failed to close input stream [{}]: {}", label, e.getMessage());
            }
        }
    }

    /**
     * 关闭 OutputStream 并记录日志
     *
     * @param output 要关闭的流
     * @param label  日志标签
     */
    public static void close(OutputStream output, String label) {
        if (output != null) {
            try {
                output.close();
            } catch (IOException e) {
                log.warn("Failed to close output stream [{}]: {}", label, e.getMessage());
            }
        }
    }

    // ==================== 特殊流操作（已拆分至独立类） ====================

    /**
     * 创建管道流对
     *
     * @return PipedStreamPair（包含 PipedInputStream 和 PipedOutputStream）
     * @throws IOException IO 异常
     */
    public static PipedStreamPair createPipedStream() throws IOException {
        PipedInputStream pipedInput = new PipedInputStream(DEFAULT_BUFFER_SIZE);
        PipedOutputStream pipedOutput = new PipedOutputStream(pipedInput);
        return new PipedStreamPair(pipedInput, pipedOutput);
    }

    /**
     * 管道流对（包含 PipedInputStream 和 PipedOutputStream）
     */
    public static class PipedStreamPair {
        private final PipedInputStream input;
        private final PipedOutputStream output;

        public PipedStreamPair(PipedInputStream input, PipedOutputStream output) {
            this.input = input;
            this.output = output;
        }

        public PipedInputStream getInput() {
            return input;
        }

        public PipedOutputStream getOutput() {
            return output;
        }
    }

}
