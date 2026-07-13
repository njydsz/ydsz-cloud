package com.njydsz.pmis.common.json.exception;

/**
 * YdszJson 异常基类（参考 Jackson 的 JsonProcessingException）
 * 
 * <p>所有 YdszJson 相关异常的基类。</p>
 * 
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class YdszJsonException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /** 错误码 */
    private final int errorCode;
    
    /** JSON 字符串位置 */
    private final int position;
    
    public YdszJsonException(String message) {
        super(message);
        this.errorCode = 0;
        this.position = -1;
    }
    
    public YdszJsonException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
        this.position = -1;
    }
    
    public YdszJsonException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.position = -1;
    }
    
    public YdszJsonException(int errorCode, String message, int position) {
        super(message + " at position " + position);
        this.errorCode = errorCode;
        this.position = position;
    }
    
    public YdszJsonException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.position = -1;
    }
    
    /**
     * 获取错误码
     */
    public int getErrorCode() {
        return errorCode;
    }
    
    /**
     * 获取 JSON 字符串位置
     */
    public int getPosition() {
        return position;
    }
}
