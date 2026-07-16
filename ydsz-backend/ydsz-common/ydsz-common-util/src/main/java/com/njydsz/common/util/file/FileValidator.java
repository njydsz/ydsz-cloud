package com.njydsz.common.util.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.util.string.StringUtils;

/**
 * FileValidator - 文件上传验证器
 * 参考：Spring MultipartValidator, Apache Commons FileUpload
 * 
 * @author ydsz-team
 * @since 1.0.0
 * 
 *
 */
public class FileValidator {

    /**
     * 严格模式允许的后缀（仅安全类型：图片、文档、压缩包）
     * 用于生产环境默认白名单
     */
    /**
     * 文件类型与魔数的映射表
     *
     * <p>用于文件头魔数校验，防止通过修改后缀名绕过扩展名白名单。
     */
    private static final Map<String, byte[][]> MAGIC_NUMBERS = new HashMap<>();

    static {
        // JPEG: FF D8 FF
        MAGIC_NUMBERS.put("jpg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}});
        MAGIC_NUMBERS.put("jpeg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}});
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        MAGIC_NUMBERS.put("png", new byte[][]{{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}});
        // GIF: 47 49 46 38
        MAGIC_NUMBERS.put("gif", new byte[][]{{0x47, 0x49, 0x46, 0x38}});
        // BMP: 42 4D
        MAGIC_NUMBERS.put("bmp", new byte[][]{{0x42, 0x4D}});
        // PDF: 25 50 44 46
        MAGIC_NUMBERS.put("pdf", new byte[][]{{0x25, 0x50, 0x44, 0x46}});
        // ZIP/DOCX/XLSX/PPTX: 50 4B 03 04
        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04};
        MAGIC_NUMBERS.put("zip", new byte[][]{zipMagic});
        MAGIC_NUMBERS.put("docx", new byte[][]{zipMagic});
        MAGIC_NUMBERS.put("xlsx", new byte[][]{zipMagic});
        MAGIC_NUMBERS.put("pptx", new byte[][]{zipMagic});
        // DOC/XLS/PPT: D0 CF 11 E0 A1 B1 1A E1 (OLE2)
        byte[] ole2Magic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        MAGIC_NUMBERS.put("doc", new byte[][]{ole2Magic});
        MAGIC_NUMBERS.put("xls", new byte[][]{ole2Magic});
        MAGIC_NUMBERS.put("ppt", new byte[][]{ole2Magic});
        // ICO: 00 00 01 00
        MAGIC_NUMBERS.put("ico", new byte[][]{{0x00, 0x00, 0x01, 0x00}});
        // RAR: 52 61 72 21 1A 07
        MAGIC_NUMBERS.put("rar", new byte[][]{{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07}});
    }

    /**
     * 校验文件头魔数
     *
     * <p>通过读取文件前几个字节，与已知文件类型的魔数比对，
     * 防止通过修改文件后缀名绕过扩展名白名单。
     *
     * @param file   待校验的文件
     * @param suffix 文件后缀（小写，不含点号）
     * @throws IllegalArgumentException 魔数不匹配时抛出
     * @throws IOException              读取文件内容时发生 IO 异常
     */
    public void validateMagicNumber(MultipartFile file, String suffix) throws IOException {
        if (file == null || suffix == null) {
            return;
        }
        byte[][] expectedMagics = MAGIC_NUMBERS.get(suffix);
        if (expectedMagics == null) {
            // 未知类型，跳过魔数校验
            return;
        }

        byte[] fileHeader;
        try (InputStream is = file.getInputStream()) {
            int maxMagicLength = 8;
            fileHeader = new byte[maxMagicLength];
            int bytesRead = is.read(fileHeader);
            if (bytesRead < maxMagicLength) {
                byte[] trimmed = new byte[bytesRead];
                System.arraycopy(fileHeader, 0, trimmed, 0, bytesRead);
                fileHeader = trimmed;
            }
        }

        boolean matched = false;
        for (byte[] expected : expectedMagics) {
            if (fileHeader.length >= expected.length &&
                    Arrays.equals(Arrays.copyOf(fileHeader, expected.length), expected)) {
                matched = true;
                break;
            }
        }

        if (!matched) {
            throw new IllegalArgumentException(
                    "文件内容与后缀名不匹配（魔数校验失败）" +
                    " | declaredType=" + suffix +
                    " | actualHeader=" + bytesToHex(Arrays.copyOf(fileHeader, Math.min(fileHeader.length, 8))));
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * 验证单个文件（含魔数校验）
     *
     * @param file 待验证的文件
     * @throws IllegalArgumentException 验证失败时抛出异常
     * @throws IOException              读取文件内容时发生 IO 异常
     */
    public void validateWithMagicCheck(MultipartFile file) throws IOException {
        validate(file);
        String suffix = normalizeSuffix(FileTypeUtils.getFileType(file.getOriginalFilename()));
        validateMagicNumber(file, suffix);
    }

    private static final String[] STRICT_SUFFIX = new String[]{
            "png", "bmp", "jpg", "jpeg", "gif", "svg", "ico",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "zip", "rar"
    };

    private static final Set<String> STRICT_SUFFIX_SET = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(STRICT_SUFFIX))
    );

    /**
     * 开发模式允许的后缀（包含 STRICT_SUFFIX + 代码/脚本/配置文件）
     * 仅供开发环境使用，不应作为生产环境默认值
     */
    private static final String[] DEVELOPMENT_SUFFIX = new String[]{
            "java", "sql", "sh", "cmd", "php", "vue",
            "xml", "js", "py", "py3", "css", "md", "html",
            "htm", "json", "mp3", "mp4"
    };

    private static final Set<String> DEVELOPMENT_SUFFIX_SET = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(DEVELOPMENT_SUFFIX))
    );

    private static final Set<String> DEFAULT_ALLOWED_SUFFIX_SET = STRICT_SUFFIX_SET;
    
    private final Set<String> configuredAllowedSuffixSet;
    private final Long maxSize;

    /**
     * 构造方法 - 使用默认配置
     */
    public FileValidator() {
        this.configuredAllowedSuffixSet = Collections.emptySet();
        this.maxSize = null;
    }

    /**
     * 构造方法 - 自定义允许的后缀列表
     *
     * @param allowedSuffixes 允许的文件后缀列表
     */
    public FileValidator(List<String> allowedSuffixes) {
        this(allowedSuffixes, null);
    }

    /**
     * 构造方法 - 自定义允许的后缀列表和最大文件大小
     *
     * @param allowedSuffixes 允许的文件后缀列表
     * @param maxSize 最大文件大小（字节），null 表示不限制
     */
    public FileValidator(List<String> allowedSuffixes, Long maxSize) {
        this.maxSize = maxSize;
        if (allowedSuffixes == null || allowedSuffixes.isEmpty()) {
            this.configuredAllowedSuffixSet = Collections.emptySet();
        } else {
            Set<String> normalizedSet = new HashSet<>();
            for (String suffix : allowedSuffixes) {
                String normalized = normalizeSuffix(suffix);
                if (!normalized.isEmpty()) {
                    normalizedSet.add(normalized);
                }
            }
            this.configuredAllowedSuffixSet = Collections.unmodifiableSet(normalizedSet);
        }
    }

    /**
     * 验证单个文件
     *
     * @param file 待验证的文件
     * @throws IllegalArgumentException 验证失败时抛出异常
     */
    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateSize(file);
        validateSuffix(file);
    }

    /**
     * 验证多个文件
     *
     * @param files 待验证的文件数组
     * @throws IllegalArgumentException 验证失败时抛出异常
     */
    public void validate(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("文件不能为空");
        }
        for (MultipartFile file : files) {
            validate(file);
        }
    }

    /**
     * 验证文件列表
     *
     * @param files 待验证的文件列表
     * @throws IllegalArgumentException 验证失败时抛出异常
     */
    public void validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        for (MultipartFile file : files) {
            validate(file);
        }
    }

    /**
     * 验证文件非空
     */
    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
    }

    /**
     * 验证文件大小
     */
    private void validateSize(MultipartFile file) {
        if (maxSize != null && maxSize > 0 && file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制：" + FileUtils.formatFileSize(maxSize));
        }
    }

    /**
     * 验证文件后缀
     */
    private void validateSuffix(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename) || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("文件名无效");
        }
        
        String suffix = normalizeSuffix(FileTypeUtils.getFileType(originalFilename));
        Set<String> allowedSet = getEffectiveAllowedSuffixSet();
        
        if (!allowedSet.contains(suffix)) {
            throw new IllegalArgumentException("不允许的文件类型：" + suffix);
        }
    }

    /**
     * 获取有效的允许后缀集合
     * 如果未配置自定义后缀，则使用默认后缀集合
     */
    private Set<String> getEffectiveAllowedSuffixSet() {
        return configuredAllowedSuffixSet.isEmpty() ? DEFAULT_ALLOWED_SUFFIX_SET : configuredAllowedSuffixSet;
    }

    /**
     * 规范化文件后缀（转小写、去空格）
     */
    private String normalizeSuffix(String suffix) {
        return suffix == null ? "" : suffix.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 检查文件类型是否被允许
     *
     * @param filename 文件名
     * @return true 允许，false 不允许
     */
    public boolean isAllowed(String filename) {
        String suffix = normalizeSuffix(FileTypeUtils.getFileType(filename));
        return getEffectiveAllowedSuffixSet().contains(suffix);
    }

    /**
     * 获取允许的最大文件大小
     *
     * @return 最大文件大小（字节），null 表示不限制
     */
    public Long getMaxSize() {
        return maxSize;
    }

    /**
     * 获取允许的文件后缀集合
     *
     * @return 允许的文件后缀集合
     */
    public Set<String> getAllowedSuffixes() {
        return getEffectiveAllowedSuffixSet();
    }

    /**
     * 构建默认的文件验证器
     *
     * @return 默认验证器实例
     */
    public static FileValidator defaultValidator() {
        return new FileValidator();
    }

    /**
     * 构建自定义的文件验证器
     *
     * @param allowedSuffixes 允许的后缀列表
     * @return 自定义验证器实例
     */
    public static FileValidator customValidator(List<String> allowedSuffixes) {
        return new FileValidator(allowedSuffixes);
    }

    /**
     * 构建带大小限制的自定义验证器
     *
     * @param allowedSuffixes 允许的后缀列表
     * @param maxSize 最大文件大小（字节）
     * @return 自定义验证器实例
     */
    public static FileValidator customValidator(List<String> allowedSuffixes, Long maxSize) {
        return new FileValidator(allowedSuffixes, maxSize);
    }

    /**
     * 获取严格模式允许的后缀集合（图片、文档、压缩包）
     *
     * @return 不可修改的后缀集合
     */
    public static Set<String> getStrictSuffixSet() {
        return STRICT_SUFFIX_SET;
    }

    /**
     * 获取开发模式允许的后缀集合（严格模式 + 代码/脚本/配置文件）
     *
     * @return 不可修改的后缀集合
     */
    public static Set<String> getDevelopmentSuffixSet() {
        return DEVELOPMENT_SUFFIX_SET;
    }
}
