package com.njydsz.common.json.exception;

/**
 * JSON 序列化异常（参考 Jackson 的 JsonMappingException）
 *
 * <p>在 JSON 序列化过程中抛出的异常。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JsonSerializationException extends JsonException {

    private static final long serialVersionUID = 1L;

    public static final int NULL_OBJECT = 2001;
    public static final int CIRCULAR_REFERENCE = 2002;
    public static final int UNSUPPORTED_TYPE = 2003;
    public static final int FIELD_ACCESS_ERROR = 2004;
    public static final int SERIALIZATION_ERROR = 2005;

    /**
     * 构造函数（仅消息）
     *
     * @param message 错误消息
     */
    public JsonSerializationException(String message) {
        super(SERIALIZATION_ERROR, message);
    }

    /**
     * 构造函数（错误码和消息）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public JsonSerializationException(int errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数（消息和原因）
     *
     * @param message 错误消息
     * @param cause 原始异常
     */
    public JsonSerializationException(String message, Throwable cause) {
        super(SERIALIZATION_ERROR, message, cause);
    }

    /**
     * 构造函数（错误码、消息和原因）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 原始异常
     */
    public JsonSerializationException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 创建空对象异常
     *
     * @return 序列化异常
     */
    public static JsonSerializationException nullObject() {
        return new JsonSerializationException(NULL_OBJECT, "Cannot serialize null object");
    }

    /**
     * 创建循环引用异常
     *
     * @param obj 循环引用的对象
     * @return 序列化异常
     */
    public static JsonSerializationException circularReference(Object obj) {
        return new JsonSerializationException(CIRCULAR_REFERENCE,
            "Circular reference detected: " + (obj != null ? obj.getClass().getName() : "null"));
    }

    /**
     * 创建不支持类型异常
     *
     * @param clazz 不支持的类型
     * @return 序列化异常
     */
    public static JsonSerializationException unsupportedType(Class<?> clazz) {
        return new JsonSerializationException(UNSUPPORTED_TYPE,
            "Unsupported type: " + (clazz != null ? clazz.getName() : "null"));
    }

    /**
     * 创建字段访问异常
     *
     * @param fieldName 字段名
     * @param cause 原始异常
     * @return 序列化异常
     */
    public static JsonSerializationException fieldAccessError(String fieldName, Throwable cause) {
        return new JsonSerializationException(FIELD_ACCESS_ERROR,
            "Failed to access field: " + fieldName, cause);
    }

    @Override
    public String getMessage() {
        // 基类 JsonException.getMessage() 已自动追加 [field: xxx]
        // 这里无需重复处理 fieldPath
        return super.getMessage();
    }
}
