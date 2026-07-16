package com.njydsz.common.json.exception;

/**
 * JSON 反序列化异常（参考 Jackson 的 JsonMappingException）
 *
 * <p>在 JSON 反序列化过程中抛出的异常，包含行列号和上下文片段。</p>
 *
 * @since 1.0.0
 */
public class JsonDeserializationException extends JsonException {

    private static final long serialVersionUID = 1L;

    public static final int MISSING_FIELD = 3001;
    public static final int TYPE_MISMATCH = 3002;
    public static final int INVALID_VALUE = 3003;
    public static final int NO_DEFAULT_CONSTRUCTOR = 3004;
    public static final int PARSE_ERROR = 3005;
    public static final int VALIDATION_ERROR = 3006;

    /** 行号（1-based） */
    private int line = -1;

    /** 列号（1-based） */
    private int column = -1;

    /** 上下文片段（错误位置前后的 JSON 文本） */
    private String contextSnippet;

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

    /**
     * 创建带行列号和上下文片段的异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param position 字符位置
     * @param json 原始 JSON 字符串（用于计算行列号和上下文）
     * @since 1.4.0
     */
    public JsonDeserializationException(int errorCode, String message, int position, String json) {
        super(errorCode, enrichMessage(message, position, json), position);
        computeLineColumn(json, position);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getContextSnippet() {
        return contextSnippet;
    }

    /**
     * 从 JSON 字符串和位置计算行列号和上下文片段。
     */
    private void computeLineColumn(String json, int position) {
        if (json == null || position < 0 || position >= json.length()) {
            return;
        }
        int lineNum = 1;
        int colNum = 1;
        for (int i = 0; i < position && i < json.length(); i++) {
            if (json.charAt(i) == '\n') {
                lineNum++;
                colNum = 1;
            } else {
                colNum++;
            }
        }
        this.line = lineNum;
        this.column = colNum;

        // 提取上下文片段（前后各 40 字符）
        int start = Math.max(0, position - 40);
        int end = Math.min(json.length(), position + 40);
        StringBuilder sb = new StringBuilder(end - start + 3);
        sb.append(json, start, position);
        sb.append("[HERE]");
        sb.append(json, position, end);
        this.contextSnippet = sb.toString();
    }

    /**
     * 增强 error message，附加行列号信息。
     */
    private static String enrichMessage(String message, int position, String json) {
        if (json == null || position < 0) {
            return message;
        }
        int lineNum = 1;
        int colNum = 1;
        for (int i = 0; i < position && i < json.length(); i++) {
            if (json.charAt(i) == '\n') {
                lineNum++;
                colNum = 1;
            } else {
                colNum++;
            }
        }
        return message + " (line " + lineNum + ", column " + colNum + ")";
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
            "Failed to parse JSON at position " + position, position, json);
    }

    public static JsonDeserializationException validationError(String message) {
        return new JsonDeserializationException(VALIDATION_ERROR, message);
    }
}
