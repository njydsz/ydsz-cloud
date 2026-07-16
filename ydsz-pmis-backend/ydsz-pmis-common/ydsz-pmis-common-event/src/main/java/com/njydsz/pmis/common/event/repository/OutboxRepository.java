package com.njydsz.pmis.common.event.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.pmis.common.event.model.DatabaseDialect;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.json.exception.JsonException;
import com.njydsz.pmis.common.json.type.JsonType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/**
 * Outbox 消息 JDBC 仓储
 *
 * <p>使用 {@link JdbcTemplate} 直接操作数据库，不依赖 ORM 框架。
 * 所有多写操作在调用方的数据库事务中执行。
 *
 * <p>支持多实例部署的原子 claim 机制：通过 {@code UPDATE ... WHERE status = 'PENDING'}
 * 原子地将消息状态从 PENDING 改为 PROCESSING，确保同一消息只被一个实例处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class OutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(OutboxRepository.class);

    private static final JsonType<Map<String, String>> MAP_TYPE = new JsonType<>() {};

    /** 表名合法字符校验正则（防 SQL 注入） */
    private static final String TABLE_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final DatabaseDialect dialect;

    /**
     * @param jdbcTemplate JDBC 模板
     * @param tableName     Outbox 表名（默认 pmis_outbox）
     */
    public OutboxRepository(JdbcTemplate jdbcTemplate, String tableName) {
        this(jdbcTemplate, tableName, DatabaseDialect.UNKNOWN);
    }

    /**
     * @param jdbcTemplate JDBC 模板
     * @param tableName     Outbox 表名（默认 pmis_outbox）
     * @param dialect       数据库方言
     */
    public OutboxRepository(JdbcTemplate jdbcTemplate, String tableName, DatabaseDialect dialect) {
        if (tableName == null || !tableName.matches(TABLE_NAME_PATTERN)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.dialect = dialect != null ? dialect : DatabaseDialect.UNKNOWN;
    }

    /**
     * 插入 Outbox 消息（在当前事务中执行）
     *
     * @param message 消息实体
     */
    public void save(OutboxMessage message) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName(tableName);

        Map<String, Object> params = new HashMap<>(16);
        params.put("id", message.getId());
        params.put("aggregate_id", message.getAggregateId());
        params.put("aggregate_type", message.getAggregateType());
        params.put("event_type", message.getEventType());
        params.put("payload", message.getPayload());
        params.put("headers", serializeHeaders(message.getHeaders()));
        params.put("status", message.getStatus().name());
        params.put("retry_count", message.getRetryCount());
        params.put("max_retries", message.getMaxRetries());
        params.put("next_retry_at", Timestamp.from(message.getNextRetryAt()));
        params.put("created_at", Timestamp.from(message.getCreatedAt()));
        params.put("updated_at", Timestamp.from(message.getUpdatedAt()));
        params.put("tenant_id", message.getTenantId());
        params.put("deduplication_id", message.getDeduplicationId());
        params.put("schema_version", message.getSchemaVersion());
        params.put("content_type", message.getContentType());
        params.put("priority", message.getPriority());
        params.put("trace_id", message.getTraceId());
        insert.execute(params);
    }

    /**
     * 查询待投递的消息（按优先级降序、创建时间升序）
     *
     * @param limit 最大条数
     * @return 待投递消息列表
     */
    public List<OutboxMessage> findPending(int limit) {
        String sql = "SELECT * FROM " + tableName
                + " WHERE status = ? AND (next_retry_at IS NULL OR next_retry_at <= ?)"
                + " ORDER BY priority DESC, created_at ASC"
                + dialect.limitClause();
        return jdbcTemplate.query(sql, new OutboxRowMapper(),
                OutboxStatus.PENDING.name(), Timestamp.from(Instant.now()), limit);
    }

    /**
     * 原子 claim 消息：将指定消息状态从 PENDING 改为 PROCESSING
     *
     * <p>多实例部署时，仅一个实例能成功 claim（UPDATE 影响行数为 1），
     * 其余实例的 UPDATE 不会匹配到该行（status 已变为 PROCESSING）。
     *
     * @param id 消息 ID
     * @return true 表示 claim 成功，false 表示消息已被其他实例 claim
     */
    public boolean claimForProcessing(String id) {
        String sql = "UPDATE " + tableName
                + " SET status = ?, updated_at = ?"
                + " WHERE id = ? AND status = ?";
        int affected = jdbcTemplate.update(sql,
                OutboxStatus.PROCESSING.name(),
                Timestamp.from(Instant.now()),
                id,
                OutboxStatus.PENDING.name());
        return affected > 0;
    }

    /**
     * 回收超时的 PROCESSING 消息（实例宕机后恢复）
     *
     * <p>将 updated_at 早于指定时间的 PROCESSING 消息重置为 PENDING。
     *
     * @param thresholdMinutes 超时阈值（分钟）
     * @return 回收的消息数量
     */
    public int reclaimStaleProcessing(int thresholdMinutes) {
        Instant cutoff = Instant.now().minusSeconds(thresholdMinutes * 60L);
        String sql = "UPDATE " + tableName
                + " SET status = ?, updated_at = ?"
                + " WHERE status = ? AND updated_at < ?";
        int affected = jdbcTemplate.update(sql,
                OutboxStatus.PENDING.name(),
                Timestamp.from(Instant.now()),
                OutboxStatus.PROCESSING.name(),
                Timestamp.from(cutoff));
        if (affected > 0) {
            log.warn("Reclaimed {} stale PROCESSING messages older than {} minutes", affected, thresholdMinutes);
        }
        return affected;
    }

    /**
     * 更新消息状态为已投递
     *
     * @param id 消息 ID
     */
    public void markAsSent(String id) {
        String sql = "UPDATE " + tableName
                + " SET status = ?, sent_at = ?, updated_at = ?, error_message = NULL"
                + " WHERE id = ?";
        jdbcTemplate.update(sql,
                OutboxStatus.SENT.name(),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                id);
    }

    /**
     * 更新消息为失败，增加重试计数
     *
     * @param id             消息 ID
     * @param errorMessage    错误信息
     * @param backoffSeconds  退避秒数
     */
    public void markAsFailed(String id, String errorMessage, long backoffSeconds) {
        String sql = "UPDATE " + tableName
                + " SET retry_count = retry_count + 1, error_message = ?,"
                + " next_retry_at = ?, updated_at = ?,"
                + " status = CASE WHEN retry_count + 1 >= max_retries THEN ? ELSE ? END"
                + " WHERE id = ?";
        jdbcTemplate.update(sql,
                errorMessage,
                Timestamp.from(Instant.now().plusSeconds(backoffSeconds)),
                Timestamp.from(Instant.now()),
                OutboxStatus.DEAD_LETTER.name(),
                OutboxStatus.PENDING.name(),
                id);
    }

    /**
     * 统计各状态消息数
     *
     * @return 状态 → 数量
     */
    public Map<String, Long> countByStatus() {
        String sql = "SELECT status, COUNT(*) as cnt FROM " + tableName + " GROUP BY status";
        return jdbcTemplate.query(sql, rs -> {
            Map<String, Long> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("status"), rs.getLong("cnt"));
            }
            return result;
        });
    }

    /**
     * 清理已投递的消息（定期维护）
     *
     * @param beforeTime 早于此时间的 SENT 消息将被删除
     * @return 删除条数
     */
    public int deleteSentBefore(Instant beforeTime) {
        String sql = "DELETE FROM " + tableName + " WHERE status = ? AND sent_at < ?";
        return jdbcTemplate.update(sql, OutboxStatus.SENT.name(), Timestamp.from(beforeTime));
    }

    /**
     * 按 tenantId 查询各状态消息数
     *
     * @param tenantId 租户 ID
     * @return 状态 → 数量
     */
    public Map<String, Long> countByStatusAndTenant(String tenantId) {
        String sql = "SELECT status, COUNT(*) as cnt FROM " + tableName
                + " WHERE tenant_id = ? GROUP BY status";
        return jdbcTemplate.query(sql, rs -> {
            Map<String, Long> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("status"), rs.getLong("cnt"));
            }
            return result;
        }, tenantId);
    }

    /**
     * 按 tenantId 清理已投递的消息
     *
     * @param tenantId   租户 ID
     * @param beforeTime 早于此时间的 SENT 消息将被删除
     * @return 删除条数
     */
    public int deleteSentBeforeByTenant(String tenantId, Instant beforeTime) {
        String sql = "DELETE FROM " + tableName
                + " WHERE status = ? AND tenant_id = ? AND sent_at < ?";
        return jdbcTemplate.update(sql, OutboxStatus.SENT.name(), tenantId, Timestamp.from(beforeTime));
    }

    /**
     * 根据 deduplicationId 查询是否已存在
     *
     * @param deduplicationId 幂等去重 ID
     * @return true 表示已存在
     */
    public boolean existsByDeduplicationId(String deduplicationId) {
        if (deduplicationId == null || deduplicationId.isBlank()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM " + tableName
                + " WHERE deduplication_id = ? AND status IN (?, ?)";
        Long count = jdbcTemplate.queryForObject(sql, Long.class,
                deduplicationId,
                OutboxStatus.PENDING.name(),
                OutboxStatus.PROCESSING.name());
        return count != null && count > 0;
    }

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return Json.toJson(headers);
        } catch (JsonException e) {
            log.warn("Failed to serialize headers", e);
            return null;
        }
    }

    private Map<String, String> deserializeHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return Json.fromJson(json, MAP_TYPE);
        } catch (JsonException e) {
            log.warn("Failed to deserialize headers: {}", json, e);
            return Map.of();
        }
    }

    /**
     * Outbox 消息行映射器
     */
    private class OutboxRowMapper implements RowMapper<OutboxMessage> {
        @Override
        public OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp nextRetry = rs.getTimestamp("next_retry_at");
            Timestamp sentAt = rs.getTimestamp("sent_at");
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");

            var builder = OutboxMessage.builder()
                    .id(rs.getString("id"))
                    .aggregateId(rs.getString("aggregate_id"))
                    .aggregateType(rs.getString("aggregate_type"))
                    .eventType(rs.getString("event_type"))
                    .payload(rs.getString("payload"))
                    .headers(deserializeHeaders(rs.getString("headers")))
                    .status(OutboxStatus.valueOf(rs.getString("status")))
                    .retryCount(rs.getInt("retry_count"))
                    .maxRetries(rs.getInt("max_retries"))
                    .nextRetryAt(nextRetry != null ? nextRetry.toInstant() : null)
                    .createdAt(createdAt != null ? createdAt.toInstant() : null)
                    .updatedAt(updatedAt != null ? updatedAt.toInstant() : null)
                    .sentAt(sentAt != null ? sentAt.toInstant() : null)
                    .errorMessage(rs.getString("error_message"));

            // 兼容旧表（无新字段的表不会报错）
            try {
                builder.tenantId(rs.getString("tenant_id"));
            } catch (SQLException ignored) {
                // 列不存在时忽略
            }
            try {
                builder.deduplicationId(rs.getString("deduplication_id"));
            } catch (SQLException ignored) {
                // 列不存在时忽略
            }
            try {
                builder.schemaVersion(rs.getString("schema_version"));
            } catch (SQLException ignored) {
                // 列不存在时忽略
            }
            try {
                builder.contentType(rs.getString("content_type"));
            } catch (SQLException ignored) {
                // 列不存在时忽略
            }
            try {
                builder.priority(rs.getInt("priority"));
            } catch (SQLException ignored) {
                builder.priority(OutboxMessage.DEFAULT_PRIORITY);
            }
            try {
                builder.traceId(rs.getString("trace_id"));
            } catch (SQLException ignored) {
                // 列不存在时忽略
            }

            return builder.build();
        }
    }
}
