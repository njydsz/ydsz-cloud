package com.njydsz.common.tenant.validation;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.common.tenant.config.TenantProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 租户表索引校验器。
 *
 * <p>在应用启动后扫描所有配置了租户隔离的表，检查是否存在包含租户列的索引。
 * 缺失索引时输出 WARN 日志，提示 DBA/开发者添加合适的联合索引。
 *
 * <p><b>校验逻辑：</b>
 * <ol>
 *   <li>查询数据库中所有业务表（排除 ignore-tables 中的表）</li>
 *   <li>检查每个表的索引是否包含 tenant_id 列（或 per-table 配置的列名）</li>
 *   <li>无联合索引时输出告警日志，建议索引格式：{@code idx_{table}_tenant (tenant_id, ...)}</li>
 * </ol>
 *
 * <p><b>配置：</b>通过 {@code ydsz.tenant.validation.index-check.enabled=true} 启用，
 * 默认开启。生产环境可在确认索引无误后关闭以减少启动耗时。
 *
 * <p><b>注意：</b>此校验器仅检查索引是否<b>包含</b>租户列，不验证索引顺序或覆盖度。
 * 对于大表的复杂查询路径，建议结合 EXPLAIN 进一步分析。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.tenant.validation.index-check",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantIndexValidator {

    private final DataSource dataSource;
    private final TenantProperties properties;

    public TenantIndexValidator(DataSource dataSource, TenantProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * 应用启动完成后执行索引校验。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateIndexes() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("开始校验租户表索引...");
        List<IndexWarning> warnings = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            Set<String> ignoreTables = properties.getNormalizedIgnoreTables();

            // 获取所有表
            try (ResultSet tables = metaData.getTables(conn.getCatalog(), conn.getSchema(),
                    null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName == null || ignoreTables.contains(tableName.toLowerCase())) {
                        continue;
                    }
                    // 检查该表是否有租户列
                    String tenantColumn = detectTenantColumn(metaData, conn, tableName);
                    if (tenantColumn != null) {
                        // 验证索引是否包含租户列
                        if (!hasIndexIncludingColumn(metaData, conn, tableName, tenantColumn)) {
                            warnings.add(new IndexWarning(tableName, tenantColumn));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("索引校验失败（不影响启动）: {}", e.getMessage());
            return;
        }

        if (warnings.isEmpty()) {
            log.info("租户表索引校验通过，所有含租户列的表均有合适的联合索引。");
        } else {
            log.warn("检测到 {} 个表缺少包含租户列的索引：", warnings.size());
            for (IndexWarning warning : warnings) {
                log.warn("  表 [{}] 缺少包含列 [{}] 的联合索引。建议：CREATE INDEX idx_{}_{} ON {} ({}, ...);",
                        warning.tableName,
                        warning.tenantColumn,
                        warning.tableName,
                        warning.tenantColumn,
                        warning.tableName,
                        warning.tenantColumn);
            }
        }
    }

    /**
     * 检测表中是否存在租户列（通过配置映射或默认列名）。
     *
     * @param metaData  数据库元数据
     * @param conn      数据库连接
     * @param tableName 表名
     * @return 租户列名，不存在返回 null
     */
    private String detectTenantColumn(DatabaseMetaData metaData, Connection conn, String tableName)
            throws SQLException {
        // 先检查 per-table 映射
        String mapped = properties.resolveColumn(tableName);
        if (mapped != null) {
            if (columnExists(metaData, conn, tableName, mapped)) {
                return mapped;
            }
        }
        // 检查默认租户列名
        String defaultColumn = properties.getActiveTenantFields().get(0).getColumn();
        if (columnExists(metaData, conn, tableName, defaultColumn)) {
            return defaultColumn;
        }
        return null;
    }

    /**
     * 检查列是否存在。
     */
    private boolean columnExists(DatabaseMetaData metaData, Connection conn,
                                  String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(conn.getCatalog(), conn.getSchema(),
                tableName, columnName)) {
            return columns.next();
        }
    }

    /**
     * 检查表是否有包含指定列的索引。
     */
    private boolean hasIndexIncludingColumn(DatabaseMetaData metaData, Connection conn,
                                             String tableName, String columnName) throws SQLException {
        try (ResultSet indexes = metaData.getIndexInfo(conn.getCatalog(), conn.getSchema(),
                tableName, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                String colName = indexes.getString("COLUMN_NAME");
                // 跳过主键索引
                if (indexName != null && indexName.equalsIgnoreCase("PRIMARY")) {
                    continue;
                }
                if (columnName.equalsIgnoreCase(colName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 索引告警信息。
     */
    private record IndexWarning(String tableName, String tenantColumn) {
    }

    /**
     * 生成校验报告（可扩展为写入文件或上报到监控系统）。
     *
     * @return 校验报告 Map
     */
    public Map<String, Object> generateReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("enabled", properties.isEnabled());
        report.put("tenantColumn", properties.getTenantColumn());
        report.put("ignoreTables", properties.getNormalizedIgnoreTables());
        return report;
    }
}
