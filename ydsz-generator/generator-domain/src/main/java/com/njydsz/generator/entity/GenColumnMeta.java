package com.njydsz.generator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 列元数据缓存实体（含人工覆盖配置）。
 *
 * <p>存储每个字段的原始数据库属性 + 用户自定义的类型/名称/跳过规则覆盖。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenColumnMeta {

  /** 记录 ID。 */
  private Long id;
  /** 所属表元数据 ID。 */
  private Long tableMetaId;
  /** 物理列名。 */
  private String columnName;
  /** 物理数据类型。 */
  private String dataType;
  /** 字段长度。 */
  private Integer columnSize;
  /** 是否可为空。 */
  private Boolean nullable;
  /** 是否为主键。 */
  private Boolean isPk;
  /** 字段注释。 */
  private String comment;
  /** 人工覆盖 Java 类型（为空则使用自动映射）。 */
  private String overrideJavaType;
  /** 人工覆盖字段名（为空则使用自动命名）。 */
  private String overrideFieldName;
  /** 是否在 DTO 中跳过。 */
  private Boolean skipDto;
  /** 是否在 VO 中跳过。 */
  private Boolean skipVo;
  /** 是否在 Query 中跳过。 */
  private Boolean skipQuery;
  /** 扩展配置 JSON（枚举值、校验规则等）。 */
  private String extraConfig;
}
