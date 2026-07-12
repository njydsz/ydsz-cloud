package com.njydsz.pmis.common.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 文件工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class FileUtils {

    private FileUtils() {
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm", "m4v", "mpg", "mpeg"
    );
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus"
    );
    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "csv", "rtf", "odt", "ods", "odp"
    );
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "html", "css", "xml", "json", "yaml", "yml", "sql", "sh", "bat", "go", "rs", "c", "cpp", "h", "kt", "swift", "rb", "php"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico",
            "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm",
            "mp3", "wav", "flac", "aac", "ogg",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "csv", "txt", "md",
            "zip", "rar", "7z", "tar", "gz",
            "java", "py", "js", "ts", "html", "css", "xml", "json", "yaml", "yml", "sql"
    );

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

    // ==================== 文件类型检测 ====================

    /**
     * 获取文件类型（扩展名）
     *
     * @param fileName 文件名
     * @return 文件扩展名（小写，不含点），如 "jpg"
     */
    public static String getFileType(String fileName) {
        return getExtension(fileName);
    }

    /**
     * 判断扩展名是否在允许列表中
     */
    public static boolean isAllowedExtension(String ext) {
        return ext != null && ALLOWED_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断是否为图片扩展名
     */
    public static boolean isImageExtension(String ext) {
        return ext != null && IMAGE_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断是否为视频扩展名
     */
    public static boolean isVideoExtension(String ext) {
        return ext != null && VIDEO_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断是否为音频扩展名
     */
    public static boolean isAudioExtension(String ext) {
        return ext != null && AUDIO_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断是否为办公文档扩展名
     */
    public static boolean isOfficeExtension(String ext) {
        return ext != null && OFFICE_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * 判断是否为代码文件扩展名
     */
    public static boolean isCodeExtension(String ext) {
        return ext != null && CODE_EXTENSIONS.contains(ext.toLowerCase());
    }
}
