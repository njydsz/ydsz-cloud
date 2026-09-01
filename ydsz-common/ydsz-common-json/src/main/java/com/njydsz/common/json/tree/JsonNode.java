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

  /**
   * 判断是否为对象
   *
   * @return 当前节点为 JSON 对象时返回 {@code true}；基类默认返回 {@code false}，由 {@code ObjectNode} 覆盖为 {@code true}
   */
  public boolean isObject() {
    return false;
  }

  /**
   * 判断是否为数组
   *
   * @return 当前节点为 JSON 数组时返回 {@code true}；基类默认返回 {@code false}，由 {@code ArrayNode} 覆盖为 {@code true}
   */
  public boolean isArray() {
    return false;
  }

  /**
   * 判断是否为字符串
   *
   * @return 当前节点为 JSON 字符串时返回 {@code true}；基类默认返回 {@code false}，由 {@code TextNode} 覆盖为 {@code true}
   */
  public boolean isTextual() {
    return false;
  }

  /**
   * 判断是否为数值
   *
   * @return 当前节点为 JSON 数值时返回 {@code true}；基类默认返回 {@code false}，由 {@code NumberNode} 覆盖为 {@code true}
   */
  public boolean isNumber() {
    return false;
  }

  /**
   * 判断是否为布尔值
   *
   * @return 当前节点为 JSON 布尔值时返回 {@code true}；基类默认返回 {@code false}，由 {@code BooleanNode} 覆盖为 {@code true}
   */
  public boolean isBoolean() {
    return false;
  }

  /**
   * 判断是否为 null
   *
   * @return 当前节点为 JSON null 时返回 {@code true}；基类默认返回 {@code false}，由 {@code NullNode} 覆盖为 {@code true}
   */
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

  /**
   * RFC 6901 JSON Pointer 访问（P1 能力补齐，对标 Jackson {@code at()}）。
   *
   * <p>与 {@link #path(String)} 的区别：入参必须是标准 JSON Pointer——以 {@code /} 开头
   * 或为空串（空串指向整文档）。非法指针（不以 {@code /} 开头）返回 MissingNode 而非降级。
   *
   * @param jsonPointer JSON Pointer 表达式，如 {@code /user/address/0/city}
   * @return 指向的节点；路径不可达时返回 MissingNode（与 Jackson 语义一致，不抛异常）
   */
  public JsonNode at(String jsonPointer) {
    if (jsonPointer == null) {
      return MissingNode.getInstance();
    }
    if (jsonPointer.isEmpty()) {
      return this;
    }
    if (!jsonPointer.startsWith("/")) {
      return MissingNode.getInstance();
    }
    return path(jsonPointer.substring(1));
  }

  /**
   * 深度优先查找第一个匹配字段名的值节点（P1 能力补齐，对标 Jackson {@code findValue()}）。
   *
   * <p>仅容器节点（对象/数组）会实际递归；叶子节点返回 null。
   *
   * @param fieldName 字段名
   * @return 第一个匹配字段的值节点；未找到返回 null
   */
  public JsonNode findValue(String fieldName) {
    return null;
  }

  /**
   * 深度优先收集全部匹配字段名的值节点（P1 能力补齐，对标 Jackson {@code findValues()}）。
   *
   * @param fieldName 字段名
   * @param found 结果收集列表（由调用方创建并传入，按遍历顺序追加）
   */
  public void findValues(String fieldName, List<JsonNode> found) {
    // 基类空实现：叶子节点无可遍历子节点
  }

  /**
   * 转换为字符串
   *
   * @return 节点对应的字符串；基类默认返回空字符串，对象 / 数组等非文本节点同样返回空字符串而非 {@code null}
   */
  public String asText() {
    return "";
  }

  /**
   * 转换为字符串（带默认值）
   * <p>对齐 Jackson 语义：若 {@link #asText()} 返回 null，则返回默认值。 容器节点（ObjectNode / ArrayNode）应覆盖此方法直接返回默认值。
   *
   * @param defaultValue 默认值
   * @return 节点对应的字符串；{@link #asText()} 返回 {@code null} 时返回 {@code defaultValue}，
   *     容器节点覆盖后直接返回 {@code defaultValue}
   */
  public String asText(String defaultValue) {
    String str = asText();
    return (str == null) ? defaultValue : str;
  }

  /**
   * 转换为整数
   *
   * @return 节点对应的 int 值；基类默认返回 {@code 0}，非数值节点同样返回 {@code 0}（不抛异常、不做精度校验）
   */
  public int asInt() {
    return 0;
  }

  /**
   * 转换为整数（带默认值）
   * <p>对齐 Jackson 语义：非数值节点返回默认值。数值节点（NumberNode）应覆盖此方法。
   *
   * @param defaultValue 默认值
   * @return 节点对应的 int 值；非数值节点返回 {@code defaultValue}
   */
  public int asInt(int defaultValue) {
    return defaultValue;
  }

  /**
   * 转换为长整数
   *
   * @return 节点对应的 long 值；基类默认返回 {@code 0L}，非数值节点同样返回 {@code 0L}
   */
  public long asLong() {
    return 0L;
  }

  /**
   * 转换为长整数（带默认值）
   * <p>对齐 Jackson 语义：非数值节点返回默认值。数值节点（NumberNode）应覆盖此方法。
   *
   * @param defaultValue 默认值
   * @return 节点对应的 long 值；非数值节点返回 {@code defaultValue}
   */
  public long asLong(long defaultValue) {
    return defaultValue;
  }

  /**
   * 转换为双精度数
   *
   * @return 节点对应的 double 值；基类默认返回 {@code 0.0}，非数值节点同样返回 {@code 0.0}
   */
  public double asDouble() {
    return 0.0;
  }

  /**
   * 转换为双精度数（带默认值）
   * <p>对齐 Jackson 语义：非数值节点返回默认值。数值节点（NumberNode）应覆盖此方法。
   *
   * @param defaultValue 默认值
   * @return 节点对应的 double 值；非数值节点返回 {@code defaultValue}
   */
  public double asDouble(double defaultValue) {
    return defaultValue;
  }

  /**
   * 转换为布尔值
   *
   * @return 节点对应的 boolean 值；基类默认返回 {@code false}，非布尔节点同样返回 {@code false}
   */
  public boolean asBoolean() {
    return false;
  }

  /**
   * 转换为布尔值（带默认值）
   * <p>对齐 Jackson 语义：非布尔节点返回默认值。布尔节点（BooleanNode）应覆盖此方法。
   *
   * @param defaultValue 默认值
   * @return 节点对应的 boolean 值；非布尔节点返回 {@code defaultValue}
   */
  public boolean asBoolean(boolean defaultValue) {
    return defaultValue;
  }

  /**
   * 判断是否为缺失节点
   *
   * @return 当前节点为缺失节点（{@code MissingNode}）时返回 {@code true}；基类默认返回 {@code false}，
   *     {@code MissingNode} 覆盖为 {@code true}
   */
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
   * @since 1.0.0
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
   * @since 1.0.0
   */
  public JsonNode deepCopy() {
    return this;
  }

  /**
   * 获取子节点数量
   *
   * @return 子节点数量：对象为字段个数、数组为元素个数，叶子节点与 {@code MissingNode} 返回 {@code 0}
   */
  public int size() {
    return 0;
  }

  /**
   * 是否有指定字段
   *
   * @param fieldName 字段名
   * @return 对象节点存在该字段时返回 {@code true}；非对象节点一律返回 {@code false}
   */
  public boolean has(String fieldName) {
    return false;
  }

  /**
   * 是否有指定索引
   *
   * @param index 索引
   * @return 数组节点存在该下标（{@code 0 <= index < size()}）时返回 {@code true}；非数组节点一律返回 {@code false}
   */
  public boolean has(int index) {
    return false;
  }

  /**
   * 字段名迭代器（仅对象）
   *
   * @return 字段名迭代器，不会为 {@code null}；非对象节点返回空迭代器
   */
  public Iterator<String> fieldNames() {
    return Collections.emptyIterator();
  }

  /**
   * 元素迭代器（仅数组）
   *
   * @return 子元素迭代器，按下标顺序遍历，不会为 {@code null}；非数组节点返回空迭代器
   */
  public Iterator<JsonNode> elements() {
    return Collections.emptyIterator();
  }

  /**
   * 转换为 Map
   *
   * @return 字段名到子节点的映射，不会为 {@code null}；非对象节点返回不可变的空 {@code Map}
   */
  public Map<String, JsonNode> asMap() {
    return Collections.emptyMap();
  }

  /**
   * 转换为 List
   *
   * @return 子节点列表，按下标顺序排列，不会为 {@code null}；非数组节点返回不可变的空 {@code List}
   */
  public List<JsonNode> asList() {
    return Collections.emptyList();
  }

  /**
   * 转换为原始值
   *
   * @return 节点承载的原始值（{@code String} / {@code Number} / {@code Boolean} / {@code Map} / {@code List}）；
   *     基类默认返回 {@code null}
   */
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
