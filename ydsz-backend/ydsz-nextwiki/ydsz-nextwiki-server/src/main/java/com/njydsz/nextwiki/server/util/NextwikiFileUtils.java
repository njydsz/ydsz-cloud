package com.njydsz.nextwiki.server.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;

/**
 * NextWiki 文件工具类（P1-R1 + P1-R2）
 * <p>
 * 消除跨 5 个 Service 重复的工具方法和 MultipartFile 适配器实现。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public final class NextwikiFileUtils {

    private NextwikiFileUtils() {
    }

    /** 支持缩略图生成的图片后缀（统一版本，含 svg） */
    public static final Set<String> IMAGE_SUFFIXES = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    );

    /** 直接可读取文本内容的文件后缀 */
    public static final Set<String> TEXT_SUFFIXES = Set.of(
            "txt", "md", "csv", "json", "xml", "html", "htm", "log", "properties", "yml", "yaml", "sql"
    );

    /** 支持 Office 预览的文件后缀 */
    public static final Set<String> OFFICE_SUFFIXES = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf"
    );

    /** 支持直接预览的文件后缀（无需转换） */
    public static final Set<String> DIRECT_PREVIEW_SUFFIXES = Set.of(
            "pdf", "txt", "md", "html", "htm", "csv", "json", "xml"
    );

    /**
     * 解析存储实例
     */
    public static IFileStorage resolveStorage(IFileStorageProvider provider) {
        if (provider != null) {
            return provider.getStorage();
        }
        return null;
    }

    /**
     * 提取文件后缀（小写，不含点号）
     */
    public static String extractSuffix(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    /**
     * 净化文件名：去除路径穿越字符、特殊字符、超长名称
     */
    public static String sanitizeFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        String name = filename;
        name = name.replace("/", "_").replace("\\", "_");
        name = name.replace("..", "_");
        name = name.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9._\\- ()（）]", "_");
        if (name.length() > 255) {
            String suffix = extractSuffix(name);
            String baseName = suffix.isEmpty()
                    ? name : name.substring(0, name.length() - suffix.length() - 1);
            name = baseName.substring(0, Math.min(baseName.length(), 255 - suffix.length() - 1))
                    + "." + suffix;
        }
        return name;
    }

    /**
     * 生成存储键
     */
    public static String generateStorageKey(String userId, String originalFilename) {
        String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String suffix = extractSuffix(originalFilename);
        return "wiki/" + userId + "/" + datePath + "/" + uuid
                + (suffix.isEmpty() ? "" : "." + suffix);
    }

    /**
     * 将 Path 包装为 MultipartFile（P1-R1: 消除 4 份重复实现）
     */
    public static MultipartFile toMultipartFile(Path filePath, String name, String contentType)
            throws IOException {
        return new PathMultipartFileImpl(filePath, name, contentType);
    }

    /**
     * 统一的 Path → MultipartFile 适配器实现
     */
    private static class PathMultipartFileImpl implements MultipartFile {
        private final Path filePath;
        private final String name;
        private final String contentType;
        private final long size;

        PathMultipartFileImpl(Path filePath, String name, String contentType) throws IOException {
            this.filePath = filePath;
            this.name = name;
            this.contentType = contentType;
            this.size = Files.size(filePath);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            Path fileName = filePath.getFileName();
            return fileName != null ? fileName.toString() : name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(filePath);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(filePath);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(filePath, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
