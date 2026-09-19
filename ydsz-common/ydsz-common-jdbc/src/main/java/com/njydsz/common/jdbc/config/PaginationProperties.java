package com.njydsz.common.jdbc.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 分页拦截器配置类
 *
 * <p>配置 MyBatis-Plus PaginationInnerInterceptor 的行为参数。
 *
 * <p>配置示例：
 *
 * <pre>
 * # application.yml
 * ydsz:
 *   jdbc:
 *     pagination:
 *       db-type: mysql            # 数据库类型（可选，不配置则自动检测）
 *       max-limit: 500            # 单页最大记录数（防止全表扫描）
 *       overflow: false           # 页码溢出是否继续查询
 *       optimize-count: true      # 自动裁剪 COUNT 语句的 JOIN/GROUP BY
 * </pre>
 *
 * <p><b>优化说明：</b>
 *
 * <ul>
 *   <li>显式指定 dbType 可避免运行时自动检测数据库类型的性能开销
 *   <li>设置 maxLimit 可防止恶意或误操作导致的全表扫描（安全加固）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see PaginationInnerInterceptor
 */
@Data
@Configuration
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
@ConfigurationProperties(prefix = "ydsz.jdbc.pagination")
public class PaginationProperties {

  /**
   * 数据库类型（可选）
   *
   * <p>支持的类型：mysql, oracle, postgresql, sqlserver, db2, h2, sqlite, mariadb 等。
   *
   * <p>不配置时由 MyBatis-Plus 自动检测。
   */
  private String dbType;

  /**
   * 单页最大记录数
   *
   * <p>防止无限制查询导致的全表扫描和 OOM。
   *
   * <p>默认值：500
   */
  private Long maxLimit = 500L;

  /**
   * 是否自动裁剪 COUNT 语句
   *
   * <p>开启后 MP 会自动移除与 COUNT 无关的 LEFT JOIN、ORDER BY、GROUP BY 等子句，
   * 提升分页 COUNT 查询的性能。仅当分页查询存在复杂 JOIN 时才会生效。
   *
   * <p>默认值：true
   */
  private boolean isOptimizeCount = true;

  /**
   * 页码溢出是否继续查询
   *
   * <p>true: 页码超出总页数时继续查询（返回空结果）
   *
   * <p>false: 页码超出总页数时停止查询
   *
   * <p>默认值：false
   */
  private boolean isOverflow = false;
}
