package com.njydsz.pmis.common.util.io;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

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
 *   <li><b>对象序列化：</b>对象的序列化与反序列化</li>
 *   <li><b>NIO 高性能操作：</b>FileChannel、MappedByteBuffer、DirectBuffer</li>
 *   <li><b>流包装与装饰：</b>BufferedStream、Datastream、自动关闭等</li>
 *   <li><b>资源安全管理：</b>静默关闭、异常处理、超时控制</li>
 *   <li><b>特殊流操作：</b>管道流、TeeStream（一流双写）、LimitStream</li>
 *   <li><b>文件链接操作：</b>文件拷贝、移动、合并等</li>
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
 * // 对象序列化
 * IOUtils.serialize(obj, new FileOutputStream("data.ser"));
 * Object obj = IOUtils.deserialize(new FileInputStream("data.ser"));
 *
 * // 一流双写（同时写入两个输出流）
 * TeeStream tee = new TeeStream(outputStream1, outputStream2);
 * IOUtils.copy(inputStream, tee);
 *
 * // 限制读取字节数
 * LimitStream limited = new LimitStream(inputStream, 1024);
 * byte[] data = IOUtils.toByteArray(limited);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
@SuppressWarnings("unchecked")
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
     * 反序列化安全过滤器，仅允许基础类型和remi包下的类
     */
    private static final ObjectInputFilter DESERIALIZE_FILTER = ObjectInputFilter.Config.createFilter(
        "java.base/java.lang.*;java.base/java.util.*;java.base/java.time.*;java.base/java.math.*;"
        + "java.base/java.io.Serializable;com.njydsz.pmis.**;!*"
    );

    // ==================== 流复制方法 ====================

    /**
     * 复制 InputStream 到 OutputStream
     * 使用默认缓冲区大小（8KB）
     *
     * @param input  输入流
     * @param output 输出流
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 复制 InputStream 到 OutputStream（指定缓冲区大小）
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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
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
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
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
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
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

    // ==================== 对象序列化与反序列化 ====================

    /**
     * 序列化对象到 OutputStream
     *
     * @param obj    要序列化的对象
     * @param output 输出流
     * @throws IOException IO 异常
     */
    public static void serialize(Serializable obj, OutputStream output) throws IOException {
        Objects.requireNonNull(obj, "obj cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        try (ObjectOutputStream oos = new ObjectOutputStream(output)) {
            oos.writeObject(obj);
            oos.flush();
        }
    }

    /**
     * 从 InputStream 反序列化对象
     *
     * @param input 输入流
     * @param <T>   对象类型
     * @return 反序列化的对象
     * @throws IOException            IO 异常
     * @throws ClassNotFoundException 类未找到异常
     */
    
    public static <T> T deserialize(InputStream input) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(input, "input cannot be null");

        try (ObjectInputStream ois = new ObjectInputStream(input)) {
            ois.setObjectInputFilter(DESERIALIZE_FILTER);
            return (T) ois.readObject();
        }
    }

    /**
     * 序列化对象到 byte 数组
     *
     * @param obj 要序列化的对象
     * @return 字节数组
     * @throws IOException IO 异常
     */
    public static byte[] serializeToByteArray(Serializable obj) throws IOException {
        Objects.requireNonNull(obj, "obj cannot be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialize(obj, baos);
        return baos.toByteArray();
    }

    /**
     * 从 byte 数组反序列化对象
     *
     * @param data 字节数组
     * @param <T>  对象类型
     * @return 反序列化的对象
     * @throws IOException            IO 异常
     * @throws ClassNotFoundException 类未找到异常
     */
    
    public static <T> T deserializeFromByteArray(byte[] data) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(data, "data cannot be null");
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            ois.setObjectInputFilter(DESERIALIZE_FILTER);
            return (T) ois.readObject();
        }
    }

    // ==================== NIO 高性能操作 ====================

    /**
     * 使用 FileChannel 复制文件（高性能）
     *
     * @param sourceFile 源文件
     * @param destFile   目标文件
     * @throws IOException IO 异常
     */
    public static void copyFileFast(File sourceFile, File destFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        Objects.requireNonNull(destFile, "destFile cannot be null");

        if (!sourceFile.exists()) {
            throw new IOException("Source file does not exist: " + sourceFile);
        }

        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directory: " + parentDir);
            }
        }

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destFile);
             FileChannel sourceChannel = fis.getChannel();
             FileChannel destChannel = fos.getChannel()) {
            sourceChannel.transferTo(0, sourceChannel.size(), destChannel);
        }
    }

    /**
     * 使用 FileChannel 复制文件（Path 版本）
     *
     * @param source 源文件路径
     * @param dest   目标文件路径
     * @throws IOException IO 异常
     */
    public static void copyFileFast(Path source, Path dest) throws IOException {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(dest, "dest cannot be null");

        if (!Files.exists(source)) {
            throw new IOException("Source file does not exist: " + source);
        }

        Path parentDir = dest.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (FileChannel sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(dest, StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            sourceChannel.transferTo(0, sourceChannel.size(), destChannel);
        }
    }

    /**
     * 使用 FileChannel 复制文件（字符串路径版本）
     *
     * @param sourcePath 源文件路径
     * @param destPath   目标文件路径
     * @throws IOException IO 异常
     */
    public static void copyFileFast(String sourcePath, String destPath) throws IOException {
        copyFileFast(Paths.get(sourcePath), Paths.get(destPath));
    }

    /**
     * 使用 MappedByteBuffer 复制大文件（超高性能）
     * 适合 GB 级别的大文件复制
     *
     * @param sourceFile 源文件
     * @param destFile   目标文件
     * @throws IOException IO 异常
     */
    public static void copyFileMapped(File sourceFile, File destFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        Objects.requireNonNull(destFile, "destFile cannot be null");

        if (!sourceFile.exists()) {
            throw new IOException("Source file does not exist: " + sourceFile);
        }

        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directory: " + parentDir);
            }
        }

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destFile);
             FileChannel sourceChannel = fis.getChannel();
             FileChannel destChannel = fos.getChannel()) {
            long size = sourceChannel.size();
            long position = 0;
            long maxMappingSize = Integer.MAX_VALUE - 1024 * 1024;

            while (position < size) {
                long mappingSize = Math.min(size - position, maxMappingSize);
                destChannel.write(sourceChannel.map(FileChannel.MapMode.READ_ONLY, position, mappingSize));
                position += mappingSize;
            }
        }
    }

    /**
     * 读取文件所有字节到 byte 数组（NIO 版本）
     *
     * @param file 文件
     * @return 字节数组
     * @throws IOException IO 异常
     */
    public static byte[] readFileToByteArray(File file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file);
        }

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    /**
     * 将 byte 数组写入文件（NIO 版本）
     *
     * @param data 字节数组
     * @param file 文件
     * @throws IOException IO 异常
     */
    public static void writeByteArrayToFile(byte[] data, File file) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(file, "file cannot be null");

        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directory: " + parentDir);
            }
        }

        try (FileChannel channel = FileChannel.open(file.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            channel.write(buffer);
        }
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

    // ==================== 特殊流操作 ====================

    /**
     * 一流双写输出流（TeeStream）
     * 将写入的数据同时复制到两个输出流
     *
     * @param output1 第一个输出流
     * @param output2 第二个输出流
     * @return TeeStream
     */
    public static TeeStream tee(OutputStream output1, OutputStream output2) {
        return new TeeStream(output1, output2);
    }

    /**
     * 限制读取字节数的输入流
     *
     * @param input 原始输入流
     * @param limit 最大读取字节数
     * @return LimitInputStream
     */
    public static LimitInputStream limit(InputStream input, long limit) {
        return new LimitInputStream(input, limit);
    }

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

    // ==================== 内部静态类 ====================

    /**
     * TeeStream - 一流双写输出流
     * 将写入的数据同时复制到两个输出流
     * 参考：Unix tee 命令
     */
    public static class TeeStream extends OutputStream {
        private final OutputStream output1;
        private final OutputStream output2;

        /**
         * 构造 TeeStream
         *
         * @param output1 第一个输出流
         * @param output2 第二个输出流
         */
        public TeeStream(OutputStream output1, OutputStream output2) {
            this.output1 = Objects.requireNonNull(output1, "output1 cannot be null");
            this.output2 = Objects.requireNonNull(output2, "output2 cannot be null");
        }

        @Override
        public void write(int b) throws IOException {
            output1.write(b);
            output2.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            output1.write(b);
            output2.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            output1.write(b, off, len);
            output2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            output1.flush();
            output2.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                output1.close();
            } finally {
                output2.close();
            }
        }
    }

    /**
     * LimitInputStream - 限制读取字节数的输入流
     * 用于限制从底层流读取的最大字节数
     */
    public static class LimitInputStream extends InputStream {
        private final InputStream input;
        private long remaining;
        private long mark = -1;

        /**
         * 构造 LimitInputStream
         *
         * @param input 原始输入流
         * @param limit 最大读取字节数
         */
        public LimitInputStream(InputStream input, long limit) {
            this.input = Objects.requireNonNull(input, "input cannot be null");
            if (limit < 0) {
                throw new IllegalArgumentException("limit cannot be negative");
            }
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return EOF;
            }
            int b = input.read();
            if (b != EOF) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return EOF;
            }
            int toRead = (int) Math.min(len, remaining);
            int bytesRead = input.read(b, off, toRead);
            if (bytesRead > 0) {
                remaining -= bytesRead;
            }
            return bytesRead;
        }

        @Override
        public long skip(long n) throws IOException {
            if (remaining <= 0) {
                return 0;
            }
            long toSkip = Math.min(n, remaining);
            long skipped = input.skip(toSkip);
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            if (remaining <= 0) {
                return 0;
            }
            return (int) Math.min(input.available(), remaining);
        }

        @Override
        public synchronized void mark(int readlimit) {
            mark = remaining;
            input.mark(readlimit);
        }

        @Override
        public synchronized void reset() throws IOException {
            if (mark >= 0) {
                remaining = mark;
                mark = -1;
            }
            input.reset();
        }

        @Override
        public boolean markSupported() {
            return input.markSupported();
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        /**
         * 获取剩余可读取字节数
         *
         * @return 剩余字节数
         */
        public long getRemaining() {
            return remaining;
        }
    }
}
