package com.remisoft.nextwiki.server.util;

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

import com.remisoft.common.file.storage.IFileStorage;
import com.remisoft.common.file.storage.IFileStorageProvider;

/**
 * NextWiki 文件工具类 — 跨 Service 共享的文件操作工具方法集合
 *
 * <p>消除跨 5 个 Service（FileApplicationService / ChunkUploadApplicationService /
 * PreviewApplicationService / ContentExtractionApplicationService / DownloadApplicationService）
 * 重复的工具方法和 MultipartFile 适配器实现。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>文件后缀分类：{@code IMAGE_SUFFIXES} / {@code TEXT_SUFFIXES} / {@code OFFICE_SUFFIXES} 统一定义</li>
 *   <li>存储路径解析：根据文件名生成存储 key（UUID + 后缀），避免冲突</li>
 *   <li>文件名清洗：移除非法字符、路径穿越（{@code ../}）等安全风险</li>
 *   <li>MultipartFile 适配器：{@code PathMultipartFileImpl} 将本地 Path 包装为 MultipartFile 接口</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
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
     * 解析存储实例（从可选 provider 获取，未配置返回 {@code null}）。
     *
     * @param provider 文件存储 provider（可为 {@code null}）
     * @return 存储实例 {@link IFileStorage}；provider 为 {@code null} 时返回 {@code null}
     * @note 无副作用；调用方需判空
     */
    public static IFileStorage resolveStorage(IFileStorageProvider provider) {
        if (provider != null) {
            return provider.getStorage();
        }
        return null;
    }

    /**
     * 提取文件后缀（小写，不含点号）。
     * <p>无点号或点号在末尾（如 "file."）视为无后缀，返回空串。
     *
     * @param filename 文件名（可为 {@code null}）
     * @return 小写后缀（不含 "."）；无后缀或入参为空时返回空串
     * @note 纯字符串处理，线程安全
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
     * 净化文件名：防路径穿越（{@code /}、{@code \\}、{@code ..}）→ 下划线，去除非法字符，限制长度 ≤255。
     * <p>归一化后缀保留，截断时优先保留后缀；是上传/重命名的安全入口，防止目录穿越与非法文件名。
     *
     * @param filename 原始文件名（可为 {@code null}）
     * @return 净化后的安全文件名；入参为空时原样返回
     * @note 线程安全；仅做字符归一化，不改变文件内容
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
     * 生成对象存储键（路径式：{@code wiki/{userId}/{yyyy/MM/dd}/{uuid}.{suffix}}）。
     * <p>UUID 保证全局唯一，避免同名文件互相覆盖；按日期分目录便于生命周期管理。
     *
     * @param userId          用户 ID（作为存储命名空间一级目录）
     * @param originalFilename 原始文件名（仅用于提取后缀）
     * @return 存储键字符串（不含前导 "/"）
     * @note 线程安全；后缀为空时不带点号
     */
    public static String generateStorageKey(String userId, String originalFilename) {
        String datePath = LocalDateTime.now().toString().substring(0, 10).replace("-", "/");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String suffix = extractSuffix(originalFilename);
        return "wiki/" + userId + "/" + datePath + "/" + uuid
                + (suffix.isEmpty() ? "" : "." + suffix);
    }

    /**
     * 将本地 {@link Path} 包装为 {@link MultipartFile}（P1-R1：统一消除多份重复实现）。
     * <p>用于把本地临时文件/分片传给需要 MultipartFile 的存储上传接口，避免重复造轮子。
     *
     * @param filePath    本地文件路径（必须存在）
     * @param name        逻辑文件名（如 {@code fileNodeId_thumb.png}）
     * @param contentType 内容类型（如 "image/png"）
     * @return 包装后的 {@link MultipartFile} 适配器
     * @throws IOException 读取文件大小失败时抛出
     * @note 适配器为懒加载内容（getBytes/getInputStream 实时读盘），非预读入内存
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
