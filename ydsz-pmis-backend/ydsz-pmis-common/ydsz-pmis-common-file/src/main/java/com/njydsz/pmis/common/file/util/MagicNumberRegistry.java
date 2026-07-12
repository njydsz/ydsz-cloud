package com.njydsz.pmis.common.file.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 文件 Magic Number 注册表
 *
 * <p>集中管理常见文件类型的 Magic Number（文件头签名），
 * 用于 FileTypeValidator 进行文件类型真实校验。
 *
 * <p><b>注册表覆盖：</b></p>
 * <ul>
 *   <li>图片：PNG、JPEG、GIF、BMP、WEBP</li>
 *   <li>文档：PDF、DOCX、XLSX、PPTX、XLS</li>
 *   <li>压缩：ZIP</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class MagicNumberRegistry {

    private static final Map<String, byte[]> REGISTRY = new HashMap<>();
    private static final Map<String, Set<String>> EXT_TO_MAGIC_TYPES = new HashMap<>();

    static {
        register("PNG", hex("89 50 4E 47"));
        register("JPEG", hex("FF D8 FF"));
        register("GIF", hex("47 49 46"));
        register("BMP", hex("42 4D"));
        register("WEBP", hex("52 49 46 46"));

        register("PDF", hex("25 50 44 46"));
        register("ZIP", hex("50 4B 03 04"));
        register("ZIP_EMPTY", hex("50 4B 05 06"));
        register("ZIP_SPANNED", hex("50 4B 07 08"));

        register("XLS", hex("D0 CF 11 E0 A1 B1 1A E1"));
        register("DOC", hex("D0 CF 11 E0 A1 B1 1A E1"));
        register("PPT", hex("D0 CF 11 E0 A1 B1 1A E1"));

        register("MP4_FTYP", hex("00 00 00 18 66 74 79 70"));
        register("MP4_MOOV", hex("00 00 00 20 66 74 79 70"));
        register("AVI", hex("52 49 46 46"));
        register("FLV", hex("46 4C 56 01"));

        register("MP3_ID3", hex("49 44 33"));
        register("MP3_NO_ID3", hex("FF FB"));
        register("WAV", hex("52 49 46 46"));
        register("FLAC", hex("66 4C 61 43"));

        mapExtToMagic("jpg", "JPEG");
        mapExtToMagic("jpeg", "JPEG");
        mapExtToMagic("png", "PNG");
        mapExtToMagic("gif", "GIF");
        mapExtToMagic("bmp", "BMP");
        mapExtToMagic("webp", "WEBP");
        mapExtToMagic("pdf", "PDF");
        mapExtToMagic("docx", "ZIP");
        mapExtToMagic("xlsx", "ZIP");
        mapExtToMagic("pptx", "ZIP");
        mapExtToMagic("doc", "DOC");
        mapExtToMagic("xls", "XLS");
        mapExtToMagic("ppt", "PPT");
        mapExtToMagic("zip", "ZIP");
        mapExtToMagic("mp4", "MP4_FTYP");
        mapExtToMagic("avi", "AVI");
        mapExtToMagic("flv", "FLV");
        mapExtToMagic("mp3", "MP3_ID3");
        mapExtToMagic("wav", "WAV");
        mapExtToMagic("flac", "FLAC");
    }

    private MagicNumberRegistry() {
        throw new UnsupportedOperationException();
    }

    /**
     * 注册 Magic Number
     *
     * @param type 文件类型标识
     * @param magic Magic Number 字节数组
     */
    public static void register(String type, byte[] magic) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("类型标识不能为空");
        }
        if (magic == null || magic.length == 0) {
            throw new IllegalArgumentException("Magic Number 不能为空");
        }
        REGISTRY.put(type, magic);
    }

    /**
     * 获取指定类型的 Magic Number
     *
     * @param type 文件类型标识
     * @return Magic Number 字节数组，未注册返回 null
     */
    public static byte[] getMagicNumber(String type) {
        byte[] magic = REGISTRY.get(type);
        return magic != null ? magic.clone() : null;
    }

    /**
     * 根据文件后缀获取预期的 Magic Number 类型集合
     *
     * @param ext 文件后缀（不含点）
     * @return 预期类型集合，未映射返回空集合
     */
    public static Set<String> getExpectedMagicTypes(String ext) {
        if (ext == null || ext.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> types = EXT_TO_MAGIC_TYPES.get(ext.toLowerCase());
        return types != null ? types : Collections.emptySet();
    }

    /**
     * 匹配文件头字节与指定类型的 Magic Number
     *
     * @param header 文件头字节
     * @param type 文件类型标识
     * @return true 匹配成功
     */
    public static boolean match(byte[] header, String type) {
        if (header == null || type == null) {
            return false;
        }
        byte[] magic = REGISTRY.get(type);
        if (magic == null) {
            return false;
        }
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 匹配文件头字节与任一预期类型的 Magic Number
     *
     * @param header 文件头字节
     * @param expectedTypes 预期类型集合
     * @return true 至少匹配一种
     */
    public static boolean matchAny(byte[] header, Set<String> expectedTypes) {
        if (header == null || expectedTypes == null || expectedTypes.isEmpty()) {
            return false;
        }
        for (String type : expectedTypes) {
            if (match(header, type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取已注册的类型数量
     */
    public static int size() {
        return REGISTRY.size();
    }

    private static void mapExtToMagic(String ext, String type) {
        EXT_TO_MAGIC_TYPES.computeIfAbsent(ext.toLowerCase(), k -> new java.util.HashSet<>())
                .add(type);
    }

    private static byte[] hex(String hexStr) {
        String[] parts = hexStr.split("\\s+");
        byte[] result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }
}
