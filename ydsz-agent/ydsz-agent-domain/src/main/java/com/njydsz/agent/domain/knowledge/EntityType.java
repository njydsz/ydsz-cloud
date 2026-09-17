package com.njydsz.agent.domain.knowledge;

/**
 * 知识图谱实体类型枚举。
 *
 * <p>定义实体节点可归属的语义类别，用于抽取时的类型约束和查询时的过滤。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public enum EntityType {

  /** 人物 */
  PERSON,

  /** 地理位置 */
  LOCATION,

  /** 组织机构 */
  ORGANIZATION,

  /** 抽象概念 */
  CONCEPT,

  /** 事件 */
  EVENT,

  /** 产品 */
  PRODUCT,

  /** 技术/技术栈 */
  TECHNOLOGY,

  /** 未知类型（兜底） */
  UNKNOWN;

  /**
   * 将字符串解析为枚举值（大小写不敏感）。
   *
   * @param value 类型字符串
   * @return 对应枚举，无法解析时返回 {@link #UNKNOWN}
   */
  public static EntityType fromString(String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return EntityType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }

  /**
   * 是否为已知类型（非 UNKNOWN）。
   *
   * @return true 表示非兜底值
   */
  public boolean isKnown() {
    return this != UNKNOWN;
  }
}
