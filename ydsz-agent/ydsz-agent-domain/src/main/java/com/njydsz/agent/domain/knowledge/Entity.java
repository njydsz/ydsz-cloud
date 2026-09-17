package com.njydsz.agent.domain.knowledge;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 知识图谱中的实体节点。
 *
 * <p>代表从文档中抽取的关键概念、人物、地点、组织等。
 * 实体是不可变值对象，通过 {@code id} 唯一标识。
 *
 * <p><b>不可变性</b>：所有字段为 final，properties 通过 {@link Collections#unmodifiableMap} 包装。
 *
 * @param id 唯一 ID（格式：entity:{type}:{name} 小写）
 * @param name 实体名称
 * @param type 实体类型
 * @param description 实体描述
 * @param sourceDocId 来源文档 ID
 * @param properties 扩展属性
 * @author ydsz-team
 * @since 26.09.17
 */
public record Entity(
    String id,
    String name,
    EntityType type,
    String description,
    String sourceDocId,
    Map<String, String> properties) {

  /** 实体 ID 统一前缀 */
  public static final String ID_PREFIX = "entity:";

  /**
   * 构造实体实例。
   *
   * <p>对 {@code properties} 做防御性拷贝并包装为不可变映射。
   *
   * @throws NullPointerException 当 id 或 name 或 type 为 null 时
   */
  public Entity {
    Objects.requireNonNull(id, "entity id 不能为 null");
    Objects.requireNonNull(name, "entity name 不能为 null");
    Objects.requireNonNull(type, "entity type 不能为 null");
    properties = properties != null
        ? Collections.unmodifiableMap(properties)
        : Collections.emptyMap();
  }

  /**
   * 便捷构造器（不传扩展属性）。
   *
   * @param id 唯一 ID
   * @param name 实体名称
   * @param type 实体类型
   * @param description 实体描述
   * @param sourceDocId 来源文档 ID
   */
  public Entity(String id, String name, EntityType type, String description, String sourceDocId) {
    this(id, name, type, description, sourceDocId, Collections.emptyMap());
  }

  /**
   * 生成标准化的实体 ID。
   *
   * <p>格式：{@code entity:{type}:{name}}，名称统一转为小写并去除首尾空白。
   *
   * @param name 实体名称
   * @param type 实体类型
   * @return 标准化 ID
   */
  public static String generateId(String name, EntityType type) {
    Objects.requireNonNull(name, "name 不能为 null");
    Objects.requireNonNull(type, "type 不能为 null");
    String normalizedName = name.trim().toLowerCase().replaceAll("\\s+", "_");
    String typeStr = type.name().toLowerCase();
    return ID_PREFIX + typeStr + ":" + normalizedName;
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
