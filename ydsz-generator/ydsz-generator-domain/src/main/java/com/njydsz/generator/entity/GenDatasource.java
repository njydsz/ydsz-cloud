package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.generator.enums.DbDialectEnum;

/**
 * 代码生成器数据源配置实体。
 *
 * <p>管理数据库连接信息，支持多数据源切换与连接测试。
 *
 * <p><b>DDD 分层约束：</b>domain 层不引入任何 persistence 注解（如 @Table 等 MyBatis 注解），
 * 持久化映射统一在 infra 层的 PO 对象上声明。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenDatasource {

  /** 数据源 ID。 */
  private Long id;
  /** 数据源名称（唯一标识，如 master-db）。 */
  private String name;
  /** JDBC URL。 */
  private String jdbcUrl;
  /** 数据库用户名。 */
  private String username;
  /** 数据库密码（AES 加密存储）。 */
  private String password;
  /** 数据库方言。 */
  private DbDialectEnum dialect;
  /** 是否为默认数据源。 */
  private Boolean isDefault;
  /** 数据源描述。 */
  private String description;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
