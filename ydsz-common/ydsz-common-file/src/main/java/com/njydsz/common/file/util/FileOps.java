package com.njydsz.common.file.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 文件操作统一门面（ydsz-common-file 模块内置工具类）。
 *
 * <p>提供文件名清洗、后缀提取、存储键生成、Path → MultipartFile 适配等通用方法。
 * 各业务模块的文件工具逻辑应优先使用本类，
 * 原 {@code com.njydsz.nextwiki.server.util.NextwikiFileUtils} 已于 v2.0.0 移除。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * String safe = FileOps.sanitizeFileName("../../etc/passwd");     // 返回 "____etc_passwd"
 * String suffix = FileOps.extractSuffix("photo.jpg");              // 返回 "jpg"
 * MultipartFile mf = FileOps.toMultipartFile(path, "thumb.png", "image/png");
 * String key = FileOps.generateStorageKey("user-001", "photo.jpg"); // "file/user-001/2026/08/16/{uuid}.jpg"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class FileOps {

    private FileOps() {
    }

    /** 支持缩略图生成的图片后缀 */
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
     * 提取文件后缀（小写，不含点号）。
     * <p>无点号或点号在末尾（如 "file."）视为无后缀，返回空串。
     *
     * @param filename 文件名（可为 null）
     * @return 小写后缀（不含 "."）；无后缀或入参为空时返回空串
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
     * 净化文件名：防路径穿越（/ \ ..）→ 下划线，去除非法字符，限制长度 ≤255。
     * <p>归一化后缀保留，截断时优先保留后缀；是上传/重命名的安全入口。
     *
     * @param filename 原始文件名（可为 null）
     * @return 净化后的安全文件名；入参为空时原样返回
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
     * 生成对象存储键（路径式：file/{namespace}/{yyyy/MM/dd}/{uuid}.{suffix}）。
     *
     * @param namespace       命名空间（如用户 ID、租户 ID 或业务模块标识）
     * @param originalFilename 原始文件名（仅用于提取后缀）
     * @return 存储键字符串（不含前导 "/"）
     */
    public static String generateStorageKey(String namespace, String originalFilename) {
        String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
        String uuid = IdGenerator.nextIdStr();
        String suffix = extractSuffix(originalFilename);
        return "file/" + namespace + "/" + datePath + "/" + uuid
                + (suffix.isEmpty() ? "" : "." + suffix);
    }

    /**
     * 将本地 {@link Path} 包装为 {@link MultipartFile}。
     *
     * @param filePath    本地文件路径（必须存在）
     * @param name        逻辑文件名（如 "thumb.png"）
     * @param contentType 内容类型（如 "image/png"）
     * @return MultipartFile 适配器
     * @throws IOException 读取文件大小时失败
     */
    public static MultipartFile toMultipartFile(Path filePath, String name, String contentType)
            throws IOException {
        return new PathMultipartFileImpl(filePath, name, contentType);
    }

    /**
     * Path → MultipartFile 适配器实现
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
