package com.njydsz.agent.domain.knowledge;

/**
 * 知识图谱关系类型枚举。
 *
 * <p>定义实体之间有向边的语义关系，用于抽取和查询时的类型约束。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public enum RelationType {

  /** 任职于 / 工作于 */
  WORKS_AT,

  /** 位于 */
  LOCATED_IN,

  /** 属于某组织/系统的部分 */
  PART_OF,

  /** 相关（通用弱关系） */
  RELATED_TO,

  /** 由...创建 */
  CREATED_BY,

  /** 依赖 */
  DEPENDS_ON,

  /** 归属于 */
  BELONGS_TO;

  /**
   * 将字符串解析为枚举值（大小写不敏感）。
   *
   * @param value 关系类型字符串
   * @return 对应枚举，无法解析时返回 {@link #RELATED_TO}
   */
  public static RelationType fromString(String value) {
    if (value == null || value.isBlank()) {
      return RELATED_TO;
    }
    try {
      return RelationType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return RELATED_TO;
    }
  }
}
