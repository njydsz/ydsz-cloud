package com.njydsz.pmis.common.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * IO 工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class IOUtils {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private IOUtils() {
    }

    /**
     * 将输入流转换为字节数组
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int n;
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            return output.toByteArray();
        }
    }

    /**
     * 将输入流转换为字符串
     */
    public static String toString(InputStream input) throws IOException {
        return toString(input, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 将输入流转换为字符串
     */
    public static String toString(InputStream input, java.nio.charset.Charset charset) throws IOException {
        if (input == null) {
            return "";
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int n;
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            return output.toString(charset);
        }
    }

    /**
     * 复制输入流到输出流
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        if (input == null || output == null) {
            return 0;
        }
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 安全关闭 Closeable
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 安全关闭 AutoCloseable
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 复制文件
     */
    public static void copyFile(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 读取文件内容为字符串
     */
    public static String readFileToString(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    /**
     * 读取文件内容为字节数组
     */
    public static byte[] readFileToByteArray(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    /**
     * 将字符串写入文件
     */
    public static void writeStringToFile(File file, String data) throws IOException {
        Files.writeString(file.toPath(), data);
    }
}
