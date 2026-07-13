package com.njydsz.pmis.common.json.pointer;

import com.njydsz.pmis.common.json.exception.YdszJsonException;
import com.njydsz.pmis.common.json.parser.YdszJsonParser;

import java.util.List;
import java.util.Map;

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
 * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class JsonPointer {

    private final String pointer;

    private final String[] tokens;

    /**
     * 创建 JSON Pointer
     *
     * @param pointer JSON Pointer 字符串（必须以 "/" 开头或为空）
     * @throws YdszJsonException 如果格式无效
     */
    public JsonPointer(String pointer) {
        if (pointer == null) {
            throw new YdszJsonException("JSON Pointer cannot be null");
        }
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new YdszJsonException("JSON Pointer must start with '/' or be empty, but got: " + pointer);
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

    /**
     * 评估 JSON Pointer，从 JSON 文档中提取值
     *
     * @param json JSON 文档字符串
     * @return 指针指向的值
     * @throws YdszJsonException 如果路径不存在或格式错误
     */
    public Object evaluate(String json) {
        if (pointer.isEmpty()) {
            return json;
        }

        Object parsedValue = YdszJsonParser.parse(json);
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
                throw new YdszJsonException("JSON Pointer path not found: " + currentToken + " in " + pointer);
            }
            return evaluateInternal(map.get(currentToken), tokenIndex + 1);
        } else if (currentNode instanceof List) {
            List<?> list = (List<?>) currentNode;
            try {
                int index = Integer.parseInt(currentToken);
                if (index < 0 || index >= list.size()) {
                    throw new YdszJsonException("JSON Pointer array index out of bounds: " + currentToken);
                }
                return evaluateInternal(list.get(index), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new YdszJsonException("JSON Pointer array index must be integer: " + currentToken);
            }
        } else {
            throw new YdszJsonException("JSON Pointer cannot traverse through non-object/array at: " + currentToken);
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

    @Override
    public String toString() {
        return "JsonPointer{pointer='" + pointer + "'}";
    }
}
