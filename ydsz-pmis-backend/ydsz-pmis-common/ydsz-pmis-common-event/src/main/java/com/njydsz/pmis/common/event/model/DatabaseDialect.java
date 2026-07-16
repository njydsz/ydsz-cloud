package com.njydsz.pmis.common.event.model;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据库方言枚举
 *
 * <p>用于 OutboxRepository 根据数据库类型适配 SQL 语法和 JSON 列处理。
 * 自动从 JDBC URL 检测，也可通过配置显式指定。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DatabaseDialect {

    /** PostgreSQL */
    POSTGRESQL,

    /** MySQL 8.x+ */
    MYSQL,

    /** Oracle 19c+ */
    ORACLE,

    /** 未知方言，使用通用 SQL */
    UNKNOWN;

    private static final Logger log = LoggerFactory.getLogger(DatabaseDialect.class);

    /**
     * 从 DataSource 自动检测数据库方言
     *
     * @param dataSource 数据源
     * @return 检测到的方言，检测失败返回 UNKNOWN
     */
    public static DatabaseDialect detect(DataSource dataSource) {
        if (dataSource == null) {
            return UNKNOWN;
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            if (productName == null) {
                return UNKNOWN;
            }
            String name = productName.toLowerCase();
            if (name.contains("postgresql")) {
                return POSTGRESQL;
            }
            if (name.contains("mysql")) {
                return MYSQL;
            }
            if (name.contains("oracle")) {
                return ORACLE;
            }
            log.warn("Unknown database product name: {}, using UNKNOWN dialect", productName);
            return UNKNOWN;
        } catch (SQLException e) {
            log.warn("Failed to detect database dialect", e);
            return UNKNOWN;
        }
    }

    /**
     * 构建 LIMIT 子句
     *
     * <p>PostgreSQL/MySQL 使用 {@code LIMIT ?}，
     * Oracle 使用 {@code FETCH FIRST ? ROWS ONLY}，
     * UNKNOWN 使用 {@code LIMIT ?}（兼容大部分数据库）。
     *
     * @return LIMIT 子句 SQL 片段
     */
    public String limitClause() {
        return switch (this) {
            case ORACLE -> " FETCH FIRST ? ROWS ONLY";
            default -> " LIMIT ?";
        };
    }

}
