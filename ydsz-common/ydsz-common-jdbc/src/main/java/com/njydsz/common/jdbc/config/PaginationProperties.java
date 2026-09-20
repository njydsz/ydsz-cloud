package com.njydsz.common.jdbc.config;

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
@Configuration
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
@ConfigurationProperties(prefix = "ydsz.jdbc.pagination")
public class PaginationProperties {

  private String dbType;
  private Long maxLimit = 500L;
  private boolean isOptimizeCount = true;
  private boolean isOverflow = false;

  public String getDbType() { return dbType; }
  public void setDbType(String dbType) { this.dbType = dbType; }

  public Long getMaxLimit() { return maxLimit; }
  public void setMaxLimit(Long maxLimit) { this.maxLimit = maxLimit; }

  public boolean isOptimizeCount() { return isOptimizeCount; }
  public void setOptimizeCount(boolean isOptimizeCount) { this.isOptimizeCount = isOptimizeCount; }

  public boolean isOverflow() { return isOverflow; }
  public void setOverflow(boolean isOverflow) { this.isOverflow = isOverflow; }
}
