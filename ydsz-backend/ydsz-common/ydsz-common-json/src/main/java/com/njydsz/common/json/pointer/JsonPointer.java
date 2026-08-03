package com.njydsz.common.json.pointer;

import java.util.List;
import java.util.Map;

import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.parser.JsonParserUtil;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Pointer 实现（RFC 6901）
 *
 * <p>JSON Pointer 定义了一种字符串语法，用于标识 JSON 文档中的特定值。</p>
 *
 * <p><b>语法示例：</b></p>
 * <ul>
 *   <li>"" - 文档根节点</li>
 *   <li>"/foo" - 对象的 "foo" 属性</li>
 *   <li>"/foo/0" - 数组的第 1 个元素</li>
 *   <li>"/a~1b" - 对象的 "a/b" 属性（~1 表示 /）</li>
 *   <li>"/a~0b" - 对象的 "a~b" 属性（~0 表示 ~）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * String json = "{\"foo\":{\"bar\":[1,2,3]}}";
 * JsonPointer pointer = new JsonPointer("/foo/bar/0");
 * Object value = pointer.evaluate(json);
 * </pre>
 *
 * @author ydsz-team
 * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>
 * @since 1.0.0
 */
@Experimental("JSON Pointer (RFC 6901) 属于独立工具域，非核心序列化能力")
public final class JsonPointer {

    private final String pointer;

    private final String[] tokens;

    /**
     * 创建 JSON Pointer
     *
     * @param pointer JSON Pointer 字符串（必须以 "/" 开头或为空）
     * @throws JsonException 如果格式无效
     */
    public JsonPointer(String pointer) {
        if (pointer == null) {
            throw new JsonException("JSON Pointer cannot be null");
        }
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new JsonException("JSON Pointer must start with '/' or be empty, but got: " + pointer);
        }
        this.pointer = pointer;
        this.tokens = parseTokens(pointer);
    }

    private static String[] parseTokens(String pointer) {
        if (pointer.isEmpty()) {
            return new String[0];
        }
        String[] parts = pointer.substring(1).split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = unescape(parts[i]);
        }
        return parts;
    }

    private static String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    /**
     * 评估 JSON Pointer，从 JSON 文档中提取值
     *
     * @param json JSON 文档字符串
     * @return 指针指向的值
     * @throws JsonException 如果路径不存在或格式错误
     */
    public Object evaluate(String json) {
        if (pointer.isEmpty()) {
            return json;
        }

        Object parsedValue = JsonParserUtil.parse(json);
        return evaluateInternal(parsedValue, 0);
    }

    private Object evaluateInternal(Object currentNode, int tokenIndex) {
        if (tokenIndex >= tokens.length) {
            return currentNode;
        }

        String currentToken = tokens[tokenIndex];

        if (currentNode instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) currentNode;
            if (!map.containsKey(currentToken)) {
                throw new JsonException("JSON Pointer path not found: " + currentToken + " in " + pointer);
            }
            return evaluateInternal(map.get(currentToken), tokenIndex + 1);
        } else if (currentNode instanceof List) {
            List<?> list = (List<?>) currentNode;
            try {
                int index = Integer.parseInt(currentToken);
                if (index < 0 || index >= list.size()) {
                    throw new JsonException("JSON Pointer array index out of bounds: " + currentToken);
                }
                return evaluateInternal(list.get(index), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new JsonException("JSON Pointer array index must be integer: " + currentToken);
            }
        } else {
            throw new JsonException("JSON Pointer cannot traverse through non-object/array at: " + currentToken);
        }
    }

    /**
     * 获取原始 JSON Pointer 字符串
     *
     * @return JSON Pointer 字符串
     */
    public String getPointer() {
        return pointer;
    }

    /**
     * 在当前 Pointer 后面追加路径段，返回新的 JsonPointer。
     *
     * <p>例如 {@code "/foo/bar".append("baz")} 返回 {@code "/foo/bar/baz"}。</p>
     *
     * @param segment 要追加的路径段
     * @return 新的 JsonPointer
     */
    public JsonPointer append(String segment) {
        if (segment == null || segment.isEmpty()) {
            return this;
        }
        String escaped = segment.replace("~", "~0").replace("/", "~1");
        String newPointer = this.pointer.isEmpty() ? "/" + escaped : this.pointer + "/" + escaped;
        return new JsonPointer(newPointer);
    }

    /**
     * 获取最后一个路径段（token），不包含前导 "/"。
     *
     * <p>例如 {@code "/foo/bar".tail()} 返回 {@code "bar"}，{@code ""}.tail() 返回 {@code ""}。</p>
     *
     * @return 最后一个 token，无 token 时返回空字符串
     */
    public String tail() {
        if (tokens.length == 0) {
            return "";
        }
        // tokens 在 parseTokens 阶段已完成 unescape，此处直接返回，避免双重反转义
        return tokens[tokens.length - 1];
    }

    /**
     * 去掉最后一个路径段，返回新的 JsonPointer（父路径）。
     *
     * <p>例如 {@code "/foo/bar".head()} 返回 {@code "/foo"}，{@code "/foo"}.head() 返回 {@code ""}。</p>
     *
     * @return 新的 JsonPointer，去掉最后一个 token
     */
    public JsonPointer head() {
        if (tokens.length <= 1) {
            return new JsonPointer("");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            // 重新 escape token（~ 和 / 需要还原为 ~0 / ~1），避免 parseTokens 双重反转义
            String escaped = escape(tokens[i]);
            sb.append('/').append(escaped);
        }
        return new JsonPointer(sb.toString());
    }

    /**
     * 获取当前 JSON Pointer 的路径深度（即 token 数量）。
     *
     * <p>空指针（""）深度为 0，{@code "/foo"} 深度为 1，{@code "/foo/bar"} 深度为 2。
     * 可用于快速判断指针层级，或作为递归遍历 {@code evaluateInternal} 的边界条件。</p>
     *
     * @return token 数量，恒为非负整数
     */
    public int depth() {
        return tokens.length;
    }

    @Override
    public String toString() {
        return "JsonPointer{pointer='" + pointer + "'}";
    }
}
