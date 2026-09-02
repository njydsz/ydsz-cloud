package com.njydsz.common.json.tree;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON 数值节点
 *
 * <p>对标 Jackson NumberNode，表示一个 JSON 数字值。 支持整数、浮点数、BigDecimal 等多种数值类型。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>不可变对象，线程安全
 *   <li>支持 int、long、double、BigDecimal 等类型
 *   <li>提供多种数值转换方法
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * NumberNode intNode = new NumberNode(42);
 * int value = intNode.asInt(); // 42
 *
 * NumberNode doubleNode = new NumberNode(3.14);
 * double pi = doubleNode.asDouble(); // 3.14
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class NumberNode extends JsonNode {

  private final Number value;

  /**
   * 创建数值节点
   *
   * @param value 数值，支持 Integer、Long、Double、Float、BigDecimal 等
   */
  public NumberNode(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("NumberNode value must not be null");
    }
    this.value = value;
  }

  @Override
  public boolean isNumber() {
    return true;
  }

  @Override
  public String asText() {
    return value.toString();
  }

  @Override
  public String asText(String defaultValue) {
    return asText();
  }

  @Override
  public int asInt() {
    return value.intValue();
  }

  @Override
  public int asInt(int defaultValue) {
    return value != null ? value.intValue() : defaultValue;
  }

  @Override
  public long asLong() {
    return value.longValue();
  }

  @Override
  public long asLong(long defaultValue) {
    return value != null ? value.longValue() : defaultValue;
  }

  @Override
  public double asDouble() {
    return value.doubleValue();
  }

  @Override
  public double asDouble(double defaultValue) {
    return value != null ? value.doubleValue() : defaultValue;
  }

  /**
   * 获取原始数值对象
   *
   * @return 原始 Number 对象
   */
  public Number numberValue() {
    return value;
  }

  @Override
  public Object asValue() {
    return value;
  }

  @Override
  public String toString() {
    return value != null ? value.toString() : "null";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof NumberNode)) {
      return false;
    }
    NumberNode other = (NumberNode) obj;
    // 处理 null value 的情况
    if (value == null) {
      return other.value == null;
    }
    if (other.value == null) {
      return false;
    }
    // 数值归一比较：整数值按 long 比较，浮点数按 double 比较，
    // 避免 Integer(1).equals(Long(1L)) = false 这类问题
    if (isIntegral(value) && isIntegral(other.value)) {
      return value.longValue() == other.value.longValue();
    }
    return Double.compare(value.doubleValue(), other.value.doubleValue()) == 0;
  }

  /** 判断 Number 是否为整数类型（含 BigInteger）。 */
  private static boolean isIntegral(Number n) {
    return n instanceof Integer
        || n instanceof Long
        || n instanceof Short
        || n instanceof Byte
        || n instanceof BigInteger
        || n instanceof AtomicInteger
        || n instanceof AtomicLong;
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
