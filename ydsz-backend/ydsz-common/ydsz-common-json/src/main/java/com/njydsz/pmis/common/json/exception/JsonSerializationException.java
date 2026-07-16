package com.njydsz.common.json.exception;

/**
 * JSON 序列化异常（参考 Jackson 的 JsonMappingException）
 *
 * <p>在 JSON 序列化过程中抛出的异常。</p>
 *
 * @since 1.0.0
 */
public class JsonSerializationException extends JsonException {

    private static final long serialVersionUID = 1L;

    public static final int NULL_OBJECT = 2001;
    public static final int CIRCULAR_REFERENCE = 2002;
    public static final int UNSUPPORTED_TYPE = 2003;
    public static final int FIELD_ACCESS_ERROR = 2004;
    public static final int SERIALIZATION_ERROR = 2005;

    public JsonSerializationException(String message) {
        super(UNSUPPORTED_TYPE, message);
    }

    public JsonSerializationException(int errorCode, String message) {
        super(errorCode, message);
    }

    public JsonSerializationException(String message, Throwable cause) {
        super(UNSUPPORTED_TYPE, message, cause);
    }

    public JsonSerializationException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static JsonSerializationException nullObject() {
        return new JsonSerializationException(NULL_OBJECT, "Cannot serialize null object");
    }

    public static JsonSerializationException circularReference(Object obj) {
        return new JsonSerializationException(CIRCULAR_REFERENCE,
            "Circular reference detected: " + (obj != null ? obj.getClass().getName() : "null"));
    }

    public static JsonSerializationException unsupportedType(Class<?> clazz) {
        return new JsonSerializationException(UNSUPPORTED_TYPE,
            "Unsupported type: " + (clazz != null ? clazz.getName() : "null"));
    }

    public static JsonSerializationException fieldAccessError(String fieldName, Throwable cause) {
        return new JsonSerializationException(FIELD_ACCESS_ERROR,
            "Failed to access field: " + fieldName, cause);
    }
}
