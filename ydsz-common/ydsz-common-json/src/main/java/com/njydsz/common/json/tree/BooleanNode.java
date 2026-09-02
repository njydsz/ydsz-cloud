package com.njydsz.common.json.tree;

/**
 * JSON 布尔节点
 *
 * <p>对标 Jackson BooleanNode，表示 JSON 中的布尔值（true/false）。 采用单例模式，只创建两个实例：TRUE 和 FALSE。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>单例模式，只创建 TRUE 和 FALSE 两个实例
 *   <li>不可变对象，线程安全
 *   <li>自动装箱优化，避免频繁创建对象
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * BooleanNode trueNode = BooleanNode.of(true);
 * BooleanNode falseNode = BooleanNode.of(false);
 *
 * boolean value = trueNode.asBoolean(); // true
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class BooleanNode extends JsonNode {

  private static final BooleanNode TRUE = new BooleanNode(true);
  private static final BooleanNode FALSE = new BooleanNode(false);

  private final boolean value;

  private BooleanNode(boolean value) {
    this.value = value;
  }

  /**
   * 工厂方法：创建或获取布尔节点
   *
   * @param value 布尔值
   * @return 对应的 BooleanNode 单例
   */
  public static BooleanNode of(boolean value) {
    return value ? TRUE : FALSE;
  }

  @Override
  public boolean isBoolean() {
    return true;
  }

  @Override
  public boolean asBoolean() {
    return value;
  }

  @Override
  public boolean asBoolean(boolean defaultValue) {
    return value;
  }

  @Override
  public String asText() {
    return String.valueOf(value);
  }

  @Override
  public Object asValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BooleanNode)) {
      return false;
    }
    return value == ((BooleanNode) obj).value;
  }

  @Override
  public int hashCode() {
    return Boolean.hashCode(value);
  }
}
