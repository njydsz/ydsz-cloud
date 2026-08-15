package com.njydsz.common.util.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件操作工具类
 *
 * <p>封装 Apache Commons IO 的 {@link org.apache.commons.io.FileUtils} 提供便捷的文件读写、
 * 目录操作、扩展名解析等能力。所有 IO 异常均被转为安全默认值或日志记录，
 * 不向上抛出（除非方法文档显式说明）。
 *
 * <p>统一使用 UTF-8 字符编码进行文本读写。
 *
 * <p>注意：本类与 {@code org.apache.commons.io.FileUtils} 同名，类内对 Commons IO
 * 的调用一律使用全限定名，避免同名类遮蔽导致的编译冲突。
 *
 * @author ydsz-team
 * @since 4.0.0
 */
@Slf4j
public final class FileUtils {

    /** 文件扩展名分隔符 */
    private static final String EXTENSION_SEPARATOR = ".";

    /** 最后一个点号的索引基准 */
    private static final int LAST_DOT_NOT_FOUND = -1;

    /** 文件名带扩展名的最小长度（至少包含 "a.b"） */
    private static final int MIN_FILENAME_WITH_EXTENSION_LENGTH = 2;

    /** 文件大小不存在时的返回值 */
    private static final long SIZE_NOT_EXIST = -1L;

    private FileUtils() {
        throw new UnsupportedOperationException("FileUtils is a utility class and cannot be instantiated");
    }

    // ==================== 文件读写 ====================

    /**
     * 读取文件内容为 UTF-8 字符串。
     *
     * <p>如果文件不存在或读取失败，返回 null 并记录错误日志。
     *
     * @param path 文件路径，不能为 null
     * @return 文件内容字符串，读取失败返回 null
     * @throws NullPointerException 如果 path 为 null
     */
    public static String readFileToString(String path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            return org.apache.commons.io.FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read file to string: {}", path, e);
            return null;
        }
    }

    /**
     * 将字符串写入文件（UTF-8），覆盖已有内容。
     *
     * <p>如果目标文件的父目录不存在，将自动创建。写入失败时记录错误日志。
     *
     * @param path    目标文件路径，不能为 null
     * @param content 要写入的内容，不能为 null
     * @return 是否写入成功
     * @throws NullPointerException 如果任一参数为 null
     */
    public static boolean writeStringToFile(String path, String content) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try {
            org.apache.commons.io.FileUtils.writeStringToFile(new File(path), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            log.error("Failed to write string to file: {}", path, e);
            return false;
        }
    }

    /**
     * 将输入流内容复制到目标文件。
     *
     * <p>输入流在使用后会被关闭。如果写入失败，记录错误日志。
     *
     * @param is         输入流，不能为 null
     * @param targetPath 目标文件路径，不能为 null
     * @return 是否复制成功
     * @throws NullPointerException 如果任一参数为 null
     */
    public static boolean copy(InputStream is, String targetPath) {
        Objects.requireNonNull(is, "input stream must not be null");
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        try {
            org.apache.commons.io.FileUtils.copyInputStreamToFile(is, new File(targetPath));
            return true;
        } catch (IOException e) {
            log.error("Failed to copy input stream to file: {}", targetPath, e);
            return false;
        } finally {
            org.apache.commons.io.IOUtils.closeQuietly(is);
        }
    }

    // ==================== 目录操作 ====================

    /**
     * 创建目录（含不存在的父目录）。
     *
     * @param dirPath 目录路径，不能为 null
     * @return 是否创建成功（目录已存在也返回 true）
     * @throws NullPointerException 如果 dirPath 为 null
     */
    public static boolean mkdirs(String dirPath) {
        Objects.requireNonNull(dirPath, "dirPath must not be null");
        try {
            org.apache.commons.io.FileUtils.forceMkdir(new File(dirPath));
            return true;
        } catch (IOException e) {
            log.error("Failed to create directories: {}", dirPath, e);
            return false;
        }
    }

    /**
     * 静默删除文件或目录，失败不抛异常。
     *
     * @param path 文件或目录路径，不能为 null
     * @return 是否删除成功（不存在也返回 true）
     * @throws NullPointerException 如果 path 为 null
     */
    public static boolean deleteQuietly(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return org.apache.commons.io.FileUtils.deleteQuietly(new File(path));
    }

    /**
     * 类似 Unix touch：创建文件（如果不存在），或更新最后修改时间（如果已存在）。
     *
     * @param path 文件路径，不能为 null
     * @return 是否 touch 成功
     * @throws NullPointerException 如果 path 为 null
     */
    public static boolean touch(String path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            org.apache.commons.io.FileUtils.touch(new File(path));
            return true;
        } catch (IOException e) {
            log.error("Failed to touch file: {}", path, e);
            return false;
        }
    }

    // ==================== 扩展名解析 ====================

    /**
     * 获取文件扩展名（不含点号）。
     *
     * <p>例如："test.txt" 返回 "txt"，"archive.tar.gz" 返回 "gz"。
     * 无扩展名时返回空字符串。
     *
     * @param filename 文件名，不能为 null
     * @return 文件扩展名（不含点），无扩展名返回空字符串，输入为 null 返回 null
     */
    public static String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int lastDotIndex = filename.lastIndexOf(EXTENSION_SEPARATOR.charAt(0));
        if (lastDotIndex == LAST_DOT_NOT_FOUND) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 获取不含扩展名的文件名。
     *
     * <p>例如："test.txt" 返回 "test"，"archive.tar.gz" 返回 "archive.tar"。
     * 无扩展名时返回原文件名。
     *
     * @param filename 文件名
     * @return 不含扩展名的文件名，输入为 null 返回 null
     */
    public static String getFilenameWithoutExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int lastDotIndex = filename.lastIndexOf(EXTENSION_SEPARATOR.charAt(0));
        if (lastDotIndex == LAST_DOT_NOT_FOUND) {
            return filename;
        }
        return filename.substring(0, lastDotIndex);
    }

    // ==================== 目录内容判断 ====================

    /**
     * 判断目录是否为空（不包含任何文件或子目录）。
     *
     * <p>如果路径不存在或不是目录，返回 true。
     *
     * @param dirPath 目录路径，不能为 null
     * @return 目录是否为空或不存在
     * @throws NullPointerException 如果 dirPath 为 null
     */
    public static boolean isEmptyDirectory(String dirPath) {
        Objects.requireNonNull(dirPath, "dirPath must not be null");
        Path path = Paths.get(dirPath);
        if (!Files.isDirectory(path)) {
            return true;
        }
        try {
            return org.apache.commons.io.FileUtils.sizeOfDirectory(new File(dirPath)) == 0;
        } catch (Exception e) {
            log.warn("Failed to check directory emptiness: {}", dirPath, e);
            return true;
        }
    }

    // ==================== 文件大小 ====================

    /**
     * 获取文件大小（字节数）。
     *
     * @param path 文件路径，不能为 null
     * @return 文件大小（字节），文件不存在或获取失败返回 -1
     * @throws NullPointerException 如果 path 为 null
     */
    public static long sizeOf(String path) {
        Objects.requireNonNull(path, "path must not be null");
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            return SIZE_NOT_EXIST;
        }
        try {
            return org.apache.commons.io.FileUtils.sizeOf(new File(path));
        } catch (Exception e) {
            log.warn("Failed to get file size: {}", path, e);
            return SIZE_NOT_EXIST;
        }
    }
}










