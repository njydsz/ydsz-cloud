package com.njydsz.pmis.common.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 判断文件是否存在
     */
    public static boolean exists(String path) {
        return path != null && Files.exists(Paths.get(path));
    }

    /**
     * 判断文件是否存在
     */
    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    /**
     * 创建目录（如不存在）
     */
    public static void mkdirs(String path) {
        if (path != null) {
            new File(path).mkdirs();
        }
    }

    /**
     * 创建目录（如不存在）
     */
    public static void mkdirs(File file) {
        if (file != null && !file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 删除文件
     */
    public static void deleteQuietly(String path) {
        if (path != null) {
            try {
                Files.deleteIfExists(Paths.get(path));
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 删除文件
     */
    public static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 获取文件扩展名
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public static String getBaseName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastDot < 0 || lastDot < lastSlash) {
            return fileName.substring(lastSlash + 1);
        }
        return fileName.substring(lastSlash + 1, lastDot);
    }

    /**
     * 获取文件大小（字节）
     */
    public static long sizeOf(String path) {
        if (!exists(path)) {
            return 0;
        }
        try {
            return Files.size(Paths.get(path));
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        }
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    /**
     * 递归删除目录
     */
    public static void deleteDirectory(File directory) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }
        if (directory.isDirectory()) {
            File[] children = directory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        Files.delete(directory.toPath());
    }

    /**
     * 临时文件创建
     */
    public static File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix);
    }
}
