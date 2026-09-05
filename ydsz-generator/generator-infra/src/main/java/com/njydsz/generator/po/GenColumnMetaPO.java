package com.njydsz.generator.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 列元数据持久化对象。
 *
 * <p>对应 gen_column_meta 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("ydsz_gen_column_meta")
public class GenColumnMetaPO {

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
  private Boolean isPk;
  /** 字段注释。 */
  private String comment;
  /** 覆盖 Java 类型。 */
  private String overrideJavaType;
  /** 覆盖字段名。 */
  private String overrideFieldName;
  /** DTO 跳过标记。 */
  private Boolean skipDto;
  /** VO 跳过标记。 */
  private Boolean skipVo;
  /** Query 跳过标记。 */
  private Boolean skipQuery;
  /** 扩展配置 JSON。 */
  private String extraConfig;
}
