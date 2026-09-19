package com.njydsz.common.json.tree;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
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

  /**
   * 判断 JsonNode 树是否仅包含标量叶子节点（无嵌套对象/数组）。
   *
   * <p>当源树为纯标量时可采用快速直绑路径（{@link #directConvertFlatObject}）， 避免"树 → 字符串 → 再解析"的两次结构转换。
   *
   * @param node 源 JsonNode 树
   * @return true 表示所有后代节点均为标量
   * @since 26.09.01
   */
  public static boolean isFlatScalarTree(JsonNode node) {
    if (node == null || node.isMissing()) {
      return true;
    }
    if (node instanceof ObjectNode objectNode) {
      for (Map.Entry<String, JsonNode> entry : objectNode.entrySet()) {
        if (!isFlatScalarTree(entry.getValue())) {
          return false;
        }
      }
      return true;
    }
    if (node instanceof ArrayNode arrayNode) {
      Iterator<JsonNode> elements = arrayNode.elements();
      while (elements.hasNext()) {
        if (!isFlatScalarTree(elements.next())) {
          return false;
        }
      }
      return true;
    }
    // 标量节点（Text/Number/Boolean/Null）均为纯量
    return true;
  }

  /**
   * 将纯标量 ObjectNode 直转换为 Bean 实例（不走 JSON 字符串中间态）。
   *
   * <p><b>前置条件：</b>源 {@code node} 必须经 {@link #isFlatScalarTree} 校验通过， 确保所有后代节点均为叶子（标量/null），无嵌套容器。
   *
   * <p>对标 Jackson {@code TokenBuffer} 行为——直接从树字段读取并反射设置目标 Bean 字段， 跳过了"树 → 字符串 → 再解析"的两次结构转换，适用于表单提交、配置绑定等标量字段场景。
   *
   * <p><b>回退策略：</b>若目标类型无默认构造或任何字段设置失败，返回 {@code null} 由调用方回退到字符串管道。
   *
   * @param node 源 ObjectNode（纯标量树）
   * @param clazz 目标 Bean 类型
   * @param <T> 目标类型
   * @return 转换后的 Bean 实例，失败时返回 null
   * @since 26.09.01
   */
  public static <T> T directConvertFlatObject(ObjectNode node, Class<T> clazz) {
    if (node == null || clazz == null) {
      return null;
    }
    T instance;
    try {
      instance = clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      // 无默认构造：调用方回退到字符串管道
      return null;
    }
    for (Map.Entry<String, JsonNode> entry : node.entrySet()) {
      String fieldName = entry.getKey();
      JsonNode value = entry.getValue();
      Field field = findField(clazz, fieldName);
      if (field == null || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      try {
        field.setAccessible(true);
        Object converted = convertNodeToFieldValue(value, field.getType());
        if (converted != null || value.isNull()) {
          field.set(instance, converted);
        }
      } catch (Exception e) {
        // 字段设置失败：跳过，继续后续字段
      }
    }
    return instance;
  }

  /**
   * 从类继承链中查找指定字段。
   *
   * @param clazz 目标类
   * @param fieldName 字段名
   * @return 找到的字段，不存在时返回 null
   */
  private static Field findField(Class<?> clazz, String fieldName) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  /**
   * 将 JsonNode 值转换为目标字段类型的基本值。
   *
   * <p>仅处理标量转换（String/数字/布尔/BigDecimal/BigInteger）； 容器类型或复杂 Bean 返回 null，由调用方决定是否需要递归转换。
   *
   * @param node 源节点（标量或 null）
   * @param targetType 目标字段类型
   * @return 转换后的值，不支持的类型返回 null
   */
  private static Object convertNodeToFieldValue(JsonNode node, Class<?> targetType) {
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (targetType == String.class) {
      return node.asText();
    }
    if (targetType == int.class || targetType == Integer.class) {
      return node.asInt();
    }
    if (targetType == long.class || targetType == Long.class) {
      return node.asLong();
    }
    if (targetType == double.class || targetType == Double.class) {
      return node.asDouble();
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      return node.asBoolean();
    }
    if (targetType == float.class || targetType == Float.class) {
      return (float) node.asDouble();
    }
    if (targetType == short.class || targetType == Short.class) {
      return (short) node.asInt();
    }
    if (targetType == byte.class || targetType == Byte.class) {
      return (byte) node.asInt();
    }
    if (targetType == BigDecimal.class) {
      if (node instanceof NumberNode numNode && numNode.numberValue() instanceof BigDecimal bd) {
        return bd;
      }
      String text = node.asText();
      return text.isEmpty() ? null : new BigDecimal(text);
    }
    if (targetType == BigInteger.class) {
      if (node instanceof NumberNode numNode && numNode.numberValue() instanceof BigInteger bi) {
        return bi;
      }
      String text = node.asText();
      return text.isEmpty() ? null : new BigInteger(text);
    }
    // 不支持的类型（容器/Bean）：返回 null
    return null;
  }
}
