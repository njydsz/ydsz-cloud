package com.njydsz.pmis.common.util.file;

import com.njydsz.pmis.common.util.string.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FileValidator - 文件上传验证器
 * 参考：Spring MultipartValidator, Apache Commons FileUpload
 * 
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @desc 支持文件非空验证、大小验证、后缀白名单验证
 */
public class FileValidator {

    /**
     * 严格模式允许的后缀（仅安全类型：图片、文档、压缩包）
     * 用于生产环境默认白名单
     */
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
