package com.njydsz.generator.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源响应 VO（外向传输）。
 *
 * <p>排除敏感字段 password，仅在系统内部需要时使用 {@code GenDatasource} 实体。
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenDatasourceRespVO {

  /** 主键 ID。 */
  private Long id;
  /** 数据源名称。 */
  private String name;
  /** JDBC URL。 */
  private String jdbcUrl;
  /** 用户名。 */
  private String username;
  /** 数据库方言。 */
  private String dialect;
  /** 是否默认数据源。 */
  private Boolean default;
  /** 描述。 */
  private String description;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
