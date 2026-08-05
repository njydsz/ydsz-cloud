package com.remisoft.common.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * 文件操作工具类。
 *
 * <p>提供文件复制、读写、高性能 NIO 传输等操作，是 {@link IOUtils} 的文件操作专本子集。
 * 从 IOUtils 1.3.0 版本起拆分为独立类，避免 IOUtils 臃肿。
 *
 * <p><b>线程安全：</b>所有方法均为无状态纯函数式调用，线程安全。
 *
 * <pre>{@code
 * // 快速复制文件（FileChannel.transferTo，零拷贝）
 * FileUtils.copyFileFast(new File("src.txt"), new File("dest.txt"));
 *
 * // 读取整个文件为 byte[]
 * byte[] data = FileUtils.readFileToByteArray(new File("data.bin"));
 * }</pre>
 *
 * @author remi-team
 * @since 1.3.0
 */
public class FileUtils {

    /** 文件拷贝默认缓冲区大小（64KB） */
    public static final int COPY_BUFFER_SIZE = 65536;

    /** MappedByteBuffer 单次映射上限（约 2GB - 1MB），避免 Integer.MAX_VALUE 溢出 */
    private static final long MAX_MAPPING_SIZE = Integer.MAX_VALUE - 1024L * 1024L;

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private FileUtils() {
        throw new UnsupportedOperationException("FileUtils is a utility class and cannot be instantiated");
    }

    /**
     * 使用 FileChannel 复制文件（高性能）。
     *
     * <p>底层使用 {@code FileChannel.transferTo()}，走零拷贝路径，
     * 比 {@code byte[]} 缓冲循环复制节省一次用户态-内核态数据拷贝。
     *
     * @param sourceFile 源文件，必须存在
     * @param destFile   目标文件
     * @throws IOException 源文件不存在或 IO 异常
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
     * 使用 FileChannel 复制文件（Path 版本）。
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
     * 使用 FileChannel 复制文件（字符串路径版本）。
     *
     * @param sourcePath 源文件路径
     * @param destPath   目标文件路径
     * @throws IOException IO 异常
     */
    public static void copyFileFast(String sourcePath, String destPath) throws IOException {
        copyFileFast(Paths.get(sourcePath), Paths.get(destPath));
    }

    /**
     * 使用 MappedByteBuffer 复制大文件（超高性能）。
     *
     * <p>适合 GB 级别大文件复制。文件内容通过内存映射直接传输，
     * 减少一次内核态到用户态的数据拷贝。
     *
     * <p><b>已知限制：</b>MappedByteBuffer 由 GC 回收，JDK 无公开 API 主动 unmap。
     * 在 Windows 下映射区域可能锁定源文件直至 GC 回收，导致文件无法立即删除/移动。
     * 如需立即释放，建议改用 {@link #copyFileFast(File, File)}。
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

            while (position < size) {
                long mappingSize = Math.min(size - position, MAX_MAPPING_SIZE);
                destChannel.write(sourceChannel.map(FileChannel.MapMode.READ_ONLY, position, mappingSize));
                position += mappingSize;
            }
        }
    }

    /**
     * 读取文件所有字节到 byte 数组（NIO 版本）。
     *
     * @param file 文件对象
     * @return 字节数组
     * @throws IOException      文件不存在或 IO 异常
     * @throws IllegalArgumentException 文件大于 2GB
     */
    public static byte[] readFileToByteArray(File file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file);
        }

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("文件过大，超过 2GB 限制: " + file);
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            channel.read(buffer);
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    /**
     * 将 byte 数组写入文件（NIO 版本）。
     *
     * <p>若父目录不存在会自动创建。
     *
     * @param data 字节数组
     * @param file 文件对象
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
}
