package com.remisoft.common.json.exception;

/**
 * YdszJson 异常基类（参考 Jackson 的 JsonProcessingException）
 * 
 * <p>所有 YdszJson 相关异常的基类。</p>
 * 
 * @author remi-team
 * @since 1.0.0
 */
public class JsonException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /** 错误码 */
    private final int errorCode;
    
    /** JSON 字符串位置 */
    private final int position;
    
    /**
     * 构造函数（仅消息）
     *
     * @param message 错误消息
     */
    public JsonException(String message) {
        super(message);
        this.errorCode = 0;
        this.position = -1;
    }
    
    /**
     * 构造函数（消息和原因）
     *
     * @param message 错误消息
     * @param cause 原始异常
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
        this.position = -1;
    }
    
    /**
     * 构造函数（错误码和消息）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public JsonException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.position = -1;
    }
    
    /**
     * 构造函数（错误码、消息和位置）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param position JSON 字符串中的位置
     */
    public JsonException(int errorCode, String message, int position) {
        super(message + " at position " + position);
        this.errorCode = errorCode;
        this.position = position;
    }
    
    /**
     * 构造函数（错误码、消息和原因）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 原始异常
     */
    public JsonException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.position = -1;
    }
    
    /**
     * 获取错误码
     *
     * @return 错误码，未设置时返回 0
     */
    public int getErrorCode() {
        return errorCode;
    }
    
    /**
     * 获取 JSON 字符串位置
     *
     * @return 字符位置，未设置时返回 -1
     */
    public int getPosition() {
        return position;
    }
}
