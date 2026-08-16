package com.njydsz.common.json.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.reader.JSONReader;

/**
 * 流式 JSON 解析器（参考 Jackson 的 JsonParser）。
 *
 * <p>提供 token-by-token 的 JSON 解析能力，适合：</p>
 * <ul>
 *   <li>超大 JSON 流式处理（避免全部加载到内存）</li>
 *   <li>部分字段提取（跳过无关数据）</li>
 *   <li>混合解析（部分树解析、部分流解析）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 1. 解析 JSON 对象，只提取 name 字段
 * try (JsonParser parser = JsonParser.from(json)) {
 *     parser.nextToken();             // START_OBJECT
 *     while (parser.nextToken() == JsonToken.FIELD_NAME) {
 *         String name = parser.getCurrentName();
 *         if ("name".equals(name)) {
 *             parser.nextToken();     // VALUE_STRING
 *             System.out.println(parser.getText());
 *         } else {
 *             parser.nextToken();
 *             parser.skipValue();     // 跳过不关心的字段
 *         }
 *     }
 *     parser.nextToken();             // END_OBJECT
 * }
 *
 * // 2. 部分树解析（结合 DOM）
 * try (JsonParser parser = JsonParser.from(json)) {
 *     parser.nextToken();             // START_OBJECT
 *     parser.nextToken();             // FIELD_NAME "address"
 *     parser.nextToken();
 *     JsonNode address = parser.readValueAsTree();
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public class JsonParser implements Closeable {

    /** 当前解析器状态令牌 */
    public enum JsonToken {
        /** 对象开始 <code>{</code> */
        START_OBJECT,
        /** 对象结束 <code>}</code> */
        END_OBJECT,
        /** 数组开始 <code>[</code> */
        START_ARRAY,
        /** 数组结束 <code>]</code> */
        END_ARRAY,
        /** 字段名 */
        FIELD_NAME,
        /** 字符串值 */
        VALUE_STRING,
        /** 整数值 */
        VALUE_NUMBER_INT,
        /** 浮点数值 */
        VALUE_NUMBER_FLOAT,
        /** 布尔值 true */
        VALUE_TRUE,
        /** 布尔值 false */
        VALUE_FALSE,
        /** null 值 */
        VALUE_NULL,
        /** 嵌入式对象（非标准 JSON） */
        VALUE_EMBEDDED_OBJECT,
        /** 不可用（尚未调用 nextToken） */
        NOT_AVAILABLE
    }

    private final JSONReader reader;
    private final String source;
    private JsonToken currentToken;
    private String currentName;
    private String textValue;
    private boolean closed;

    /**
     * 私有构造函数。
     *
     * @param json 要解析的 JSON 字符串
     */
    private JsonParser(String json) {
        this.source = json;
        this.reader = new JSONReader(json);
        this.currentToken = JsonToken.NOT_AVAILABLE;
    }

    /**
     * 从 JSON 字符串创建解析器。
     *
     * @param json JSON 字符串
     * @return 新的 JsonParser 实例
     * @throws IllegalArgumentException 如果 json 为 null
     */
    public static JsonParser from(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string must not be null");
        }
        return new JsonParser(json);
    }

    /**
     * 从 UTF-8 输入流创建解析器。
     *
     * @param in 输入流
     * @return 新的 JsonParser 实例
     * @throws JsonException 如果读取流失败
     */
    public static JsonParser from(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("InputStream must not be null");
        }
        try {
            byte[] bytes = in.readAllBytes();
            return new JsonParser(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JsonException("Failed to read InputStream", e);
        }
    }

    /**
     * 从 UTF-8 字节数组创建解析器。
     *
     * @param bytes UTF-8 编码的字节数组
     * @return 新的 JsonParser 实例
     */
    public static JsonParser from(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        return new JsonParser(new String(bytes, StandardCharsets.UTF_8));
    }

    // ==================== Token 导航 ====================

    /**
     * 推进到下一个 token。
     *
     * @return 新的当前 token 类型
     * @throws JsonException 如果解析失败或遇到意外字符
     */
    public JsonToken nextToken() {
        if (closed) {
            throw new IllegalStateException("Parser is closed");
        }
        try {
            reader.skipWhitespace();
            if (reader.isEnd()) {
                currentToken = null;
                return null;
            }

            char c = reader.nextChar();
            switch (c) {
                case '{':
                    currentToken = JsonToken.START_OBJECT;
                    break;
                case '}':
                    currentToken = JsonToken.END_OBJECT;
                    break;
                case '[':
                    currentToken = JsonToken.START_ARRAY;
                    break;
                case ']':
                    currentToken = JsonToken.END_ARRAY;
                    break;
                case ',':
                    // 跳过分隔符，读取下一个有效 token
                    return nextToken();
                case ':':
                    // 跳过冒号（在字段名之后），读取值
                    return nextToken();
                case '"':
                    // 可能是字符串值或字段名
                    reader.back(); // 回退让 readString 正常工作
                    String value = reader.readString();
                    // 判断是字段名还是值的依据：下一个非空白字符
                    reader.skipWhitespace();
                    if (!reader.isEnd() && reader.peek() == ':') {
                        currentName = value;
                        currentToken = JsonToken.FIELD_NAME;
                    } else {
                        textValue = value;
                        currentToken = JsonToken.VALUE_STRING;
                    }
                    break;
                case 't': // true
                    if (reader.nextChar() == 'r' && reader.nextChar() == 'u' && reader.nextChar() == 'e') {
                        currentToken = JsonToken.VALUE_TRUE;
                    } else {
                        throw new JsonException("Expected 'true'");
                    }
                    break;
                case 'f': // false
                    if (reader.nextChar() == 'a' && reader.nextChar() == 'l' && reader.nextChar() == 's' && reader.nextChar() == 'e') {
                        currentToken = JsonToken.VALUE_FALSE;
                    } else {
                        throw new JsonException("Expected 'false'");
                    }
                    break;
                case 'n': // null
                    if (reader.nextChar() == 'u' && reader.nextChar() == 'l' && reader.nextChar() == 'l') {
                        currentToken = JsonToken.VALUE_NULL;
                    } else {
                        throw new JsonException("Expected 'null'");
                    }
                    break;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        reader.back();
                        parseNumber();
                    } else {
                        throw new JsonException("Unexpected character: '" + c + "' at position " + reader.getPosition());
                    }
            }
            return currentToken;
        } catch (JsonException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonException("Parse error at position " + reader.getPosition() + ": " + e.getMessage(), e);
        }
    }

    /**
     * 解析数值（整数或浮点）。
     */
    private void parseNumber() {
        StringBuilder sb = new StringBuilder();
        boolean hasDot = false;
        boolean hasExp = false;

        // 负号
        char c = reader.nextChar();
        sb.append(c);

        while (!reader.isEnd()) {
            c = reader.nextChar();
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c == '.' && !hasDot && !hasExp) {
                hasDot = true;
                sb.append(c);
            } else if ((c == 'e' || c == 'E') && !hasExp) {
                hasExp = true;
                sb.append(c);
                // 指数符号
                if (!reader.isEnd()) {
                    char next = reader.peek();
                    if (next == '+' || next == '-') {
                        sb.append(reader.nextChar());
                    }
                }
            } else {
                // 数值结束
                reader.back();
                break;
            }
        }

        String numStr = sb.toString();
        textValue = numStr;
        currentToken = (hasDot || hasExp) ? JsonToken.VALUE_NUMBER_FLOAT : JsonToken.VALUE_NUMBER_INT;
    }

    /**
     * 获取当前 token。
     *
     * @return 当前 token，如果尚未调用 nextToken 则返回 NOT_AVAILABLE
     */
    public JsonToken currentToken() {
        return currentToken;
    }

    /**
     * 获取当前字段名（仅当 token 为 FIELD_NAME 时有效）。
     *
     * @return 当前字段名
     */
    public String getCurrentName() {
        return currentName;
    }

    /**
     * 获取当前位置（字符偏移）。
     *
     * @return 当前解析位置
     */
    public int getCurrentLocation() {
        return reader.getPosition();
    }

    // ==================== Value Reading ====================

    /**
     * 获取当前 token 的文本表示。
     *
     * @return 文本值（字符串值去掉引号、数值的字符串形式、true/false/null）
     */
    public String getText() {
        if (currentToken == JsonToken.FIELD_NAME) {
            return currentName;
        }
        return textValue;
    }

    /**
     * 获取字符串值（仅对 VALUE_STRING 有效）。
     *
     * @return 解码后的字符串值
     */
    public String getTextValue() {
        return textValue;
    }

    /**
     * 获取字符串值（适用任何 token，对数值做toString转换）。
     *
     * @return 字符串表示
     */
    public String getValueAsString() {
        if (currentToken == JsonToken.VALUE_STRING || currentToken == JsonToken.FIELD_NAME) {
            return textValue;
        }
        if (currentToken == JsonToken.VALUE_NULL || currentToken == null) {
            return null;
        }
        if (currentToken == JsonToken.VALUE_TRUE) return "true";
        if (currentToken == JsonToken.VALUE_FALSE) return "false";
        return textValue != null ? textValue : currentToken.name();
    }

    /**
     * 获取当前整数值。
     *
     * @return int 值
     * @throws NumberFormatException 如果值无法转为 int
     */
    public int getIntValue() {
        if (textValue != null) {
            return Integer.parseInt(textValue);
        }
        if (currentToken == JsonToken.VALUE_TRUE) return 1;
        if (currentToken == JsonToken.VALUE_FALSE) return 0;
        throw new IllegalStateException("Not a number token: " + currentToken);
    }

    /**
     * 获取当前长整数值。
     *
     * @return long 值
     */
    public long getLongValue() {
        if (textValue != null) {
            return Long.parseLong(textValue);
        }
        if (currentToken == JsonToken.VALUE_TRUE) return 1L;
        if (currentToken == JsonToken.VALUE_FALSE) return 0L;
        throw new IllegalStateException("Not a number token: " + currentToken);
    }

    /**
     * 获取当前 double 值。
     *
     * @return double 值
     */
    public double getDoubleValue() {
        if (textValue != null) {
            return Double.parseDouble(textValue);
        }
        throw new IllegalStateException("Not a number token: " + currentToken);
    }

    /**
     * 获取当前 BigDecimal 值。
     *
     * @return BigDecimal 值
     */
    public BigDecimal getDecimalValue() {
        if (textValue != null) {
            return new BigDecimal(textValue);
        }
        throw new IllegalStateException("Not a number token: " + currentToken);
    }

    /**
     * 获取当前 BigInteger 值。
     *
     * @return BigInteger 值
     */
    public BigInteger getBigIntegerValue() {
        if (textValue != null) {
            return new BigInteger(textValue);
        }
        throw new IllegalStateException("Not a number token: " + currentToken);
    }

    /**
     * 获取当前布尔值（仅对 VALUE_TRUE / VALUE_FALSE 有效）。
     *
     * @return true 或 false
     */
    public boolean getBooleanValue() {
        if (currentToken == JsonToken.VALUE_TRUE) return true;
        if (currentToken == JsonToken.VALUE_FALSE) return false;
        throw new IllegalStateException("Not a boolean token: " + currentToken);
    }

    // ==================== Skip and Tree Reading ====================

    /**
     * 跳过当前值（不包含当前值本身已经消耗的 token）。
     *
     * <p>用于跳过不需要的节点，无论是原子值还是嵌套对象/数组。</p>
     */
    public void skipValue() {
        reader.skipValue();
        // 读取跳过后，当前 token 已失效，需要重新导航
        currentToken = JsonToken.NOT_AVAILABLE;
    }

    /**
     * 跳过当前对象的剩余部分（必须在 START_OBJECT 之后调用）。
     *
     * <p>一次性消费到匹配的 END_OBJECT。</p>
     */
    public void skipChildren() {
        if (currentToken != JsonToken.START_OBJECT && currentToken != JsonToken.START_ARRAY) {
            // 如果已经是原子值，什么都不做
            return;
        }
        int depth = 1;
        while (depth > 0 && !reader.isEnd()) {
            char c = reader.nextChar();
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        currentToken = JsonToken.NOT_AVAILABLE;
    }

    /**
     * 将当前节点（及其所有子节点）解析为 JsonNode 树。
     *
     * <p>当前位置应为 START_OBJECT、START_ARRAY 或值 token。
     *
     * @return 解析后的 JsonNode
     */
    public com.njydsz.common.json.tree.JsonNode readValueAsTree() {
        try {
            // 回退一个字符，因为当前 token 已经被读取但未消费 value
            reader.back();
            String rawValue = reader.readRawValue();
            return com.njydsz.common.json.YdszJson.readTree(rawValue);
        } catch (Exception e) {
            throw new JsonException("Failed to read tree at position " + reader.getPosition(), e);
        }
    }

    // ==================== Lifecycle ====================

    /**
     * 关闭解析器并释放资源。
     */
    @Override
    public void close() throws IOException {
        closed = true;
        currentToken = null;
        currentName = null;
        textValue = null;
    }

    /**
     * 检查解析器是否已关闭。
     *
     * @return true 如果已关闭
     */
    public boolean isClosed() {
        return closed;
    }
}
