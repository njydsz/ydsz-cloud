package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 表元数据领域实体。
 *
 * <p>对应 ydsz_gen_table_meta 表，缓存数据库表的结构信息。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_table_meta")
public class GenTableMeta extends MpBaseEntity<String> {

  /** 数据源 ID。 */
  private String datasourceId;
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
