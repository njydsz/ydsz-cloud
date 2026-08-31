package com.njydsz.literule.domain.api;

/**
 * 决策表命中策略（对齐 DMN 1.4 标准）
 *
 * <ul>
 *   <li>{@link #UNIQUE} — 唯一命中：多行匹配时报错
 *   <li>{@link #FIRST} — 首次命中（默认）：返回首条匹配行
 *   <li>{@link #PRIORITY} — 优先级命中：返回所有匹配行中 priority 最小者
 *   <li>{@link #COLLECT} — 收集命中：返回全部匹配行（按优先级排序）
 *   <li>{@link #ANY} — 任意命中：返回首条匹配行（与 FIRST 行为一致，仅语义不同）
 *   <li>{@link #RULE_ORDER} — 规则顺序命中：返回全部匹配行（按行在表中的出现顺序）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum HitPolicy {
  /** 唯一命中：多行匹配时报错 */
  UNIQUE,

  /** 首次命中（默认）：返回首条匹配行 */
  FIRST,

  /** 优先级命中：返回所有匹配行中 priority 最小者 */
  PRIORITY,

  /** 收集命中：返回全部匹配行（按优先级排序） */
  COLLECT,

  /** 任意命中：返回首条匹配行（与 FIRST 行为一致，仅语义不同） */
  ANY,

  /** 规则顺序命中：返回全部匹配行，按行在表中的出现顺序排列（DMN 1.4 RULE ORDER） */
  RULE_ORDER;

  /**
   * 从字符串安全解析（大小写不敏感）
   *
   * @param code 策略编码
   * @return 对应策略；未匹配返回 {@link #FIRST}（默认值，向后兼容旧数据）
   */
  public static HitPolicy fromCode(String code) {
    if (code == null || code.isBlank()) {
      return FIRST;
    }
    try {
      return HitPolicy.valueOf(code.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return FIRST;
    }
  }
}
