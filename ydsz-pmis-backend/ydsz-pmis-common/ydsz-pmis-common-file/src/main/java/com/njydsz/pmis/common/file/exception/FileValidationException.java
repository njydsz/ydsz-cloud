package com.njydsz.pmis.common.file.exception;

/**
 * 文件校验异常。
 * <p>
 * 当文件上传校验失败时（如文件大小超限、扩展名不允许、Content-Type 不匹配等）抛出此异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class FileValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造文件校验异常
     *
     * @param message 异常信息
     */
    public FileValidationException(String message) {
        super(message);
    }

    /**
     * 构造文件校验异常（带原因）
     *
     * @param message 异常信息
     * @param cause   导致此异常的原始原因
     */
    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
