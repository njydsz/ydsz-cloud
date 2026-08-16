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
                case '"':\n                    // 可能是字符串值或字段名\n                    reader.back(); // 回退让 readString 正常工作\n                    String value = reader.readString();\n                    // 判断是字段名还是值的依据：下一个非空白字符\n                    reader.skipWhitespace();\n                    if (!reader.isEnd() && reader.peek() == ':') {\n                        currentName = value;\n                        currentToken = JsonToken.FIELD_NAME;\n                    } else {\n                        textValue = value;\n                        currentToken = JsonToken.VALUE_STRING;\n                    }\n                    break;\n                case 't': // true\n                    if (reader.nextChar() == 'r' && reader.nextChar() == 'u' && reader.nextChar() == 'e') {\n                        currentToken = JsonToken.VALUE_TRUE;\n                    } else {\n                        throw new JsonException("Expected 'true'");\n                    }\n                    break;\n                case 'f': // false\n                    if (reader.nextChar() == 'a' && reader.nextChar() == 'l' && reader.nextChar() == 's' && reader.nextChar() == 'e') {\n                        currentToken = JsonToken.VALUE_FALSE;\n                    } else {\n                        throw new JsonException("Expected 'false'");\n                    }\n                    break;\n                case 'n': // null\n                    if (reader.nextChar() == 'u' && reader.nextChar() == 'l' && reader.nextChar() == 'l') {\n                        currentToken = JsonToken.VALUE_NULL;\n                    } else {\n                        throw new JsonException("Expected 'null'");\n                    }\n                    break;\n                default:\n                    if (c == '-' || (c >= '0' && c <= '9')) {\n                        reader.back();\n                        parseNumber();\n                    } else {\n                        throw new JsonException("Unexpected character: '" + c + "' at position " + reader.getPosition());\n                    }\n            }\n            return currentToken;\n        } catch (JsonException e) {\n            throw e;\n        } catch (Exception e) {\n            throw new JsonException("Parse error at position " + reader.getPosition() + ": " + e.getMessage(), e);\n        }\n    }\n\n    /**\n     * 解析数值（整数或浮点）。\n     */\n    private void parseNumber() {\n        StringBuilder sb = new StringBuilder();\n        boolean hasDot = false;\n        boolean hasExp = false;\n\n        // 负号\n        char c = reader.nextChar();\n        sb.append(c);\n\n        while (!reader.isEnd()) {\n            c = reader.nextChar();\n            if (c >= '0' && c <= '9') {\n                sb.append(c);\n            } else if (c == '.' && !hasDot && !hasExp) {\n                hasDot = true;\n                sb.append(c);\n            } else if ((c == 'e' || c == 'E') && !hasExp) {\n                hasExp = true;\n                sb.append(c);\n                // 指数符号\n                if (!reader.isEnd()) {\n                    char next = reader.peek();\n                    if (next == '+' || next == '-') {\n                        sb.append(reader.nextChar());\n                    }\n                }\n            } else {\n                // 数值结束\n                reader.back();\n                break;\n            }\n        }\n\n        String numStr = sb.toString();\n        textValue = numStr;\n        currentToken = (hasDot || hasExp) ? JsonToken.VALUE_NUMBER_FLOAT : JsonToken.VALUE_NUMBER_INT;\n    }\n\n    /**\n     * 获取当前 token。\n     *\n     * @return 当前 token，如果尚未调用 nextToken 则返回 NOT_AVAILABLE\n     */\n    public JsonToken currentToken() {\n        return currentToken;\n    }\n\n    /**\n     * 获取当前字段名（仅当 token 为 FIELD_NAME 时有效）。\n     *\n     * @return 当前字段名\n     */\n    public String getCurrentName() {\n        return currentName;\n    }\n\n    /**\n     * 获取当前位置（字符偏移）。\n     *\n     * @return 当前解析位置\n     */\n    public int getCurrentLocation() {\n        return reader.getPosition();\n    }\n\n    // ==================== Value Reading ====================\n\n    /**\n     * 获取当前 token 的文本表示。\n     *\n     * @return 文本值（字符串值去掉引号、数值的字符串形式、true/false/null）\n     */\n    public String getText() {\n        if (currentToken == JsonToken.FIELD_NAME) {\n            return currentName;\n        }\n        return textValue;\n    }\n\n    /**\n     * 获取字符串值（仅对 VALUE_STRING 有效）。\n     *\n     * @return 解码后的字符串值\n     */\n    public String getTextValue() {\n        return textValue;\n    }\n\n    /**\n     * 获取字符串值（适用任何 token，对数值做toString转换）。\n     *\n     * @return 字符串表示\n     */\n    public String getValueAsString() {\n        if (currentToken == JsonToken.VALUE_STRING || currentToken == JsonToken.FIELD_NAME) {\n            return textValue;\n        }\n        if (currentToken == JsonToken.VALUE_NULL || currentToken == null) {\n            return null;\n        }\n        if (currentToken == JsonToken.VALUE_TRUE) return "true";\n        if (currentToken == JsonToken.VALUE_FALSE) return "false";\n        return textValue != null ? textValue : currentToken.name();\n    }\n\n    /**\n     * 获取当前整数值。\n     *\n     * @return int 值\n     * @throws NumberFormatException 如果值无法转为 int\n     */\n    public int getIntValue() {\n        if (textValue != null) {\n            return Integer.parseInt(textValue);\n        }\n        if (currentToken == JsonToken.VALUE_TRUE) return 1;\n        if (currentToken == JsonToken.VALUE_FALSE) return 0;\n        throw new IllegalStateException("Not a number token: " + currentToken);\n    }\n\n    /**\n     * 获取当前长整数值。\n     *\n     * @return long 值\n     */\n    public long getLongValue() {\n        if (textValue != null) {\n            return Long.parseLong(textValue);\n        }\n        if (currentToken == JsonToken.VALUE_TRUE) return 1L;\n        if (currentToken == JsonToken.VALUE_FALSE) return 0L;\n        throw new IllegalStateException("Not a number token: " + currentToken);\n    }\n\n    /**\n     * 获取当前 double 值。\n     *\n     * @return double 值\n     */\n    public double getDoubleValue() {\n        if (textValue != null) {\n            return Double.parseDouble(textValue);\n        }\n        throw new IllegalStateException("Not a number token: " + currentToken);\n    }\n\n    /**\n     * 获取当前 BigDecimal 值。\n     *\n     * @return BigDecimal 值\n     */\n    public BigDecimal getDecimalValue() {\n        if (textValue != null) {\n            return new BigDecimal(textValue);\n        }\n        throw new IllegalStateException("Not a number token: " + currentToken);\n    }\n\n    /**\n     * 获取当前 BigInteger 值。\n     *\n     * @return BigInteger 值\n     */\n    public BigInteger getBigIntegerValue() {\n        if (textValue != null) {\n            return new BigInteger(textValue);\n        }\n        throw new IllegalStateException("Not a number token: " + currentToken);\n    }\n\n    /**\n     * 获取当前布尔值（仅对 VALUE_TRUE / VALUE_FALSE 有效）。\n     *\n     * @return true 或 false\n     */\n    public boolean getBooleanValue() {\n        if (currentToken == JsonToken.VALUE_TRUE) return true;\n        if (currentToken == JsonToken.VALUE_FALSE) return false;\n        throw new IllegalStateException("Not a boolean token: " + currentToken);\n    }\n\n    // ==================== Skip and Tree Reading ====================\n\n    /**\n     * 跳过当前值（不包含当前值本身已经消耗的 token）。\n     *\n     * <p>用于跳过不需要的节点，无论是原子值还是嵌套对象/数组。</p>\n     */\n    public void skipValue() {\n        reader.skipValue();\n        // 读取跳过后，当前 token 已失效，需要重新导航\n        currentToken = JsonToken.NOT_AVAILABLE;\n    }\n\n    /**\n     * 跳过当前对象的剩余部分（必须在 START_OBJECT 之后调用）。\n     *\n     * <p>一次性消费到匹配的 END_OBJECT。</p>\n     */\n    public void skipChildren() {\n        if (currentToken != JsonToken.START_OBJECT && currentToken != JsonToken.START_ARRAY) {\n            // 如果已经是原子值，什么都不做\n            return;\n        }\n        int depth = 1;\n        while (depth > 0 && !reader.isEnd()) {\n            char c = reader.nextChar();\n            if (c == '{' || c == '[') {\n                depth++;\n            } else if (c == '}' || c == ']') {\n                depth--;\n            }\n        }\n        currentToken = JsonToken.NOT_AVAILABLE;\n    }\n\n    /**\n     * 将当前节点（及其所有子节点）解析为 JsonNode 树。\n     *\n     * <p>当前位置应为 START_OBJECT、START_ARRAY 或值 token。\n     *\n     * @return 解析后的 JsonNode\n     */\n    public com.njydsz.common.json.tree.JsonNode readValueAsTree() {\n        try {\n            // 回退一个字符，因为当前 token 已经被读取但未消费 value\n            reader.back();\n            String rawValue = reader.readRawValue();\n            return com.njydsz.common.json.YdszJson.readTree(rawValue);\n        } catch (Exception e) {\n            throw new JsonException("Failed to read tree at position " + reader.getPosition(), e);\n        }\n    }\n\n    // ==================== Lifecycle ====================\n\n    /**\n     * 关闭解析器并释放资源。\n     */\n    @Override\n    public void close() throws IOException {\n        closed = true;\n        currentToken = null;\n        currentName = null;\n        textValue = null;\n    }\n\n    /**\n     * 检查解析器是否已关闭。\n     *\n     * @return true 如果已关闭\n     */\n    public boolean isClosed() {\n        return closed;\n    }\n}\n