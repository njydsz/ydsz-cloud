package com.njydsz.common.json.tree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 对象节点
 *
 * <p>对标 Jackson ObjectNode / FastJSON2 JSONObject，表示一个 JSON 对象（key-value 结构）， 内部使用 LinkedHashMap
 * 保持字段插入顺序。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>支持动态添加/删除字段
 *   <li>支持链式调用（Builder 模式）
 *   <li>提供类型安全的字段访问（getString/getInteger/getLong/getBoolean 等）
 *   <li>支持转换为 Map 和 JSON 字符串
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * ObjectNode node = new ObjectNode();
 * node.put("name", "John")
 *     .put("age", 30)
 *     .put("active", true);
 *
 * // 获取字段
 * String name = node.getString("name");
 * int age = node.getIntValue("age");
 * ObjectNode sub = node.getObjectNode("sub");
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ObjectNode extends JsonNode {

  private final LinkedHashMap<String, JsonNode> fields;

  /** 创建空的 JSON 对象节点 */
  public ObjectNode() {
    this.fields = new LinkedHashMap<>();
  }

  /**
   * 使用现有字段集合创建 JSON 对象节点
   *
   * @param fields 字段集合
   */
  public ObjectNode(Map<String, JsonNode> fields) {
    this.fields = new LinkedHashMap<>(fields);
  }

  /**
   * 添加字符串字段
   *
   * @param name 字段名
   * @param value 字段值，null 会被转换为 NullNode
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, String value) {
    fields.put(name, value != null ? new TextNode(value) : NullNode.getInstance());
    return this;
  }

  /**
   * 添加整数字段
   *
   * @param name 字段名
   * @param value 字段值
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, int value) {
    fields.put(name, new NumberNode(value));
    return this;
  }

  /**
   * 添加长整数字段
   *
   * @param name 字段名
   * @param value 字段值
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, long value) {
    fields.put(name, new NumberNode(value));
    return this;
  }

  /**
   * 添加双精度浮点数字段
   *
   * @param name 字段名
   * @param value 字段值
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, double value) {
    fields.put(name, new NumberNode(value));
    return this;
  }

  /**
   * 添加布尔字段
   *
   * @param name 字段名
   * @param value 字段值
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, boolean value) {
    fields.put(name, BooleanNode.of(value));
    return this;
  }

  /**
   * 添加 JSON 节点字段
   *
   * @param name 字段名
   * @param node 节点值，null 会被转换为 NullNode
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, JsonNode node) {
    fields.put(name, node != null ? node : NullNode.getInstance());
    return this;
  }

  /**
   * 设置字段值（如果字段已存在则更新，不存在则添加）
   *
   * @param name 字段名
   * @param node 节点值
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode set(String name, JsonNode node) {
    return put(name, node);
  }

  /**
   * 移除指定字段
   *
   * @param name 要移除的字段名
   * @return 被移除的节点，如果字段不存在返回 null
   */
  public JsonNode remove(String name) {
    return fields.remove(name);
  }

  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public JsonNode get(String fieldName) {
    JsonNode node = fields.get(fieldName);
    return node != null ? node : MissingNode.getInstance();
  }

  @Override
  public String asText() {
    return toString();
  }

  @Override
  public String asText(String defaultValue) {
    return defaultValue;
  }

  @Override
  public int size() {
    return fields.size();
  }

  @Override
  public boolean has(String fieldName) {
    return fields.containsKey(fieldName);
  }

  @Override
  public Iterator<String> fieldNames() {
    return Collections.unmodifiableSet(fields.keySet()).iterator();
  }

  @Override
  public Map<String, JsonNode> asMap() {
    return Collections.unmodifiableMap(fields);
  }

  /**
   * 获取字段的键值对视图（P1 能力补齐，对标 Jackson {@code fields()}）。
   *
   * @return 不可变的字段条目集合
   */
  public Set<Map.Entry<String, JsonNode>> fields() {
    return Collections.unmodifiableSet(fields.entrySet());
  }

  @Override
  public JsonNode findValue(String fieldName) {
    JsonNode direct = fields.get(fieldName);
    if (direct != null) {
      return direct;
    }
    for (JsonNode child : fields.values()) {
      if (child != null) {
        JsonNode found = child.findValue(fieldName);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  @Override
  public void findValues(String fieldName, List<JsonNode> found) {
    JsonNode direct = fields.get(fieldName);
    if (direct != null) {
      found.add(direct);
    }
    for (JsonNode child : fields.values()) {
      if (child != null) {
        child.findValues(fieldName, found);
      }
    }
  }

  @Override
  public Object asValue() {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
      JsonNode node = entry.getValue();
      result.put(entry.getKey(), node != null ? node.asValue() : null);
    }
    return result;
  }

  /**
   * 深拷贝当前 ObjectNode 及其所有嵌套子节点。
   *
   * @return 全新的 ObjectNode 副本
   */
  @Override
  public ObjectNode deepCopy() {
    ObjectNode copy = new ObjectNode();
    for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
      JsonNode node = entry.getValue();
      copy.fields.put(entry.getKey(), node != null ? node.deepCopy() : NullNode.getInstance());
    }
    return copy;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    boolean first = true;
    for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"');
      escapeJsonString(sb, entry.getKey());
      sb.append('"');
      sb.append(':');
      sb.append(entry.getValue().toString());
    }
    sb.append('}');
    return sb.toString();
  }

  /**
   * JSON 字符串转义处理
   *
   * <p>将特殊字符转换为转义序列，确保生成合法的 JSON 字符串。
   *
   * @param sb 字符串构建器
   * @param text 待转义的文本
   */
  static void escapeJsonString(StringBuilder sb, String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
  }

  // ==================== Map-like 便捷 getter（FastJSON2/Gson 风格） ====================

  /**
   * 获取字符串值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return 字符串值
   */
  public String getString(String name) {
    JsonNode node = fields.get(name);
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    return node.asText();
  }

  /**
   * 获取整数值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return 整数值
   */
  public Integer getInteger(String name) {
    JsonNode node = fields.get(name);
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (node.isNumber()) {
      return node.asInt();
    }
    try {
      return Integer.parseInt(node.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 获取 int 基本类型值（为 null 时返回 0）。
   *
   * @param name 字段名
   * @return int 值
   */
  public int getIntValue(String name) {
    Integer value = getInteger(name);
    return value != null ? value : 0;
  }

  /**
   * 获取长整数值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return 长整数值
   */
  public Long getLong(String name) {
    JsonNode node = fields.get(name);
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (node.isNumber()) {
      return node.asLong();
    }
    try {
      return Long.parseLong(node.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 获取 long 基本类型值（为 null 时返回 0）。
   *
   * @param name 字段名
   * @return long 值
   */
  public long getLongValue(String name) {
    Long value = getLong(name);
    return value != null ? value : 0L;
  }

  /**
   * 获取双精度浮点数值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return 双精度浮点数值
   */
  public Double getDouble(String name) {
    JsonNode node = fields.get(name);
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (node.isNumber()) {
      return node.asDouble();
    }
    try {
      return Double.parseDouble(node.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 获取 double 基本类型值（为 null 时返回 0）。
   *
   * @param name 字段名
   * @return double 值
   */
  public double getDoubleValue(String name) {
    Double value = getDouble(name);
    return value != null ? value : 0.0;
  }

  /**
   * 获取浮点数值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return Float 值
   */
  public Float getFloat(String name) {
    Double value = getDouble(name);
    return value != null ? value.floatValue() : null;
  }

  /**
   * 获取 float 基本类型值（为 null 时返回 0）。
   *
   * @param name 字段名
   * @return float 值
   */
  public float getFloatValue(String name) {
    Float value = getFloat(name);
    return value != null ? value : 0.0f;
  }

  /**
   * 获取布尔值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return 布尔值
   */
  public Boolean getBoolean(String name) {
    return nodeToBoolean(fields.get(name));
  }

  /**
   * 获取 boolean 基本类型值（为 null 时返回 false）。
   *
   * @param name 字段名
   * @return boolean 值
   */
  public boolean getBooleanValue(String name) {
    Boolean value = getBoolean(name);
    return value != null ? value : false;
  }

  /**
   * 获取 BigDecimal 值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return BigDecimal 值
   */
  public BigDecimal getBigDecimal(String name) {
    return nodeToBigDecimal(fields.get(name));
  }

  /**
   * 获取 BigInteger 值，不存在或 null 返回 null。
   *
   * @param name 字段名
   * @return BigInteger 值
   */
  public BigInteger getBigInteger(String name) {
    return nodeToBigInteger(fields.get(name));
  }

  /**
   * 获取嵌套对象节点（推荐使用的命名风格），不存在或非对象返回 null。
   *
   * @param name 字段名
   * @return ObjectNode 实例
   * @since 1.0.0
   */
  public ObjectNode getObjectNode(String name) {
    JsonNode node = fields.get(name);
    if (node instanceof ObjectNode objNode) {
      return objNode;
    }
    return null;
  }

  /**
   * 获取嵌套数组节点（推荐使用的命名风格），不存在或非数组返回 null。
   *
   * @param name 字段名
   * @return ArrayNode 实例
   * @since 1.0.0
   */
  public ArrayNode getArrayNode(String name) {
    JsonNode node = fields.get(name);
    if (node instanceof ArrayNode arrNode) {
      return arrNode;
    }
    return null;
  }

  /**
   * 获取节点并转换为指定类型。
   *
   * @param name 字段名
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 转换后的对象
   */
  public <T> T getObject(String name, Class<T> clazz) {
    JsonNode node = fields.get(name);
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    return YdszJson.fromJson(node.toString(), clazz);
  }

  /**
   * 获取字符串值或默认值。
   *
   * @param name 字段名
   * @param defaultValue 默认值
   * @return 字符串值或默认值
   */
  public String getStringOrDefault(String name, String defaultValue) {
    String value = getString(name);
    return value != null ? value : defaultValue;
  }

  /**
   * 获取整数值或默认值。
   *
   * @param name 字段名
   * @param defaultValue 默认值
   * @return 整数值或默认值
   */
  public Integer getIntegerOrDefault(String name, Integer defaultValue) {
    Integer value = getInteger(name);
    return value != null ? value : defaultValue;
  }

  /**
   * 获取长整数值或默认值。
   *
   * @param name 字段名
   * @param defaultValue 默认值
   * @return 长整数值或默认值
   */
  public Long getLongOrDefault(String name, Long defaultValue) {
    Long value = getLong(name);
    return value != null ? value : defaultValue;
  }

  /**
   * 获取布尔值或默认值。
   *
   * @param name 字段名
   * @param defaultValue 默认值
   * @return 布尔值或默认值
   */
  public Boolean getBooleanOrDefault(String name, Boolean defaultValue) {
    Boolean value = getBoolean(name);
    return value != null ? value : defaultValue;
  }

  // ==================== Map-like 查询 ====================

  /**
   * 是否包含指定字段。
   *
   * @param name 字段名
   * @return 包含返回 true
   */
  public boolean containsKey(String name) {
    return fields.containsKey(name);
  }

  /**
   * 是否为空对象。
   *
   * @return 为空返回 true
   */
  public boolean isEmpty() {
    return fields.isEmpty();
  }

  /**
   * 获取所有字段名。
   *
   * @return 字段名集合
   */
  public Set<String> keySet() {
    return Collections.unmodifiableSet(fields.keySet());
  }

  /**
   * 获取所有字段值。
   *
   * @return 字段值集合
   */
  public Collection<JsonNode> values() {
    return Collections.unmodifiableCollection(fields.values());
  }

  /**
   * 获取字段 entry 集合。
   *
   * @return entry 集合
   */
  public Set<Map.Entry<String, JsonNode>> entrySet() {
    return Collections.unmodifiableSet(fields.entrySet());
  }

  // ==================== 通用 put（支持任意值） ====================

  /**
   * 添加任意值字段（自动转换为 JsonNode）。
   *
   * @param name 字段名
   * @param value 字段值，null 转换为 NullNode
   * @return 当前对象节点（支持链式调用）
   */
  public ObjectNode put(String name, Object value) {
    if (value == null) {
      fields.put(name, NullNode.getInstance());
    } else if (value instanceof JsonNode node) {
      fields.put(name, node);
    } else if (value instanceof String str) {
      fields.put(name, new TextNode(str));
    } else if (value instanceof Boolean bool) {
      fields.put(name, BooleanNode.of(bool));
    } else if (value instanceof Number num) {
      fields.put(name, new NumberNode(num));
    } else if (value instanceof Map<?, ?> map) {
      fields.put(name, ObjectNode.fromMap(map));
    } else if (value instanceof List<?> list) {
      fields.put(name, ArrayNode.fromList(list));
    } else {
      fields.put(name, TreeConverter.convertToJsonNode(value));
    }
    return this;
  }

  // ==================== 转换 ====================

  /**
   * 转换为 JSON 字符串。
   *
   * @return JSON 字符串
   */
  public String toJsonString() {
    return toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ObjectNode)) {
      return false;
    }
    return fields.equals(((ObjectNode) obj).fields);
  }

  @Override
  public int hashCode() {
    return fields.hashCode();
  }

  /**
   * 从 Map 创建 ObjectNode。
   *
   * @param map 源 Map，null 返回空 ObjectNode
   * @return ObjectNode 实例
   * @since 1.0.0
   */
  public static ObjectNode fromMap(Map<?, ?> map) {
    ObjectNode node = new ObjectNode();
    if (map == null) {
      return node;
    }
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key =
          entry.getKey() instanceof String
              ? (String) entry.getKey()
              : String.valueOf(entry.getKey());
      Object value = entry.getValue();
      if (value == null) {
        node.fields.put(key, NullNode.getInstance());
      } else if (value instanceof JsonNode) {
        node.fields.put(key, (JsonNode) value);
      } else {
        node.fields.put(key, TreeConverter.convertToJsonNode(value));
      }
    }
    return node;
  }
}
