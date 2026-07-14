package com.njydsz.pmis.common.jdbc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 分页拦截器配置类
 *
 * <p>配置 MyBatis-Plus PaginationInnerInterceptor 的行为参数。
 *
 * <p>配置示例：
 * <pre>
 * # application.yml
 * ydsz:
 *   sql-intercept:
 *     pagination:
 *       db-type: mysql          # 数据库类型（可选，不配置则自动检测）
 *       max-limit: 500          # 单页最大记录数（防止全表扫描）
 *       overflow: false         # 页码溢出是否继续查询
 * </pre>
 *
 * <p><b>优化说明：</b>
 * <ul>
 *   <li>显式指定 dbType 可避免运行时自动检测数据库类型的性能开销</li>
 *   <li>设置 maxLimit 可防止恶意或误操作导致的全表扫描（安全加固）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see PaginationInnerInterceptor
 */
@Data
@Configuration
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
@ConfigurationProperties(prefix = "ydsz.jdbc.pagination")
public class PaginationProperties {

    /**
     * 数据库类型（可选）
     * <p>支持的类型：mysql, oracle, postgresql, sqlserver, db2, h2, sqlite, mariadb 等。
     * <p>不配置时由 MyBatis-Plus 自动检测。
     */
    private String dbType;

    /**
     * 单页最大记录数
     * <p>防止无限制查询导致的全表扫描和 OOM。
     * <p>默认值：500
     */
    private Long maxLimit = 500L;

    /**
     * 页码溢出是否继续查询
     * <p>true: 页码超出总页数时继续查询（返回空结果）
     * <p>false: 页码超出总页数时停止查询
     * <p>默认值：false
     */
    private boolean overflow = false;
}
