package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表元数据领域实体。
 *
 * <p>对应 ydsz_gen_table_meta 表，缓存数据库表的结构信息。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_gen_table_meta")
public class GenTableMeta {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 数据源 ID。 */
  private Long datasourceId;
  /** 物理表名。 */
  private String tableName;
  /** 表注释。 */
  private String comment;
  /** 别名（用于生成类名）。 */
  private String aliasName;
  /** 模块名称。 */
  private String moduleName;
  /** 缓存时间。 */
  private LocalDateTime cachedAt;
}
