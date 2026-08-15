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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
 * <p><b>装配守卫：</b>由 {@code TenantAutoConfiguration} 以
 * {@code @ConditionalOnBean(DataSource.class)} 条件注册，
 * 避免非 JDBC 场景（纯 WebFlux/网关/无数据库模块）启动失败。
 *
 * <p><b>异步化：</b>校验逻辑在独立单线程池中执行，不阻塞主线程的
 * {@code ApplicationReadyEvent} 处理和应用就绪。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
public class TenantIndexValidator {

    private final DataSource dataSource;
    private final TenantProperties properties;
    private final ObjectProvider<ThreadPoolTaskExecutor> taskExecutorProvider;

    public TenantIndexValidator(DataSource dataSource, TenantProperties properties,
                                ObjectProvider<ThreadPoolTaskExecutor> taskExecutorProvider) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.taskExecutorProvider = taskExecutorProvider;
    }

    /**
     * 应用启动完成后异步执行索引校验。
     *
     * <p>校验在独立线程中执行，避免元数据查询阻塞应用就绪。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateIndexes() {
        if (!properties.isEnabled()) {
            return;
        }
        // 优先使用业务配置的线程池，否则使用临时守护线程
        ThreadPoolTaskExecutor executor = taskExecutorProvider.getIfAvailable();
        if (executor != null) {
            executor.submit(this::doValidateIndexes);
        } else {
            Thread thread = new Thread(this::doValidateIndexes, "tenant-index-validator");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * 实际校验逻辑（数据库元数据查询）。
     */
    private void doValidateIndexes() {
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
