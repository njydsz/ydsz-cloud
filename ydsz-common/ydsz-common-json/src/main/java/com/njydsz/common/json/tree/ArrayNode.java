package com.njydsz.common.json.tree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 数组节点
 *
 * <p>对标 Jackson ArrayNode / FastJSON2 JSONArray，表示一个 JSON 数组（有序元素集合）， 内部使用 ArrayList 存储元素。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>支持动态添加/删除元素
 *   <li>支持链式调用（Builder 模式）
 *   <li>提供类型安全的元素访问（getString/getInteger/getLong/getBoolean 等）
 *   <li>支持转换为 List 和 JSON 字符串
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * ArrayNode array = new ArrayNode();
 * array.add("hello")
 *      .add(42)
 *      .add(true);
 *
 * // 获取元素
 * String first = array.getString(0);
 * int second = array.getIntValue(1);
 * ObjectNode sub = array.getObjectNode(2);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ArrayNode extends JsonNode {

  private final List<JsonNode> elements;

  /** 创建空的 JSON 数组节点 */
  public ArrayNode() {
    this.elements = new ArrayList<>();
  }

  /**
   * 使用现有元素列表创建 JSON 数组节点
   *
   * @param elements 元素列表
   */
  public ArrayNode(List<JsonNode> elements) {
    this.elements = new ArrayList<>(elements);
  }

  /**
   * 添加 JSON 节点元素
   *
   * @param node 要添加的节点，null 会被转换为 NullNode
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(JsonNode node) {
    elements.add(node != null ? node : NullNode.getInstance());
    return this;
  }

  /**
   * 添加字符串元素
   *
   * @param value 字符串值，null 会被转换为 NullNode
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(String value) {
    elements.add(value != null ? new TextNode(value) : NullNode.getInstance());
    return this;
  }

  /**
   * 添加整数元素
   *
   * @param value 整数值
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(int value) {
    elements.add(new NumberNode(value));
    return this;
  }

  /**
   * 添加长整数元素
   *
   * @param value 长整数值
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(long value) {
    elements.add(new NumberNode(value));
    return this;
  }

  /**
   * 添加双精度浮点数元素
   *
   * @param value 双精度浮点数值
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(double value) {
    elements.add(new NumberNode(value));
    return this;
  }

  /**
   * 添加布尔元素
   *
   * @param value 布尔值
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(boolean value) {
    elements.add(BooleanNode.of(value));
    return this;
  }

  /**
   * 设置指定索引位置的元素
   *
   * @param index 元素索引（从 0 开始）
   * @param node 新的节点值
   * @return 当前数组节点（支持链式调用）
   * @throws IndexOutOfBoundsException 如果索引超出范围
   */
  public ArrayNode set(int index, JsonNode node) {
    elements.set(index, node);
    return this;
  }

  /**
   * 替换指定索引位置的元素（自动转换为 JsonNode）。
   *
   * @param index 元素索引（从 0 开始）
   * @param value 新的元素值，null 转换为 NullNode
   * @return 当前数组节点（支持链式调用）
   * @throws IndexOutOfBoundsException 如果索引超出范围
   */
  public ArrayNode set(int index, Object value) {
    elements.set(index, toNode(value));
    return this;
  }

  /**
   * 移除指定索引位置的元素
   *
   * @param index 要移除的元素索引（从 0 开始）
   * @return 被移除的节点
   * @throws IndexOutOfBoundsException 如果索引超出范围
   */
  public JsonNode remove(int index) {
    return elements.remove(index);
  }

  /**
   * 在指定索引位置插入元素，原有元素向右顺移。
   *
   * <p>若 {@code index == size()}，等效于 {@link #add(JsonNode)} 追加到末尾。
   *
   * @param index 插入位置索引（从 0 开始，允许等于当前 size）
   * @param node 要插入的节点，null 会被转换为 NullNode
   * @return 当前数组节点（支持链式调用）
   * @throws IndexOutOfBoundsException 如果 index &lt; 0 或 index &gt; size()
   * @since 1.2.0
   */
  public ArrayNode insert(int index, JsonNode node) {
    if (index < 0 || index > elements.size()) {
      throw new IndexOutOfBoundsException(
          "Insert index out of bounds: index=" + index + ", size=" + elements.size());
    }
    elements.add(index, node != null ? node : NullNode.getInstance());
    return this;
  }

  /**
   * 在指定索引位置插入任意值（自动转换为 JsonNode），原有元素向右顺移。
   *
   * <p>若 {@code index == size()}，等效于 {@link #add(Object)} 追加到末尾。
   *
   * @param index 插入位置索引（从 0 开始，允许等于当前 size）
   * @param value 元素值，null 转换为 NullNode
   * @return 当前数组节点（支持链式调用）
   * @throws IndexOutOfBoundsException 如果 index &lt; 0 或 index &gt; size()
   */
  public ArrayNode insert(int index, Object value) {
    if (index < 0 || index > elements.size()) {
      throw new IndexOutOfBoundsException(
          "Insert index out of bounds: index=" + index + ", size=" + elements.size());
    }
    elements.add(index, toNode(value));
    return this;
  }

  /**
   * 移除指定索引位置的元素（别名方法，语义与 {@link #remove(int)} 一致）。
   *
   * <p>此方法主要供 JSON Patch（RFC 6902）实现中以统一命名风格调用。
   *
   * @param index 要移除的元素索引（从 0 开始）
   * @return 被移除的节点
   * @throws IndexOutOfBoundsException 如果索引超出范围
   * @since 1.2.0
   */
  public JsonNode removeAt(int index) {
    return elements.remove(index);
  }

  @Override
  public boolean isArray() {
    return true;
  }

  @Override
  public JsonNode get(int index) {
    if (index >= 0 && index < elements.size()) {
      return elements.get(index);
    }
    return MissingNode.getInstance();
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
    return elements.size();
  }

  @Override
  public boolean has(int index) {
    return index >= 0 && index < elements.size();
  }

  @Override
  public Iterator<JsonNode> elements() {
    return Collections.unmodifiableList(elements).iterator();
  }

  @Override
  public List<JsonNode> asList() {
    return Collections.unmodifiableList(elements);
  }

  @Override
  public Object asValue() {
    return elements.stream().map(node -> node != null ? node.asValue() : null).toList();
  }

  /**
   * 深拷贝当前 ArrayNode 及其所有嵌套子节点。
   *
   * @return 全新的 ArrayNode 副本
   */
  @Override
  public ArrayNode deepCopy() {
    ArrayNode copy = new ArrayNode();
    for (JsonNode element : elements) {
      copy.elements.add(element != null ? element.deepCopy() : NullNode.getInstance());
    }
    return copy;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(elements.get(i).toString());
    }
    sb.append(']');
    return sb.toString();
  }

  // ==================== List-like 便捷 getter（FastJSON2/Gson 风格） ====================

  /**
   * 获取字符串值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return 字符串值
   */
  public String getString(int index) {
    JsonNode node = get(index);
    if (node.isMissing() || node.isNull()) {
      return null;
    }
    return node.asText();
  }

  /**
   * 获取整数值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return 整数值
   */
  public Integer getInteger(int index) {
    JsonNode node = get(index);
    if (node.isMissing() || node.isNull()) {
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
   * @param index 索引
   * @return int 值
   */
  public int getIntValue(int index) {
    Integer value = getInteger(index);
    return value != null ? value : 0;
  }

  /**
   * 获取长整数值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return 长整数值
   */
  public Long getLong(int index) {
    JsonNode node = get(index);
    if (node.isMissing() || node.isNull()) {
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
   * @param index 索引
   * @return long 值
   */
  public long getLongValue(int index) {
    Long value = getLong(index);
    return value != null ? value : 0L;
  }

  /**
   * 获取双精度浮点数值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return 双精度浮点数值
   */
  public Double getDouble(int index) {
    JsonNode node = get(index);
    if (node.isMissing() || node.isNull()) {
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
   * @param index 索引
   * @return double 值
   */
  public double getDoubleValue(int index) {
    Double value = getDouble(index);
    return value != null ? value : 0.0;
  }

  /**
   * 获取浮点数值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return Float 值
   */
  public Float getFloat(int index) {
    Double value = getDouble(index);
    return value != null ? value.floatValue() : null;
  }

  /**
   * 获取 float 基本类型值（为 null 时返回 0）。
   *
   * @param index 索引
   * @return float 值
   */
  public float getFloatValue(int index) {
    Float value = getFloat(index);
    return value != null ? value : 0.0f;
  }

  /**
   * 获取布尔值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return 布尔值
   */
  public Boolean getBoolean(int index) {
    return nodeToBoolean(get(index));
  }

  /**
   * 获取 boolean 基本类型值（为 null 时返回 false）。
   *
   * @param index 索引
   * @return boolean 值
   */
  public boolean getBooleanValue(int index) {
    Boolean value = getBoolean(index);
    return value != null ? value : false;
  }

  /**
   * 获取 BigDecimal 值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return BigDecimal 值
   */
  public BigDecimal getBigDecimal(int index) {
    return nodeToBigDecimal(get(index));
  }

  /**
   * 获取 BigInteger 值，越界或 null 返回 null。
   *
   * @param index 索引
   * @return BigInteger 值
   */
  public BigInteger getBigInteger(int index) {
    return nodeToBigInteger(get(index));
  }

  /**
   * 获取嵌套对象节点（推荐使用的命名风格），越界或非对象返回 null。
   *
   * @param index 索引
   * @return ObjectNode 实例
   * @since 1.1.0
   */
  public ObjectNode getObjectNode(int index) {
    JsonNode node = get(index);
    if (node instanceof ObjectNode objNode) {
      return objNode;
    }
    return null;
  }

  /**
   * 获取嵌套数组节点（推荐使用的命名风格），越界或非数组返回 null。
   *
   * @param index 索引
   * @return ArrayNode 实例
   * @since 1.1.0
   */
  public ArrayNode getArrayNode(int index) {
    JsonNode node = get(index);
    if (node instanceof ArrayNode arrNode) {
      return arrNode;
    }
    return null;
  }

  /**
   * 获取元素并转换为指定类型。
   *
   * @param index 索引
   * @param clazz 目标类型
   * @param <T> 类型参数
   * @return 转换后的对象
   */
  public <T> T getObject(int index, Class<T> clazz) {
    JsonNode node = get(index);
    if (node.isMissing() || node.isNull()) {
      return null;
    }
    return YdszJson.fromJson(node.toString(), clazz);
  }

  // ==================== List-like 查询 ====================

  /**
   * 是否为空数组。
   *
   * @return 为空返回 true
   */
  public boolean isEmpty() {
    return elements.isEmpty();
  }

  /**
   * 是否包含指定节点。
   *
   * @param node 节点
   * @return 包含返回 true
   */
  public boolean contains(JsonNode node) {
    return elements.contains(node);
  }

  // ==================== 通用 add（支持任意值） ====================

  /**
   * 添加任意值元素（自动转换为 JsonNode）。
   *
   * @param value 元素值，null 转换为 NullNode
   * @return 当前数组节点（支持链式调用）
   */
  public ArrayNode add(Object value) {
    elements.add(toNode(value));
    return this;
  }

  /**
   * 将任意值转换为 JsonNode（null → NullNode；Map → ObjectNode；List → ArrayNode）。
   *
   * @param value 任意值
   * @return 转换后的 JsonNode
   */
  private static JsonNode toNode(Object value) {
    if (value == null) {
      return NullNode.getInstance();
    } else if (value instanceof JsonNode node) {
      return node;
    } else if (value instanceof String str) {
      return new TextNode(str);
    } else if (value instanceof Boolean bool) {
      return BooleanNode.of(bool);
    } else if (value instanceof Number num) {
      return new NumberNode(num);
    } else if (value instanceof Map<?, ?> map) {
      return ObjectNode.fromMap(map);
    } else if (value instanceof List<?> list) {
      return ArrayNode.fromList(list);
    } else {
      return TreeConverter.convertToJsonNode(value);
    }
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
    if (!(obj instanceof ArrayNode)) {
      return false;
    }
    return elements.equals(((ArrayNode) obj).elements);
  }

  @Override
  public int hashCode() {
    return elements.hashCode();
  }

  /**
   * 从 List 创建 ArrayNode。
   *
   * @param list 源 List，null 返回空 ArrayNode
   * @return ArrayNode 实例
   * @since 1.0.0
   */
  public static ArrayNode fromList(List<?> list) {
    ArrayNode node = new ArrayNode();
    if (list == null) {
      return node;
    }
    for (Object value : list) {
      if (value == null) {
        node.elements.add(NullNode.getInstance());
      } else if (value instanceof JsonNode) {
        node.elements.add((JsonNode) value);
      } else {
        node.elements.add(TreeConverter.convertToJsonNode(value));
      }
    }
    return node;
  }
}
