package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 列元数据领域实体。
 *
 * <p>对应 ydsz_gen_column_meta 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_column_meta")
public class GenColumnMeta extends MpBaseIdEntity<Long> {

  /** 主键 ID（AUTO 自增，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 租户 ID（多租户隔离）。 */
  private String tenantId;
  /** 所属表 ID。 */
  private Long tableMetaId;
  /** 列名。 */
  private String columnName;
  /** 数据类型（数据库原生类型名，如 VARCHAR、BIGINT）。 */
  private String dataType;
  /** Java 类型（由 dataType 经 type-mapping 自动推断，可被 overrideJavaType 覆盖）。 */
  private String javaType;
  /** 字段长度。 */
  private Integer columnSize;
  /** 是否可为空。 */
  private Boolean isNullable;
  /** 是否主键。 */
  private Boolean isPk;
  /** 字段注释。 */
  private String comment;
  /** 覆盖 Java 类型。 */
  private String overrideJavaType;
  /** 覆盖字段名。 */
  private String overrideFieldName;
  /** DTO 跳过标记。 */
  @TableField("is_dto_skipped")
  private Boolean isDtoSkipped;
  /** VO 跳过标记。 */
  @TableField("is_vo_skipped")
  private Boolean isVoSkipped;
  /** Query 跳过标记。 */
  @TableField("is_query_skipped")
  private Boolean isQuerySkipped;
  /** 是否为审计字段（审计字段由基类 MpBaseAuditEntity 提供，子类无需重复生成）。 */
  private Boolean isAuditField;
  /** 枚举值列表（从注释中自动解析，格式: 字段名(值1=标签1,值2=标签2)）。 */
  private String enumValues;
  /** 扩展配置 JSON。 */
  private String extraConfig;
}
