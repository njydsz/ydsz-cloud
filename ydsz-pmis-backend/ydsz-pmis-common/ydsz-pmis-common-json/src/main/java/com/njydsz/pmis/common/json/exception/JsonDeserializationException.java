package com.njydsz.pmis.common.json.exception;

/**
 * JSON 反序列化异常（参考 Jackson 的 JsonMappingException）
 *
 * <p>在 JSON 反序列化过程中抛出的异常。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class JsonDeserializationException extends YdszJsonException {

    private static final long serialVersionUID = 1L;

    public static final int MISSING_FIELD = 3001;
    public static final int TYPE_MISMATCH = 3002;
    public static final int INVALID_VALUE = 3003;
    public static final int NO_DEFAULT_CONSTRUCTOR = 3004;
    public static final int PARSE_ERROR = 3005;
    public static final int VALIDATION_ERROR = 3006;

    public JsonDeserializationException(String message) {
        super(TYPE_MISMATCH, message);
    }

    public JsonDeserializationException(int errorCode, String message) {
        super(errorCode, message);
    }

    public JsonDeserializationException(String message, int position) {
        super(TYPE_MISMATCH, message, position);
    }

    public JsonDeserializationException(int errorCode, String message, int position) {
        super(errorCode, message, position);
    }

    public JsonDeserializationException(String message, Throwable cause) {
        super(TYPE_MISMATCH, message, cause);
    }

    public static JsonDeserializationException missingField(String fieldName) {
        return new JsonDeserializationException(MISSING_FIELD,
            "Missing required field: " + fieldName);
    }

    public static JsonDeserializationException typeMismatch(String fieldName, Class<?> expected, Class<?> actual) {
        return new JsonDeserializationException(TYPE_MISMATCH,
            "Type mismatch for field '" + fieldName + "': expected " +
            (expected != null ? expected.getName() : "null") + " but got " +
            (actual != null ? actual.getName() : "null"));
    }

    public static JsonDeserializationException invalidValue(String fieldName, String value) {
        return new JsonDeserializationException(INVALID_VALUE,
            "Invalid value '" + value + "' for field '" + fieldName + "'");
    }

    public static JsonDeserializationException noDefaultConstructor(Class<?> clazz) {
        return new JsonDeserializationException(NO_DEFAULT_CONSTRUCTOR,
            "No default constructor for class: " + (clazz != null ? clazz.getName() : "null"));
    }

    public static JsonDeserializationException parseError(String json, int position) {
        return new JsonDeserializationException(PARSE_ERROR,
            "Failed to parse JSON at position " + position + ": " + json, position);
    }

    public static JsonDeserializationException validationError(String message) {
        return new JsonDeserializationException(VALIDATION_ERROR, message);
    }
}
