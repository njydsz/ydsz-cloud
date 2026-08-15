package com.njydsz.common.file.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.exception.FileExceptionCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件类型校验工具类
 *
 * <p>基于文件 Magic Number（文件头签名）进行文件类型校验，防止恶意文件通过修改后缀名绕过安全检查。
 *
 * <p><b>校验策略：</b></p>
 * <ul>
 *   <li>读取文件前若干字节（Magic Number）</li>
 *   <li>与已知文件类型的 Magic Number 进行匹配</li>
 *   <li>后缀名与 Magic Number 双重校验</li>
 * </ul>
 *
 * <p><b>支持类型：</b></p>
 * <ul>
 *   <li>图片：JPEG、PNG、GIF、BMP、WEBP、SVG</li>
 *   <li>文档：PDF、DOCX、XLSX、PPTX、TXT</li>
 *   <li>视频：MP4、AVI、FLV、MKV</li>
 *   <li>音频：MP3、WAV、FLAC</li>
 * </ul>
 *
 * <p><b>配置方法：</b></p>
 * <ul>
 *   <li>通过 {@link #init(boolean)} 配置开关（由 Spring 自动注入配置值）</li>
 *   <li>配置文件: {@code ydsz.file.check-magic-number=true|false}</li>
 *   <li>默认启用，关闭后仅基于后缀名白名单校验</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Slf4j
public final class FileTypeValidator {

    private FileTypeValidator() {
        throw new UnsupportedOperationException();
    }

    /**
     * 是否启用 Magic Number 校验（默认启用，由配置文件 ydsz.file.check-magic-number 控制）
     */
    private static volatile boolean checkMagicNumber = true;

    /**
     * 由 Spring 配置自动初始化开关状态
     *
     * @param enabled true 启用（默认），false 关闭
     */
    public static void init(boolean enabled) {
        checkMagicNumber = enabled;
        log.info("[FileTypeValidator] Magic Number 校验已{}（由配置文件控制）", enabled ? "启用" : "关闭");
    }

    /**
     * 获取当前 Magic Number 校验开关状态（仅供内部/测试使用）
     */
    static boolean isEnabled() {
        return checkMagicNumber;
    }

    /**
     * 后缀名到预期 Magic Number 类型的映射
     */
    private static final Map<String, String> EXT_MAGIC_MAP = new HashMap<>();

    static {
        // 后缀映射
        EXT_MAGIC_MAP.put("jpg", "JPEG");
        EXT_MAGIC_MAP.put("jpeg", "JPEG");
        EXT_MAGIC_MAP.put("png", "PNG");
        EXT_MAGIC_MAP.put("gif", "GIF");
        EXT_MAGIC_MAP.put("bmp", "BMP");
        EXT_MAGIC_MAP.put("webp", "WEBP");
        EXT_MAGIC_MAP.put("pdf", "PDF");
        EXT_MAGIC_MAP.put("docx", "ZIP");
        EXT_MAGIC_MAP.put("xlsx", "ZIP");
        EXT_MAGIC_MAP.put("pptx", "ZIP");
        EXT_MAGIC_MAP.put("mp4", "MP4_FTYP");
        EXT_MAGIC_MAP.put("avi", "AVI");
        EXT_MAGIC_MAP.put("flv", "FLV");
        EXT_MAGIC_MAP.put("mp3", "MP3_ID3");
        EXT_MAGIC_MAP.put("wav", "WAV");
        EXT_MAGIC_MAP.put("flac", "FLAC");
    }

    private static final Set<String> ALLOWED_UNKNOWN_EXTENSIONS = Set.of(
            "txt", "md", "csv", "log", "json", "xml",
            "java", "py", "sql", "sh", "php",
            "ico", "doc", "xls", "ppt",
            "mkv", "mov", "wmv", "3gp", "wma", "aac", "ogg", "mp3", "mp4"
    );

    /**
     * 校验 MultipartFile 的文件类型是否合法
     *
     * <p>通过后缀名和 Magic Number 双重校验。</p>
     * <p>对于未知后缀（不在 Magic Number 映射表中的），采用白名单机制放行；不在白名单中的后缀直接拒绝。</p>
     *
     * @param file 上传的文件
     * @throws BusinessException 文件类型不合法时抛出
     */
    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_NAME_INVALID);
        }

        String suffix = extractSuffix(originalFilename);
        if (suffix.isEmpty()) {
            throw new BusinessException(FileExceptionCode.FILE_SUFFIX_NOT_ALLOWED);
        }

        String lowerSuffix = suffix.toLowerCase();
        String expectedMagicType = EXT_MAGIC_MAP.get(lowerSuffix);
        if (expectedMagicType == null) {
            if (!ALLOWED_UNKNOWN_EXTENSIONS.contains(lowerSuffix)) {
                log.warn("[FileTypeValidator] 文件后缀不在允许列表中: {}", originalFilename);
                throw new BusinessException(FileExceptionCode.FILE_SUFFIX_NOT_ALLOWED);
            }
            return;
        }

        if (!checkMagicNumber) {
            return;
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[32];
            int read = is.read(header);
            if (read < 4) {
                log.warn("[FileTypeValidator] 文件头读取不足，拒绝上传: {}", originalFilename);
                throw new BusinessException(FileExceptionCode.FILE_SUFFIX_NOT_ALLOWED);
            }

            if (!MagicNumberRegistry.match(header, expectedMagicType)) {
                log.warn("[FileTypeValidator] Magic Number 不匹配: file={}, expected={}", originalFilename, expectedMagicType);
                throw new BusinessException(FileExceptionCode.FILE_SUFFIX_NOT_ALLOWED);
            }
        } catch (IOException e) {
            log.error("[FileTypeValidator] 文件读取失败: {}", originalFilename, e);
            throw new BusinessException(FileExceptionCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 提取文件后缀名（不含点）
     *
     * @param filename 文件名
     * @return 后缀名，无后缀返回空字符串
     */
    private static String extractSuffix(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 判断是否为图片文件（基于 Magic Number）
     *
     * @param file 上传的文件
     * @return true 如果是图片
     */
    public static boolean isImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                return false;
            }
            return MagicNumberRegistry.match(header, "JPEG")
                    || MagicNumberRegistry.match(header, "PNG")
                    || MagicNumberRegistry.match(header, "GIF")
                    || MagicNumberRegistry.match(header, "BMP")
                    || MagicNumberRegistry.match(header, "WEBP");
        } catch (IOException e) {
            return false;
        }
    }
}
