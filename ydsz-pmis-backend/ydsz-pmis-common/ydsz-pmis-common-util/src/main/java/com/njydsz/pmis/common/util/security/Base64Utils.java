package com.njydsz.pmis.common.util.security;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Base64 编码/解码工具类
 *
 * <p>提供 Base64 标准编码、URL 安全编码、MIME 编码等多种编码方式，
 * 以及文件与 Base64 字符串之间的相互转换。</p>
 *
 * <p><b>主要特性：</b>
 * <ul>
 *   <li>支持标准 Base64 编码/解码</li>
 *   <li>支持 URL 安全的 Base64 编码/解码（无填充）</li>
 *   <li>支持 MIME 格式的 Base64 编码/解码</li>
 *   <li>支持文件与 Base64 字符串互转（带大小限制和路径安全检查）</li>
 *   <li>提供编码/解码长度计算工具</li>
 *   <li>所有方法均进行 null 安全处理</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 标准编码
 * String encoded = Base64Utils.encode("Hello World");
 * String decoded = Base64Utils.decode(encoded);
 *
 * // URL 安全编码
 * String urlSafe = Base64Utils.encodeUrlSafe("data?param=value");
 *
 * // 文件转 Base64
 * String base64 = Base64Utils.convertFileToBase64("/path/to/file.txt");
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see Base64
 */
public class Base64Utils {

    /**
     * 文件处理最大限制（50MB）
     */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private Base64Utils() {
        throw new UnsupportedOperationException("Base64Utils is a utility class and cannot be instantiated");
    }

    /**
     * Base64 标准解码（字符串）
     *
     * @param data 待解码的 Base64 字符串
     * @return 解码后的字符串，输入为 null 或空时返回 null
     * @throws IllegalArgumentException 当 Base64 格式不正确时
     */
    public static String decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 decode failed: " + e.getMessage(), e);
        }
    }

    /**
     * Base64 标准编码（字符串）
     *
     * @param data 待编码的字符串
     * @return Base64 编码后的字符串，输入为 null 时返回 null
     */
    public static String encode(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64 标准编码（字节数组）
     *
     * @param bytes 待编码的字节数组
     * @return Base64 编码后的字符串，输入为 null 时返回 null
     */
    public static String encode(byte[] bytes) {
        return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64 解码为字节数组
     *
     * @param data 待解码的 Base64 字符串
     * @return 解码后的字节数组，输入为 null 或空时返回 null
     * @throws IllegalArgumentException 当 Base64 格式不正确时
     */
    public static byte[] decodeToBytes(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 decode failed: " + e.getMessage(), e);
        }
    }

    /**
     * 将文件内容转换为 Base64 字符串
     *
     * @param filePath 文件路径
     * @return Base64 编码字符串，文件超过 50MB 时抛出异常
     * @throws IllegalStateException 当文件大小超过限制时
     * @throws RuntimeException 当文件读取失败时
     */
    public static String convertFileToBase64(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        try {
            Path path = Paths.get(filePath);
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                throw new IllegalStateException("File size " + fileSize + " exceeds limit " + MAX_FILE_SIZE + " bytes for path " + filePath);
            }
            byte[] data = Files.readAllBytes(path);
            return encode(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert file to Base64 for path " + filePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * 将 Base64 字符串转换为文件
     *
     * @param fileBase64String Base64 编码的文件内容
     * @param filePath 目标文件目录
     * @param fileName 目标文件名
     * @return 转换后的文件对象
     * @throws SecurityException 当检测到路径遍历攻击时
     * @throws RuntimeException 当文件写入失败时
     */
    public static File convertBase64ToFile(String fileBase64String, String filePath, String fileName) {
        if (fileBase64String == null || filePath == null || fileName == null) {
            return null;
        }
        try {
            byte[] fileBytes = Base64.getDecoder().decode(fileBase64String);
            Path baseDir = Paths.get(filePath).toAbsolutePath().normalize();
            Path targetPath = baseDir.resolve(fileName).normalize();
            if (!targetPath.startsWith(baseDir)) {
                throw new SecurityException("Path traversal detected: target path is outside the specified directory");
            }
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, fileBytes);
            return targetPath.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert Base64 to file: " + e.getMessage(), e);
        }
    }

    /**
     * URL 安全的 Base64 编码（无填充）
     *
     * @param data 待编码的字符串
     * @return URL 安全的 Base64 编码字符串，输入为 null 时返回 null
     */
    public static String encodeUrlSafe(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeUrlSafe(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 URL-safe decode failed: " + e.getMessage(), e);
        }
    }

    public static String encodeMime(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return Base64.getMimeEncoder().encodeToString(bytes);
    }

    public static byte[] decodeMime(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return Base64.getMimeDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 MIME decode failed: " + e.getMessage(), e);
        }
    }

    public static boolean isValidBase64(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(data);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static int getEncodedLength(int originalLength) {
        if (originalLength < 0) {
            throw new IllegalArgumentException("Invalid input length: " + originalLength);
        }
        return ((originalLength + 2) / 3) * 4;
    }

    public static int getDecodedLength(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return 0;
        }
        int paddingCount = 0;
        if (base64Data.endsWith("==")) {
            paddingCount = 2;
        } else if (base64Data.endsWith("=")) {
            paddingCount = 1;
        }
        return (base64Data.length() * 3 / 4) - paddingCount;
    }
}
