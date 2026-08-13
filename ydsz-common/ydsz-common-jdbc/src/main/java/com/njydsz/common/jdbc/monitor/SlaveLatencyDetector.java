package com.njydsz.common.jdbc.monitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.core.Ordered;

import lombok.extern.slf4j.Slf4j;

/**
 * 从库复制延迟检测器 SPI
 *
 * <p>通过 JDBC 查询数据库复制延迟，支持 MySQL、PostgreSQL、Oracle。
 * 扩展方可通过 {@link Ordered} 接口控制检测器匹配优先级（值越小越优先）。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 自动检测当前延迟
 * Optional<Duration> latency = detector.detect(masterDs);
 * if (latency.isPresent() && latency.get().compareTo(threshold) > 0) {
 *     // 延迟超标，降级走主库
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SlaveLatencyDetector extends Ordered {

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
     * 默认优先级（最低）
     *
     * @return Ordered.LOWEST_PRECEDENCE
     */
    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * MySQL 实现：通过 SHOW SLAVE STATUS 获取 Seconds_Behind_Master
     *
     * @since 1.0.0
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

        @Override
        public int getOrder() {
            return 100;
        }
    }

    /**
     * PostgreSQL 实现：通过 pg_last_xact_replay_timestamp() 计算延迟
     *
     * @since 1.0.0
     */
    @Slf4j
    class PostgreSqlLatencyDetector implements SlaveLatencyDetector {

        private static final String PG_LATENCY_SQL =
                "SELECT CASE WHEN pg_is_in_recovery() "
                + "THEN EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) "
                + "ELSE 0 END AS lag_seconds";

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

        @Override
        public int getOrder() {
            return 200;
        }
    }

    /**
     * Oracle Data Guard 实现：查询 V$DATAGUARD_STATS 获取延迟
     *
     * @since 1.8.0
     */
    @Slf4j
    class OracleLatencyDetector implements SlaveLatencyDetector {

        private static final String ORACLE_LATENCY_SQL =
                "SELECT value FROM V$DATAGUARD_STATS WHERE name = 'apply lag'";

        @Override
        public Optional<Duration> detect(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(ORACLE_LATENCY_SQL)) {
                if (rs.next()) {
                    String lagValue = rs.getString("value");
                    if (lagValue != null && !lagValue.isEmpty()) {
                        return parseOracleLag(lagValue);
                    }
                }
            } catch (SQLException e) {
                log.debug("Oracle 复制延迟检测失败: {}", e.getMessage());
            }
            return Optional.empty();
        }

        /**
         * 解析 Oracle 延迟值字符串
         *
         * <p>Oracle 格式通常为 "+00 00:00:05" （日 时:分:秒）
         *
         * @param lagValue Oracle 延迟字符串
         * @return 解析后的 Duration
         */
        private Optional<Duration> parseOracleLag(String lagValue) {
            try {
                // 格式: "+00 00:00:05" 或 "+00 00:00:05.234"
                String trimmed = lagValue.trim();
                if (trimmed.length() < 9) {
                    return Optional.empty();
                }
                // 跳过符号位和日期部分，解析 HH:MM:SS
                String[] parts = trimmed.split(" ");
                if (parts.length < 2) {
                    return Optional.empty();
                }
                String timePart = parts[1];
                String[] timeParts = timePart.split(":");
                if (timeParts.length != 3) {
                    return Optional.empty();
                }
                int hours = Integer.parseInt(timeParts[0]);
                int minutes = Integer.parseInt(timeParts[1]);
                int seconds = Integer.parseInt(timeParts[2].split("\\.")[0]);
                return Optional.of(Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds));
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                log.warn("Oracle lag 值解析失败: {}", lagValue);
                return Optional.empty();
            }
        }

        @Override
        public boolean isSupported(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection()) {
                String url = conn.getMetaData().getURL();
                return url != null && url.toLowerCase().contains("oracle");
            } catch (SQLException e) {
                return false;
            }
        }

        @Override
        public int getOrder() {
            return 300;
        }
    }

    /**
     * 自动组合检测器：按 Order 排序遍历所有探测器，选择第一个支持的进行延迟检测
     *
     * <p>支持 SPI 扩展：通过 {@link #setDetectors(List)} 注入自定义探测器
     *
     * @since 1.8.0
     */
    @Slf4j
    class CompositeLatencyDetector implements SlaveLatencyDetector {

        private final List<SlaveLatencyDetector> detectors;

        /**
         * 构造组合探测器
         *
         * @param detectors 探测器列表（已按 Order 排序）
         */
        public CompositeLatencyDetector(List<SlaveLatencyDetector> detectors) {
            this.detectors = detectors;
        }

        @Override
        public Optional<Duration> detect(DataSource dataSource) {
            for (SlaveLatencyDetector detector : detectors) {
                if (detector.isSupported(dataSource)) {
                    return detector.detect(dataSource);
                }
            }
            log.warn("无匹配的延迟检测器，数据源: {}", dataSource.getClass().getName());
            return Optional.empty();
        }

        @Override
        public boolean isSupported(DataSource dataSource) {
            return detectors.stream().anyMatch(d -> d.isSupported(dataSource));
        }
    }
}
