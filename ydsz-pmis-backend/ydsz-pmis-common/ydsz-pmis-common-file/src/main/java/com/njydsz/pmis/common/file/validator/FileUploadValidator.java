package com.njydsz.pmis.common.file.validator;

import com.njydsz.pmis.common.file.exception.FileValidationException;
import com.njydsz.pmis.common.file.util.FileTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件上传校验器。
 *
 * <p>对上传文件进行多维校验，包括：
 * <ul>
 *     <li>请求总大小限制（maxRequestSize）</li>
 *     <li>单文件大小限制（maxFileSize）</li>
 *     <li>文件扩展名白名单（allowedExtensions）</li>
 *     <li>Content-Type 白名单（allowedContentTypes）</li>
 *     <li><b>Magic Number 校验</b>（防止伪造扩展名）</li>
 * </ul>
 * 校验失败时抛出 {@link FileValidationException}。
 *
 * @author ydsz-pmis-team
 * 
 * 
 */
@Slf4j
public class FileUploadValidator {

    /** 允许的文件扩展名白名单，为空时不校验扩展名 */
    private final Set<String> allowedExtensions;
    /** 允许的 Content-Type 白名单，为空时不校验 Content-Type */
    private final Set<String> allowedContentTypes;
    /** 单个文件最大允许大小（字节） */
    private final long maxFileSize;
    /** 请求总大小上限（字节） */
    private final long maxRequestSize;
    /** 是否启用 Magic Number 校验 */
    private final boolean magicNumberCheckEnabled;

    public FileUploadValidator(Set<String> allowedExtensions, Set<String> allowedContentTypes,
                                long maxFileSize, long maxRequestSize) {
        this(allowedExtensions, allowedContentTypes, maxFileSize, maxRequestSize, true);
    }

    public FileUploadValidator(Set<String> allowedExtensions, Set<String> allowedContentTypes,
                                long maxFileSize, long maxRequestSize, boolean magicNumberCheckEnabled) {
        this.allowedExtensions = allowedExtensions;
        this.allowedContentTypes = allowedContentTypes;
        this.maxFileSize = maxFileSize;
        this.maxRequestSize = maxRequestSize;
        this.magicNumberCheckEnabled = magicNumberCheckEnabled;
    }

    /**
     * 校验上传文件的合法性（带 Magic Number）
     *
     * @param file MultipartFile 对象
     * @throws FileValidationException 校验失败
     */
    public void validate(MultipartFile file) {
        if (file == null) {
            throw new FileValidationException("文件为空");
        }
        validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
        if (magicNumberCheckEnabled) {
            try {
                FileTypeValidator.validate(file);
            } catch (com.njydsz.pmis.common.exception.custom.BizException e) {
                throw new FileValidationException("Magic Number 校验失败: " + e.getMessage());
            }
        }
    }

    /**
     * 校验上传文件的合法性
     *
     * @param fileName    文件名
     * @param contentType 文件 Content-Type
     * @param fileSize    文件大小（字节）
     * @throws FileValidationException 校验失败时抛出
     */
    public void validate(String fileName, String contentType, long fileSize) {
        if (fileSize > maxRequestSize) {
            throw new FileValidationException("请求大小超出限制: " + fileSize + " > " + maxRequestSize);
        }
        if (fileSize > maxFileSize) {
            throw new FileValidationException("文件大小超出限制: " + fileSize + " > " + maxFileSize);
        }
        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            String extension = getFileExtension(fileName);
            if (extension == null || !allowedExtensions.contains(extension.toLowerCase())) {
                throw new FileValidationException("不支持的文件扩展名: " + extension);
            }
        }
        if (allowedContentTypes != null && !allowedContentTypes.isEmpty()) {
            if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
                throw new FileValidationException("不支持的Content-Type: " + contentType);
            }
        }
    }

    /**
     * 从文件名中提取扩展名
     *
     * @param fileName 文件名
     * @return 文件扩展名（不含点号），无扩展名时返回 null
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
