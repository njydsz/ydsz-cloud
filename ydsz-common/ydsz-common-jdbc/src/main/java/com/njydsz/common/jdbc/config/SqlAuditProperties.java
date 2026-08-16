package com.njydsz.common.jdbc.config;

import java.util.List;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SQL 审计配置属性
 *
 * <p>配置示例：
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     sql-audit:
 *       enabled: true
 *       audit-select: false
 *       audit-insert: true
 *       audit-update: true
 *       audit-delete: true
 *       log-parameters: true
 *       max-parameter-length: 500
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.sql-audit")
public class SqlAuditProperties {

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
