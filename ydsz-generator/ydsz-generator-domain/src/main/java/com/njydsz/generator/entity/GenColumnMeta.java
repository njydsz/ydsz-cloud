package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
  /** 所属表 ID。 */
  private Long tableMetaId;
  /** 列名。 */
  private String columnName;
  /** 数据类型。 */
  private String dataType;
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
  private Boolean isDtoSkipped;
  /** VO 跳过标记。 */
  private Boolean isVoSkipped;
  /** Query 跳过标记。 */
  private Boolean isQuerySkipped;
  /** 扩展配置 JSON。 */
  private String extraConfig;
}
