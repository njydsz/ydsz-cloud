package com.njydsz.common.json.exception;

/**
 * JSON 反序列化异常（参考 Jackson 的 JsonMappingException）
 *
 * <p>在 JSON 反序列化过程中抛出的异常，包含行列号和上下文片段。</p>
 *
 * @author ydsz-team
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

    /** 当前字段名（发生错误的字段，可为 null） */
    private String fieldName;

    /** JSON token 类型（如 "FIELD_NAME", "VALUE_STRING", "START_OBJECT" 等），可为 null */
    private String tokenType;

    /** 源码片段（出错位置前后 20 字符的短上下文），可为 null */
    private String sourceSnippet;

    /**
     * 构造函数（仅消息）
     *
     * @param message 错误消息
     */
    public JsonDeserializationException(String message) {
        super(PARSE_ERROR, message);
    }

    /**
     * 构造函数（错误码和消息）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public JsonDeserializationException(int errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数（消息和位置）
     *
     * @param message 错误消息
     * @param position JSON 字符串中的位置
     */
    public JsonDeserializationException(String message, int position) {
        super(TYPE_MISMATCH, message, position);
    }

    /**
     * 构造函数（错误码、消息和位置）
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param position JSON 字符串中的位置
     */
    public JsonDeserializationException(int errorCode, String message, int position) {
        super(errorCode, message, position);
    }

    /**
     * 构造函数（消息和原因）
     *
     * @param message 错误消息
     * @param cause 原始异常
     */
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
     * @since 1.0.0
     */
    public JsonDeserializationException(int errorCode, String message, int position, String json) {
        super(errorCode, enrichMessage(message, position, json), position);
        computeLineColumn(json, position);
    }

    /**
     * 获取行号（从 1 开始）
     *
     * @return 行号，未设置时返回 -1
     */
    public int getLine() {
        return line;
    }

    /**
     * 获取列号（从 1 开始）
     *
     * @return 列号，未设置时返回 -1
     */
    public int getColumn() {
        return column;
    }

    /**
     * 获取上下文片段
     *
     * @return 错误位置前后的 JSON 文本片段
     */
    public String getContextSnippet() {
        return contextSnippet;
    }

    /**
     * 获取当前字段名
     *
     * @return 发生错误的字段名，未设置时返回 null
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * 获取 JSON token 类型
     *
     * @return token 类型（如 "FIELD_NAME", "VALUE_STRING"），未设置时返回 null
     * @since 1.0.0
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * 获取源码片段
     *
     * @return 出错位置前后 20 字符的短上下文片段，未设置时返回 null
     * @since 1.0.0
     */
    public String getSourceSnippet() {
        return sourceSnippet;
    }

    /**
     * 设置当前字段名（用于反序列化过程中追踪当前字段）。
     *
     * <p>与 {@link #setFieldName(String)} 功能一致，返回 {@code this} 支持链式调用。
     * 推荐在创建异常后立即链式调用，例如：</p>
     * <pre>
     * throw new JsonDeserializationException(TYPE_MISMATCH, "类型不匹配")
     *     .withFieldName(fieldName);
     * </pre>
     *
     * @param fieldName 字段名
     * @return this（链式调用）
     * @since 1.2.0
     */
    public JsonDeserializationException withFieldName(String fieldName) {
        this.fieldName = fieldName;
        return this;
    }

    /**
     * 设置当前字段名的旧版方法（链式调用版见 {{@link #withFieldName(String)}}）。
     *
     * @param fieldName 字段名
     */
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * 设置 JSON token 类型（用于错误定位时标明正在解析的 token）
     *
     * @param tokenType token 类型（如 "FIELD_NAME", "VALUE_STRING"）
     * @return this（链式调用）
     * @since 1.0.0
     */
    public JsonDeserializationException withTokenType(String tokenType) {
        this.tokenType = tokenType;
        return this;
    }

    /**
     * 设置源码片段（出错位置前后 20 字符的短上下文）
     *
     * @param sourceSnippet 源码片段
     * @return this（链式调用）
     * @since 1.0.0
     */
    public JsonDeserializationException withSourceSnippet(String sourceSnippet) {
        this.sourceSnippet = sourceSnippet;
        return this;
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

        // 提取长上下文片段（前后各 40 字符）
        int start = Math.max(0, position - 40);
        int end = Math.min(json.length(), position + 40);
        StringBuilder sb = new StringBuilder(end - start + 10);
        sb.append(json, start, position);
        sb.append("[HERE]");
        sb.append(json, position, end);
        this.contextSnippet = sb.toString();

        // 提取短源码片段（前后各 20 字符）
        int snippetStart = Math.max(0, position - 20);
        int snippetEnd = Math.min(json.length(), position + 20);
        this.sourceSnippet = json.substring(snippetStart, snippetEnd);
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

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (fieldName != null) {
            sb.append(" [field: ").append(fieldName).append("]");
        }
        if (tokenType != null) {
            sb.append(" [token: ").append(tokenType).append("]");
        }
        if (line > 0 && column > 0) {
            sb.append(" (line ").append(line).append(", column ").append(column).append(")");
        }
        if (contextSnippet != null) {
            sb.append("\n  context: ...").append(contextSnippet).append("...");
        }
        return sb.toString();
    }

    /**
     * 获取位置引用字符串，格式为 {@code (line:X, column:Y)}。
     *
     * <p>如果 {@link #line} 和 {@link column} 尚未设置（值为 -1），
     * 尝试从 {@code position}（通过 {@link #getPosition()} 获取）从 JSON 源中重新计算。
     * 若无法计算则返回 {@code (unknown)}。</p>
     *
     * <p>此方法便于统一获取位置引用格式，无需关心内部是预先计算的行列号还是原始偏移量。</p>
     *
     * @return 格式为 {@code (line:X, column:Y)} 的位置引用字符串，无法确定时返回 {@code (unknown)}
     * @since 1.2.0
     * @see #getLine()
     * @see #getColumn()
     */
    public String getLocationReference() {
        if (line > 0 && column > 0) {
            return "(line:" + line + ", column:" + column + ")";
        }
        // line/column 未设置时返回 unknown（无法从 position 反向推算，无 JSON 上下文字符串）
        return "(unknown)";
    }

    /**
     * 创建缺少必需字段异常
     *
     * @param fieldName 字段名
     * @return 反序列化异常
     */
    public static JsonDeserializationException missingField(String fieldName) {
        return new JsonDeserializationException(MISSING_FIELD,
            "Missing required field: " + fieldName);
    }

    /**
     * 创建类型不匹配异常
     *
     * @param fieldName 字段名
     * @param expected 期望类型
     * @param actual 实际类型
     * @return 反序列化异常
     */
    public static JsonDeserializationException typeMismatch(String fieldName, Class<?> expected, Class<?> actual) {
        return new JsonDeserializationException(TYPE_MISMATCH,
            "Type mismatch for field '" + fieldName + "': expected " +
            (expected != null ? expected.getName() : "null") + " but got " +
            (actual != null ? actual.getName() : "null"));
    }

    /**
     * 创建无效值异常
     *
     * @param fieldName 字段名
     * @param value 无效值
     * @return 反序列化异常
     */
    public static JsonDeserializationException invalidValue(String fieldName, String value) {
        return new JsonDeserializationException(INVALID_VALUE,
            "Invalid value '" + value + "' for field '" + fieldName + "'");
    }

    /**
     * 创建缺少默认构造函数异常
     *
     * @param clazz 类型
     * @return 反序列化异常
     */
    public static JsonDeserializationException noDefaultConstructor(Class<?> clazz) {
        return new JsonDeserializationException(NO_DEFAULT_CONSTRUCTOR,
            "No default constructor for class: " + (clazz != null ? clazz.getName() : "null"));
    }

    /**
     * 创建解析错误异常
     *
     * @param json JSON 字符串
     * @param position 错误位置
     * @return 反序列化异常
     */
    public static JsonDeserializationException parseError(String json, int position) {
        return new JsonDeserializationException(PARSE_ERROR,
            "Failed to parse JSON at position " + position, position, json);
    }

    /**
     * 创建验证错误异常
     *
     * @param message 错误消息
     * @return 反序列化异常
     */
    public static JsonDeserializationException validationError(String message) {
        return new JsonDeserializationException(VALIDATION_ERROR, message);
    }
}
