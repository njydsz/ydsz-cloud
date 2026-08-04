package com.remisoft.common.json.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 树模型转换器
 *
 * <p>将解析后的 Map/List 结构转换为 JsonNode 树模型，
 * 支持递归转换嵌套的 JSON 结构。</p>
 *
 * <p><b>支持的类型映射：</b></p>
 * <ul>
 *   <li>Map → ObjectNode</li>
 *   <li>List → ArrayNode</li>
 *   <li>String → TextNode</li>
 *   <li>Number → NumberNode</li>
 *   <li>Boolean → BooleanNode</li>
 *   <li>null → NullNode</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * Object parsed = JsonParserUtil.parse("{\"name\":\"John\"}");
 * JsonNode tree = TreeConverter.convertToJsonNode(parsed);
 * String name = tree.get("name").asText(); // "John"
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class TreeConverter {

    private TreeConverter() {
        throw new UnsupportedOperationException();
    }

    /**
     * 将解析后的对象转换为 JsonNode 树
     *
     * @param value 解析后的对象（Map/List/String/Number/Boolean/null）
     * @return JsonNode 树
     */
    public static JsonNode convertToJsonNode(Object value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value instanceof String) {
            return TextNode.of((String) value);
        }
        if (value instanceof Number) {
            return new NumberNode((Number) value);
        }
        if (value instanceof Boolean) {
            return BooleanNode.of((Boolean) value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, JsonNode> fields = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String key;
                if (entry.getKey() instanceof String) {
                    key = (String) entry.getKey();
                } else {
                    key = String.valueOf(entry.getKey());
                }
                fields.put(key, convertToJsonNode(entry.getValue()));
            }
            return new ObjectNode(fields);
        }
        if (value instanceof List<?> listValue) {
            List<JsonNode> elements = new ArrayList<>();
            for (Object item : listValue) {
                elements.add(convertToJsonNode(item));
            }
            return new ArrayNode(elements);
        }
        return TextNode.of(value.toString());
    }
}
