package com.njydsz.pmis.common.json.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 树模型转换器
 *
 * <p>将解析后的 Map/List 结构转换为 JsonNode 树模型</p>
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
                fields.put((String) entry.getKey(), convertToJsonNode(entry.getValue()));
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
