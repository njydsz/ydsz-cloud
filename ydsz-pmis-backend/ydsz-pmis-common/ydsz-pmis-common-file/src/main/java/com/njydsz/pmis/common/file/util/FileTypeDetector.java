package com.njydsz.pmis.common.file.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文件类型检测工具类
 * <p>
 * 基于文件 Magic Number（文件头签名）进行文件类型检测，防止恶意文件通过
 * 修改后缀名绕过安全检查（如 "evil.exe" 改名为 "evil.jpg"）。
 * </p>
 *
 * <p><b>威胁模型：</b>攻击者通过修改文件名后缀绕过仅基于后缀名的安全检查，
 * 利用上传功能传播木马、webshell、可执行文件。Magic Number 检测是</p>
 *
 * <p><b>与 {@link FileTypeValidator} 的区别：</b></p>
 * <ul>
 *   <li>本工具类返回检测到的文件类型，不抛出异常</li>
 *   <li>支持更细粒度的类型匹配（包含多组 Magic Number 变体）</li>
 *   <li>可用于纯检测场景，不依赖 Spring MultipartFile</li>
 * </ul>
 *
 * <p><b>支持类型：</b></p>
 * <ul>
 *   <li>图片：JPEG、PNG、GIF、BMP、WEBP、SVG、ICO、TIFF</li>
 *   <li>文档：PDF、DOCX / XLSX / PPTX（ZIP 容器）</li>
 *   <li>压缩：ZIP、RAR、7Z、GZ、BZ2</li>
 *   <li>视频：MP4、AVI、FLV、MKV、MOV、WEBM</li>
 *   <li>音频：MP3、WAV、FLAC、AAC、OGG</li>
 *   <li>可执行：ELF、PE、CLASS（需重点告警）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public final class FileTypeDetector {

    /**
     * 工具类构造器，禁止实例化
     */
    private FileTypeDetector() {
        throw new UnsupportedOperationException("FileTypeDetector 是工具类，禁止实例化");
    }

    /** 读取文件头的最大字节数，用于 Magic Number 匹配（覆盖所有复合签名的 ASCII 标签） */
    private static final int MAGIC_HEADER_SIZE = 32;

    /** 文件类型到 Magic 签名的映射 */
    private static final Map<String, MagicSignature> SIGNATURES = new HashMap<>();

    /** 文件扩展名到文件类型集合的映射 */
    private static final Map<String, Set<String>> EXT_TO_TYPES = new HashMap<>();

    static {
        register("JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpg", "jpeg");
        register("PNG", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "png");
        register("GIF87a", new byte[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61}, "gif");
        register("GIF89a", new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}, "gif");
        register("BMP", new byte[]{0x42, 0x4D}, "bmp");
        register("WEBP", new byte[]{0x52, 0x49, 0x46, 0x46}, 0, "RIFF....WEBP", "webp");
        register("SVG", new byte[]{0x3C, 0x73, 0x76, 0x67}, "svg");
        register("SVG_XML", new byte[]{0x3C, 0x3F, 0x78, 0x6D, 0x6C}, "svg");
        register("ICO", new byte[]{0x00, 0x00, 0x01, 0x00}, "ico");
        register("TIFF_BE", new byte[]{(byte) 0x4D, (byte) 0x4D, 0x00, 0x2A}, "tiff", "tif");
        register("TIFF_LE", new byte[]{0x49, 0x49, 0x2A, 0x00}, "tiff", "tif");

        register("PDF", new byte[]{0x25, 0x50, 0x44, 0x46}, "pdf");

        register("ZIP", new byte[]{0x50, 0x4B, 0x03, 0x04}, "zip", "docx", "xlsx", "pptx", "odt", "ods", "odp", "jar");
        register("ZIP_EMPTY", new byte[]{0x50, 0x4B, 0x05, 0x06}, "zip");
        register("ZIP_SPANNED", new byte[]{0x50, 0x4B, 0x07, 0x08}, "zip");
        register("RAR5", new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00}, "rar");
        register("RAR4", new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00}, "rar");
        register("SEVEN_Z", new byte[]{0x37, (byte) 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}, "7z");
        register("GZIP", new byte[]{0x1F, (byte) 0x8B}, "gz", "tgz");
        register("BZIP2", new byte[]{0x42, 0x5A, 0x68}, "bz2");

        register("MP4_FTYP", new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}, "mp4", "m4v");
        register("MP4_MOOV", new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}, "mp4");
        register("MP4_FREE", new byte[]{0x00, 0x00, 0x00, 0x00, 0x66, 0x74, 0x79, 0x70}, "mp4");
        register("AVI", new byte[]{0x52, 0x49, 0x46, 0x46}, 4, "AVI", "avi");
        register("FLV", new byte[]{0x46, 0x4C, 0x56, 0x01}, "flv");
        register("MKV", new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}, "mkv", "webm");
        register("MOV", new byte[]{0x00, 0x00, 0x00, 0x14, 0x66, 0x74, 0x79, 0x70}, "mov");
        register("MOV_2", new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}, "mov");

        register("MP3_ID3", new byte[]{0x49, 0x44, 0x33}, "mp3");
        register("MP3_FRAME", new byte[]{(byte) 0xFF, (byte) 0xFB}, "mp3");
        register("MP3_FRAME2", new byte[]{(byte) 0xFF, (byte) 0xF3}, "mp3");
        register("MP3_FRAME3", new byte[]{(byte) 0xFF, (byte) 0xF2}, "mp3");
        register("WAV", new byte[]{0x52, 0x49, 0x46, 0x46}, 4, "WAVE", "wav");
        register("FLAC", new byte[]{0x66, 0x4C, 0x61, 0x43}, "flac");
        register("AAC_ADTS", new byte[]{(byte) 0xFF, (byte) 0xF1}, "aac");
        register("AAC_ADIF", new byte[]{0x41, 0x44, 0x49, 0x46}, "aac");
        register("OGG", new byte[]{0x4F, 0x67, 0x67, 0x53}, "ogg");

        register("ELF", new byte[]{0x7F, 0x45, 0x4C, 0x46}, "elf", "so");
        register("PE_EXE", new byte[]{0x4D, 0x5A}, "exe", "dll");
        register("CLASS", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, "class");

        register("UTF8_BOM", new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }

    private static void register(String type, byte[] magic, String... extensions) {
        SIGNATURES.put(type, new MagicSignature(magic, 0, null));
        for (String ext : extensions) {
            EXT_TO_TYPES.computeIfAbsent(ext.toLowerCase(), k -> new HashSet<>()).add(type);
        }
    }

    /**
     * 注册文件类型签名（支持偏移量和 ASCII 标签匹配）
     *
     * @param type       文件类型名称（如 "WEBP"、"AVI"）
     * @param magic      Magic Number 字节数组
     * @param offset     Magic Number 在文件头中的偏移量
     * @param asciiTag   ASCII 标签（用于复合签名匹配，如 "WEBP"、"AVI"、"WAVE"），可为 null
     * @param extensions 关联的文件扩展名列表
     */
    private static void register(String type, byte[] magic, int offset, String asciiTag, String... extensions) {
        SIGNATURES.put(type, new MagicSignature(magic, offset, asciiTag));
        for (String ext : extensions) {
            EXT_TO_TYPES.computeIfAbsent(ext.toLowerCase(), k -> new HashSet<>()).add(type);
        }
    }

    /**
     * 通过文件头 Magic Number 检测文件类型
     *
     * @param is 文件输入流，方法不会关闭流
     * @return 检测到的文件类型名称（如 "JPEG"、"PNG"），无法识别时返回 "UNKNOWN"
     * @throws IOException 读取流时发生 I/O 异常
     */
    public static String detectByMagic(InputStream is) throws IOException {
        byte[] header = new byte[MAGIC_HEADER_SIZE];
        int bytesRead = is.read(header);
        if (bytesRead < 2) {
            return "UNKNOWN";
        }
        for (Map.Entry<String, MagicSignature> entry : SIGNATURES.entrySet()) {
            String type = entry.getKey();
            MagicSignature sig = entry.getValue();
            if (matchesMagic(header, bytesRead, sig)) {
                return type;
            }
        }
        return "UNKNOWN";
    }

    /**
     * 通过文件扩展名检测文件类型
     *
     * @param filename 文件名
     * @return 匹配的文件类型名称（逗号分隔），无法识别时返回 null
     */
    public static String detectByExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        Set<String> types = EXT_TO_TYPES.get(ext);
        if (types == null || types.isEmpty()) {
            return null;
        }
        return String.join(",", types);
    }

    /**
     * 校验文件 Magic Number 与扩展名是否一致
     * <p>防止恶意文件通过修改后缀名绕过安全检查。
     *
     * @param is       文件输入流，方法不会关闭流
     * @param filename 文件名
     * @return true 表示 Magic Number 与扩展名匹配，false 表示不匹配或无法识别
     * @throws IOException 读取流时发生 I/O 异常
     */
    public static boolean validateMagicAndExtension(InputStream is, String filename) throws IOException {
        String extTypes = detectByExtension(filename);
        if (extTypes == null) {
            return false;
        }
        String magicType = detectByMagic(is);
        if ("UNKNOWN".equals(magicType)) {
            return false;
        }
        String[] candidates = extTypes.split(",");
        for (String candidate : candidates) {
            if (candidate.equals(magicType)) {
                return true;
            }
        }
        if ("ZIP".equals(magicType)) {
            for (String candidate : candidates) {
                if ("ZIP".equals(candidate) || "DOCX".equals(candidate)
                        || "XLSX".equals(candidate) || "PPTX".equals(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断文件头是否与指定的 Magic 签名匹配
     *
     * @param header    文件头字节数组
     * @param headerLen 文件头实际读取长度
     * @param sig       Magic 签名描述
     * @return true 表示匹配，false 表示不匹配
     */
    private static boolean matchesMagic(byte[] header, int headerLen, MagicSignature sig) {
        if (sig.asciiTag != null) {
            int checkOffset = sig.offset + sig.magic.length;
            if (headerLen < checkOffset + sig.asciiTag.length()) {
                return false;
            }
            for (int i = 0; i < sig.magic.length; i++) {
                if (header[i + sig.offset] != sig.magic[i]) {
                    return false;
                }
            }
            for (int i = 0; i < sig.asciiTag.length(); i++) {
                if (header[checkOffset + i] != (byte) sig.asciiTag.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        if (headerLen < sig.offset + sig.magic.length) {
            return false;
        }
        for (int i = 0; i < sig.magic.length; i++) {
            if (header[i + sig.offset] != sig.magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 文件 Magic 签名描述
     * <p>用于描述文件头的字节特征，支持偏移量和 ASCII 标签匹配。
     */
    private static class MagicSignature {
        /** Magic Number 字节数组 */
        final byte[] magic;
        /** Magic Number 在文件头中的偏移量 */
        final int offset;
        /** ASCII 标签（用于 RIFF+WEBP、RIFF+AVI、RIFF+WAVE 等复合签名匹配） */
        final String asciiTag;

        MagicSignature(byte[] magic, int offset, String asciiTag) {
            this.magic = magic;
            this.offset = offset;
            this.asciiTag = asciiTag;
        }
    }
}
