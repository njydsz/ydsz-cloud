package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 列元数据领域实体。
 *
 * <p>对应 ydsz_gen_column_meta 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_gen_column_meta")
public class GenColumnMeta {

  /** 主键 ID。 */
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
  private Boolean nullable;
  /** 是否主键。 */
  private Boolean pk;
  /** 字段注释。 */
  private String comment;
  /** 覆盖 Java 类型。 */
  private String overrideJavaType;
  /** 覆盖字段名。 */
  private String overrideFieldName;
  /** DTO 跳过标记。 */
  private Boolean dtoSkipped;
  /** VO 跳过标记。 */
  private Boolean voSkipped;
  /** Query 跳过标记。 */
  private Boolean querySkipped;
  /** 扩展配置 JSON。 */
  private String extraConfig;
}
