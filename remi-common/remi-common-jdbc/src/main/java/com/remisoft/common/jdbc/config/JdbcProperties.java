package com.remisoft.common.jdbc.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * JDBC 模块统一配置属性类
 *
 * <p>提供 remi.jdbc 前缀的全局配置，包括模块开关、Mapper 扫描包、慢 SQL 监控等。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * remi:
 *   jdbc:
 *     enabled: true
     *     mapper-scan-packages: com.remisoft.**.mapper
 *     slow-sql:
 *       enabled: true
 *       threshold-millis: 1000
 *       alert-threshold-millis: 3000
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "remi.jdbc")
public class JdbcProperties {

    /**
     * 是否启用 JDBC 模块（默认 true）
     */
    private boolean enabled = true;

    /**
     * Mapper 扫描包路径数组（默认 com.remisoft.**.mapper）
     */
    private List<String> mapperScanPackages = Arrays.asList("com.remisoft.**.mapper");

    /**
     * 慢 SQL 监控配置
     */
    private SlowSql slowSql = new SlowSql();

    /**
     * SQL 审计配置
     */
    private SqlAudit sqlAudit = new SqlAudit();

    /**
     * 安全查询配置（ORDER BY 注入防护 + 深度分页检测）
     */
    private SafeQuery safeQuery = new SafeQuery();

    /**
     * 慢 SQL 监控配置属性
     */
    @Data
    @Validated
    public static class SlowSql {

        /**
         * 是否启用慢 SQL 监控（默认 false）
         */
        private boolean enabled = false;

        /**
         * 慢 SQL 检测阈值（毫秒），超过此阈值的 SQL 将被记录警告
         */
        @Min(1)
        private long thresholdMillis = 1000L;

        /**
         * 慢 SQL 告警阈值（毫秒），超过此值输出告警日志并打印调用堆栈
         */
        @Min(1)
        private long alertThresholdMillis = 3000L;
    }

    /**
     * SQL 审计配置属性
     */
    @Data
    @Validated
    public static class SqlAudit {

        /**
         * 是否启用 SQL 审计（默认 false）
         */
        private boolean enabled = false;

        /**
         * 是否审计 SELECT 语句（默认 false，生产环境建议关闭）
         */
        private boolean auditSelect = false;

        /**
         * 是否审计 INSERT 语句（默认 true）
         */
        private boolean auditInsert = true;

        /**
         * 是否审计 UPDATE 语句（默认 true）
         */
        private boolean auditUpdate = true;

        /**
         * 是否审计 DELETE 语句（默认 true）
         */
        private boolean auditDelete = true;

        /**
         * 是否记录 SQL 参数（默认 true）
         */
        private boolean logParameters = true;

        /**
         * 参数最大长度（超过则截断，默认 500）
         */
        @Min(1)
        private int maxParameterLength = 500;

        /**
         * 排除的表名列表（不审计这些表的 SQL）
         */
        private List<String> excludeTables;

        /**
         * 排除的 Mapper 方法名列表（不审计这些方法的 SQL）
         */
        private List<String> excludeMethods;
    }

    /**
     * 安全查询配置属性（ORDER BY 注入防护 + 深度分页检测）
     *
     * @since 1.7.0
     */
    @Data
    @Validated
    public static class SafeQuery {

        /**
         * 是否启用安全查询拦截（默认 true）
         */
        private boolean enabled = true;

        /**
         * 严格模式（默认 false）
         *
         * <ul>
         *   <li>true: 非法排序字段抛出异常</li>
         *   <li>false: 忽略非法排序字段（仅日志警告）</li>
         * </ul>
         */
        private boolean strictMode = false;

        /**
         * 排序字段白名单
         *
         * <p>配置后，仅允许白名单中的字段参与排序。为空时仅使用正则校验。
         */
        private Set<String> orderByWhitelist;
    }
}
