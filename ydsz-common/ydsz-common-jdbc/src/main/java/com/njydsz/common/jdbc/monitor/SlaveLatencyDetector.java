package com.njydsz.common.jdbc.monitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 从库复制延迟检测器
 *
 * <p>通过 JDBC 查询数据库复制延迟，支持 MySQL 和 PostgreSQL。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 同步检测当前延迟
 * Optional<Duration> latency = detector.detect(masterDs);
 * if (latency.isPresent() && latency.get().compareTo(threshold) > 0) {
 *     // 延迟超标，降级走主库
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SlaveLatencyDetector {

    /**
     * 检测当前主从复制延迟
     *
     * @param dataSource 数据源（用于执行延迟查询）
     * @return 复制延迟（empty 表示无法检测或不是从库）
     */
    Optional<Duration> detect(DataSource dataSource);

    /**
     * 当前检测器是否支持指定的数据源类型
     *
     * @param dataSource 数据源
     * @return true 如果支持
     */
    boolean isSupported(DataSource dataSource);

    /**
     * MySQL 实现：通过 SHOW SLAVE STATUS 获取 Seconds_Behind_Master
     */
    @Slf4j
    class MysqlLatencyDetector implements SlaveLatencyDetector {

        private static final String MYSQL_LATENCY_SQL = "SHOW SLAVE STATUS";

        @Override
        public Optional<Duration> detect(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(MYSQL_LATENCY_SQL)) {
                if (rs.next()) {
                    String seconds = rs.getString("Seconds_Behind_Master");
                    if (seconds != null && !"NULL".equals(seconds)) {
                        try {
                            long secs = Long.parseLong(seconds);
                            return Optional.of(Duration.ofSeconds(secs));
                        } catch (NumberFormatException e) {
                            log.warn("MySQL Seconds_Behind_Master 解析失败: {}", seconds);
                        }
                    }
                }
            } catch (SQLException e) {
                log.debug("MySQL 复制延迟检测失败（可能不是从库或未配置复制）: {}", e.getMessage());
            }
            return Optional.empty();
        }

        @Override
        public boolean isSupported(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection()) {
                String url = conn.getMetaData().getURL();
                return url != null && url.toLowerCase().contains("mysql");
            } catch (SQLException e) {
                return false;
            }
        }
    }

    /**
     * PostgreSQL 实现：通过 pg_stat_replication 获取复制延迟
     */
    @Slf4j
    class PostgreSqlLatencyDetector implements SlaveLatencyDetector {

        // 查询当前实例作为备库的复制延迟（需要在上游主库的 pg_stat_replication 上查询，
        // 或者使用 pg_last_xact_replay_timestamp() 计算延迟）
        private static final String PG_LATENCY_SQL =
                "SELECT CASE WHEN pg_is_in_recovery() " +
                "THEN EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) " +
                "ELSE 0 END AS lag_seconds";

        @Override
        public Optional<Duration> detect(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(PG_LATENCY_SQL)) {
                if (rs.next()) {
                    double seconds = rs.getDouble("lag_seconds");
                    if (!rs.wasNull() && seconds >= 0) {
                        return Optional.of(Duration.ofMillis((long) (seconds * 1000)));
                    }
                }
            } catch (SQLException e) {
                log.debug("PostgreSQL 复制延迟检测失败: {}", e.getMessage());
            }
            return Optional.empty();
        }

        @Override
        public boolean isSupported(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection()) {
                String url = conn.getMetaData().getURL();
                return url != null && url.toLowerCase().contains("postgresql");
            } catch (SQLException e) {
                return false;
            }
        }
    }

    /**
     * 自动检测实现：根据数据源类型自动选择合适的检测器
     */
    @Slf4j
    class AutoDetectLatencyDetector implements SlaveLatencyDetector {

        private final SlaveLatencyDetector mysql = new MysqlLatencyDetector();
        private final SlaveLatencyDetector postgresql = new PostgreSqlLatencyDetector();
        private volatile SlaveLatencyDetector delegate;

        @Override
        public Optional<Duration> detect(DataSource dataSource) {
            if (delegate == null) {
                delegate = detectDelegate(dataSource);
            }
            if (delegate == null) {
                return Optional.empty();
            }
            return delegate.detect(dataSource);
        }

        @Override
        public boolean isSupported(DataSource dataSource) {
            return detectDelegate(dataSource) != null;
        }

        private SlaveLatencyDetector detectDelegate(DataSource dataSource) {
            if (mysql.isSupported(dataSource)) {
                return mysql;
            }
            if (postgresql.isSupported(dataSource)) {
                return postgresql;
            }
            log.warn("不支持的数据库类型，无法检测复制延迟");
            return null;
        }
    }
}
