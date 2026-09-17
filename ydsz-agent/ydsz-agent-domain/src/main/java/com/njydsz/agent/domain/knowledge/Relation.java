package com.njydsz.agent.domain.knowledge;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 知识图谱中的有向关系边。
 *
 * <p>表示从起始实体到目标实体的语义关系，携带置信度评估抽取结果的可信程度。
 * 关系是不可变值对象，方向性明确（from → to）。
 *
 * <p><b>不可变性</b>：所有字段为 final，properties 通过 {@link Collections#unmodifiableMap} 包装。
 *
 * <p><b>注意：</b>置信度使用 {@link BigDecimal} 表示，避免浮点精度问题。
 *
 * @param id 唯一 ID
 * @param fromEntityId 起始实体 ID
 * @param toEntityId 目标实体 ID
 * @param type 关系类型
 * @param confidence 置信度（0-1，使用 BigDecimal 精度）
 * @param properties 扩展属性
 * @author ydsz-team
 * @since 26.09.17
 */
public record Relation(
    String id,
    String fromEntityId,
    String toEntityId,
    RelationType type,
    BigDecimal confidence,
    Map<String, String> properties) {

  /** 关系 ID 统一前缀 */
  public static final String ID_PREFIX = "relation:";

  /** 最小置信度 */
  private static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;

  /** 最大置信度 */
  private static final BigDecimal MAX_CONFIDENCE = BigDecimal.ONE;

  /**
   * 构造关系实例。
   *
   * <p>对 {@code confidence} 做边界校验（0-1），对 {@code properties} 做防御性拷贝。
   *
   * @throws NullPointerException 当必填字段为 null 时
   * @throws IllegalArgumentException 当 confidence 超出 [0,1] 范围时
   */
  public Relation {
    Objects.requireNonNull(id, "relation id 不能为 null");
    Objects.requireNonNull(fromEntityId, "fromEntityId 不能为 null");
    Objects.requireNonNull(toEntityId, "toEntityId 不能为 null");
    Objects.requireNonNull(type, "relation type 不能为 null");
    Objects.requireNonNull(confidence, "confidence 不能为 null");
    if (confidence.compareTo(MIN_CONFIDENCE) < 0 || confidence.compareTo(MAX_CONFIDENCE) > 0) {
      throw new IllegalArgumentException(
          "confidence 必须在 0-1 范围内，当前值: " + confidence);
    }
    properties = properties != null
        ? Collections.unmodifiableMap(properties)
        : Collections.emptyMap();
  }

  /**
   * 便捷构造器（不传扩展属性，默认置信度 0.8）。
   *
   * @param id 唯一 ID
   * @param fromEntityId 起始实体 ID
   * @param toEntityId 目标实体 ID
   * @param type 关系类型
   */
  public Relation(String id, String fromEntityId, String toEntityId, RelationType type) {
    this(id, fromEntityId, toEntityId, type, new BigDecimal("0.8"), Collections.emptyMap());
  }

  /**
   * 生成标准化关系 ID。
   *
   * <p>格式：{@code relation:{fromId}_{type}_{toId}}。
   *
   * @param fromEntityId 起始实体 ID
   * @param toEntityId 目标实体 ID
   * @param type 关系类型
   * @return 标准化 ID
   */
  /**
   * 生成标准化关系 ID。
   *
   * <p>格式：{@code relation:{fromId}_{type}_{toId}}。
   *
   * @param fromEntityId 起始实体 ID
   * @param toEntityId 目标实体 ID
   * @param type 关系类型
   * @return 标准化 ID
   */
  public static String generateId(String fromEntityId, String toEntityId, RelationType type) {
    return ID_PREFIX + fromEntityId + "_" + type.name() + "_" + toEntityId;
  }

  /**
   * 获取指定属性值。
   *
   * @param key 属性键
   * @return 属性值，不存在时返回 null
   */
  public String getProperty(String key) {
    return properties != null ? properties.get(key) : null;
  }
}
