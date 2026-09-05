package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表元数据缓存实体。
 *
 * <p>缓存数据库表结构信息，避免频繁查询数据库 metadata。
 * 支持人工校正 aliasName/moduleName。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenTableMeta {

  /** 记录 ID。 */
  private Long id;
  /** 关联数据源 ID。 */
  private Long datasourceId;
  /** 物理表名。 */
  private String tableName;
  /** 表注释。 */
  private String comment;
  /** 用户自定义别名（用于类名生成，如 t_user -> User）。 */
  private String aliasName;
  /** 模块名称（用于包路径）。 */
  private String moduleName;
  /** 缓存时间。 */
  private LocalDateTime cachedAt;
}
