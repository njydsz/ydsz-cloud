package com.njydsz.common.json.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 树模型转换器
 *
 * <p>将解析后的 Map/List 结构转换为 JsonNode 树模型， 支持递归转换嵌套的 JSON 结构。
 *
 * <p><b>支持的类型映射：</b>
 *
 * <ul>
 *   <li>Map → ObjectNode
 *   <li>List → ArrayNode
 *   <li>String → TextNode
 *   <li>Number → NumberNode
 *   <li>Boolean → BooleanNode
 *   <li>null → NullNode
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * Object parsed = JsonParserUtil.parse("{\"name\":\"John\"}");
 * JsonNode tree = TreeConverter.convertToJsonNode(parsed);
 * String name = tree.get("name").asText(); // "John"
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
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
      Map<String, JsonNode> fields = new LinkedHashMap<>(16);
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
      List<JsonNode> elements = new ArrayList<>(16);
      for (Object item : listValue) {
        elements.add(convertToJsonNode(item));
      }
      return new ArrayNode(elements);
    }
    return TextNode.of(value.toString());
  }

  /**
   * 将 JsonNode 树转换为 Java 对象结构（Map/List/标量）。
   *
   * <p>F-2 直绑基础：{@code treeToValue} 据此跳过"树 → 字符串 → 再解析"的两次 结构转换（对标 Jackson TokenBuffer）。转换映射与
   * {@link #convertToJsonNode(Object)} 互逆：ObjectNode → Map、ArrayNode → List、叶子节点 → 对应标量、null →
   * null。
   *
   * @param node JsonNode 树
   * @return 对应的 Java 对象结构
   */
  public static Object convertToJavaObject(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node instanceof ObjectNode objectNode) {
      Map<String, Object> map = new LinkedHashMap<>(16);
      for (Map.Entry<String, JsonNode> entry : objectNode.entrySet()) {
        map.put(entry.getKey(), convertToJavaObject(entry.getValue()));
      }
      return map;
    }
    if (node instanceof ArrayNode arrayNode) {
      List<Object> list = new ArrayList<>(arrayNode.size());
      Iterator<JsonNode> elements = arrayNode.elements();
      while (elements.hasNext()) {
        list.add(convertToJavaObject(elements.next()));
      }
      return list;
    }
    if (node instanceof NumberNode numberNode) {
      return numberNode.numberValue();
    }
    if (node instanceof BooleanNode) {
      return node.asBoolean();
    }
    if (node instanceof TextNode) {
      return node.asText();
    }
    // 未知节点类型回退为字符串表示
    return node.toString();
  }
}
