package com.njydsz.common.json.tree;

/**
 * JSON 缺失节点
 *
 * <p>表示 JSON 中不存在的字段或索引，对标 Jackson MissingNode。 当访问不存在的字段或数组索引时返回此节点。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>单例模式，全局唯一
 *   <li>不可变对象，线程安全
 *   <li>isMissing() 返回 true，用于区分 null 值
 *   <li>asText() 返回空字符串，避免 NullPointerException
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * JsonNode node = ObjectNode.get("nonexistent");
 * node.isMissing(); // true
 * node.asText("");  // 返回默认值 ""
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class MissingNode extends JsonNode {

  private static final MissingNode INSTANCE = new MissingNode();

  private MissingNode() {}

  /**
   * 获取 MissingNode 单例实例
   *
   * @return MissingNode 单例
   */
  public static MissingNode getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isMissing() {
    return true;
  }

  @Override
  public String asText() {
    return "";
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
    return "";
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MissingNode;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
