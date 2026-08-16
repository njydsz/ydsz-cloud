package com.njydsz.common.json.tree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON 节点基类
 *
 * <p>对标 Jackson JsonNode，提供树模型 JSON 操作。
 *
 * <p><b>节点类型：</b>
 *
 * <ul>
 *   <li>ObjectNode - JSON 对象
 *   <li>ArrayNode - JSON 数组
 *   <li>TextNode - JSON 字符串
 *   <li>NumberNode - JSON 数值
 *   <li>BooleanNode - JSON 布尔值
 *   <li>NullNode - JSON null
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * ObjectNode node = YdszJson.tree("{\"name\":\"John\",\"age\":30}");
 * String name = node.get("name").asText();
 * int age = node.get("age").asInt();
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class JsonNode {

  /** 判断是否为对象 */
  public boolean isObject() {
    return false;
  }

  /** 判断是否为数组 */
  public boolean isArray() {
    return false;
  }

  /** 判断是否为字符串 */
  public boolean isTextual() {
    return false;
  }

  /** 判断是否为数值 */
  public boolean isNumber() {
    return false;
  }

  /** 判断是否为布尔值 */
  public boolean isBoolean() {
    return false;
  }

  /** 判断是否为 null */
  public boolean isNull() {
    return false;
  }

  /**
   * 获取子节点
   *
   * @param fieldName 字段名
   * @return 子节点，不存在返回 MissingNode
   */
  public JsonNode get(String fieldName) {
    return MissingNode.getInstance();
  }

  /**
   * 获取子节点（数组索引）
   *
   * @param index 数组索引
   * @return 子节点
   */
  public JsonNode get(int index) {
    return MissingNode.getInstance();
  }

  /**
   * 获取子节点（支持路径表达式）
   *
   * @param path 路径表达式，如 "user/name"
   * @return 子节点
   */
  public JsonNode path(String path) {
    String[] parts = path.split("/");
    JsonNode current = this;
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      // 反转 JSON Pointer 转义（~1 → /，~0 → ~），对齐 RFC 6901
      String unescaped = part.replace("~1", "/").replace("~0", "~");
      if (current.isArray()) {
        try {
          current = current.get(Integer.parseInt(unescaped));
        } catch (NumberFormatException e) {
          return MissingNode.getInstance();
        }
      } else {
        current = current.get(unescaped);
      }
      if (current.isMissing()) {
        return current;
      }
    }
    return current;
  }

  /** 转换为字符串 */
  public String asText() {
    return "";
  }

  /**
   * 转换为字符串（带默认值）
   *
   * <p>对齐 Jackson 语义：若 {@link #asText()} 返回 null，则返回默认值。 容器节点（ObjectNode / ArrayNode）应覆盖此方法直接返回默认值。
   */
  public String asText(String defaultValue) {
    String str = asText();
    return (str == null) ? defaultValue : str;
  }

  /** 转换为整数 */
  public int asInt() {
    return 0;
  }

  /**
   * 转换为整数（带默认值）
   *
   * <p>对齐 Jackson 语义：非数值节点返回默认值。数值节点（NumberNode）应覆盖此方法。
   */
  public int asInt(int defaultValue) {
    return defaultValue;
  }

  /** 转换为长整数 */
  public long asLong() {
    return 0L;
  }

  /**
   * 转换为长整数（带默认值）
   *
   * <p>对齐 Jackson 语义：非数值节点返回默认值。数值节点（NumberNode）应覆盖此方法。
   */
  public long asLong(long defaultValue) {
    return defaultValue;
  }

  /** 转换为双精度数 */
  public double asDouble() {
    return 0.0;
  }

  /**
   * 转换为双精度数（带默认值）
   *
   * <p>对齐 Jackson 语义：非数值节点返回默认值。数值节点（NumberNode）应覆盖此方法。
   */
  public double asDouble(double defaultValue) {
    return defaultValue;
  }

  /** 转换为布尔值 */
  public boolean asBoolean() {
    return false;
  }

  /**
   * 转换为布尔值（带默认值）
   *
   * <p>对齐 Jackson 语义：非布尔节点返回默认值。布尔节点（BooleanNode）应覆盖此方法。
   */
  public boolean asBoolean(boolean defaultValue) {
    return defaultValue;
  }

  /** 判断是否为缺失节点 */
  public boolean isMissing() {
    return false;
  }

  /**
   * 当前节点是否为叶子节点（标量或 null）。
   *
   * <p>叶子节点（TextNode / NumberNode / BooleanNode / NullNode / MissingNode）不可变，
   * 可直接安全共享引用；容器节点（ObjectNode / ArrayNode）可变，需要深拷贝。
   *
   * @return true 如果节点为叶子节点
   * @since 1.2.0
   */
  public boolean isLeaf() {
    return !isObject() && !isArray();
  }

  /**
   * 创建当前节点的深拷贝。
   *
   * <p>叶子节点（标量/null/缺失）不可变，子类默认实现返回 {@code this}（安全共享引用）。 容器节点（ObjectNode /
   * ArrayNode）必须重写此方法返回全新的嵌套副本。
   *
   * @return 当前节点的深拷贝（叶子节点返回 this）
   * @since 1.2.0
   */
  public JsonNode deepCopy() {
    return this;
  }

  /** 获取子节点数量 */
  public int size() {
    return 0;
  }

  /** 是否有指定字段 */
  public boolean has(String fieldName) {
    return false;
  }

  /** 是否有指定索引 */
  public boolean has(int index) {
    return false;
  }

  /** 字段名迭代器（仅对象） */
  public Iterator<String> fieldNames() {
    return Collections.emptyIterator();
  }

  /** 元素迭代器（仅数组） */
  public Iterator<JsonNode> elements() {
    return Collections.emptyIterator();
  }

  /** 转换为 Map */
  public Map<String, JsonNode> asMap() {
    return Collections.emptyMap();
  }

  /** 转换为 List */
  public List<JsonNode> asList() {
    return Collections.emptyList();
  }

  /** 转换为原始值 */
  public Object asValue() {
    return null;
  }

  // ==================== 共享节点转换工具（ObjectNode/ArrayNode 复用，1.2.1） ====================

  /**
   * 将节点转换为 Boolean。
   *
   * <p>支持布尔节点与字符串 "true"/"false"/"1"/"0"（忽略大小写）； null/缺失节点或其他不可解析值返回 null。
   *
   * @param node 源节点
   * @return 转换结果或 null
   */
  protected static Boolean nodeToBoolean(JsonNode node) {
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    String str = node.asText();
    if ("true".equalsIgnoreCase(str) || "1".equals(str)) {
      return true;
    }
    if ("false".equalsIgnoreCase(str) || "0".equals(str)) {
      return false;
    }
    return null;
  }

  /**
   * 将节点转换为 BigDecimal（支持 NumberNode 与数字文本）。
   *
   * @param node 源节点
   * @return 转换结果或 null
   */
  protected static BigDecimal nodeToBigDecimal(JsonNode node) {
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (node instanceof NumberNode numNode) {
      Number num = numNode.numberValue();
      if (num instanceof BigDecimal bd) {
        return bd;
      }
      if (num instanceof BigInteger bi) {
        return new BigDecimal(bi);
      }
      return new BigDecimal(num.toString());
    }
    try {
      return new BigDecimal(node.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 将节点转换为 BigInteger（支持 NumberNode 与数字文本）。
   *
   * @param node 源节点
   * @return 转换结果或 null
   */
  protected static BigInteger nodeToBigInteger(JsonNode node) {
    if (node == null || node.isNull() || node.isMissing()) {
      return null;
    }
    if (node instanceof NumberNode numNode) {
      Number num = numNode.numberValue();
      if (num instanceof BigInteger bi) {
        return bi;
      }
      if (num instanceof BigDecimal bd) {
        return bd.toBigInteger();
      }
      return BigInteger.valueOf(num.longValue());
    }
    try {
      return new BigInteger(node.asText());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public abstract String toString();

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);
}
