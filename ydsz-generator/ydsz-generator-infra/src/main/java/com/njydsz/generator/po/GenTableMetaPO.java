package com.njydsz.generator.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 表元数据缓存持久化对象。
 *
 * <p>对应 gen_table_meta 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("ydsz_gen_table_meta")
public class GenTableMetaPO {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 数据源 ID。 */
  private Long datasourceId;
  /** 物理表名。 */
  private String tableName;
  /** 表注释。 */
  private String comment;
  /** 别名。 */
  private String aliasName;
  /** 模块名称。 */
  private String moduleName;
  /** 缓存时间。 */
  private LocalDateTime cachedAt;
}
