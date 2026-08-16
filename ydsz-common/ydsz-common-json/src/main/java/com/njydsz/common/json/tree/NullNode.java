package com.njydsz.common.json.tree;

/**
 * JSON null 节点
 *
 * <p>对标 Jackson NullNode，表示 JSON 中的 null 值。 采用单例模式，全局唯一实例。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>单例模式，全局唯一
 *   <li>不可变对象，线程安全
 *   <li>asText() 返回 null，与其他节点区分
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * NullNode nullNode = NullNode.getInstance();
 * nullNode.isNull(); // true
 * nullNode.asText(); // null
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class NullNode extends JsonNode {

  private static final NullNode INSTANCE = new NullNode();

  private NullNode() {}

  /**
   * 获取 NullNode 单例实例
   *
   * @return NullNode 单例
   */
  public static NullNode getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isNull() {
    return true;
  }

  @Override
  public String asText() {
    return null;
  }

  @Override
  public String asText(String defaultValue) {
    return defaultValue;
  }

  @Override
  public Object asValue() {
    return null;
  }

  @Override
  public String toString() {
    return "null";
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof NullNode;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
