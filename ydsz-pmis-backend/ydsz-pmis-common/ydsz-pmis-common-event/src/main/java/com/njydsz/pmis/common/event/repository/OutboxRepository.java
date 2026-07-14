package com.njydsz.pmis.common.event.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;

/**
 * Outbox 消息 JDBC 仓储
 *
 * <p>使用 {@link JdbcTemplate} 直接操作数据库，不依赖 ORM 框架。
 * 所有多写操作在调用方的数据库事务中执行。
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
public class OutboxRepository {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;

    /**
     * @param jdbcTemplate  JDBC 模板
     * @param objectMapper   JSON 序列化器
     * @param tableName      Outbox 表名（默认 pmis_outbox）
     */
    public OutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
    }

    /**
     * 插入 Outbox 消息（在当前事务中执行）
     *
     * @param message 消息实体
     */
    public void save(OutboxMessage message) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName(tableName);

        Map<String, Object> params = Map.of(
                "id", message.getId(),
                "aggregate_id", message.getAggregateId(),
                "aggregate_type", message.getAggregateType(),
                "event_type", message.getEventType(),
                "payload", message.getPayload(),
                "headers", serializeHeaders(message.getHeaders()),
                "status", message.getStatus().name(),
                "retry_count", message.getRetryCount(),
                "max_retries", message.getMaxRetries(),
                "next_retry_at", Timestamp.from(message.getNextRetryAt()),
                "created_at", Timestamp.from(message.getCreatedAt()),
                "updated_at", Timestamp.from(message.getUpdatedAt())
        );
        insert.execute(params);
    }

    /**
     * 查询待投递的消息
     *
     * @param limit  最大条数
     * @return 待投递消息列表
     */
    public List<OutboxMessage> findPending(int limit) {
        String sql = "SELECT * FROM " + tableName
                + " WHERE status = ? AND (next_retry_at IS NULL OR next_retry_at <= ?)"
                + " ORDER BY created_at ASC LIMIT ?";
        return jdbcTemplate.query(sql, new OutboxRowMapper(),
                OutboxStatus.PENDING.name(), Timestamp.from(Instant.now()), limit);
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
     * @param id            消息 ID
     * @param errorMessage   错误信息
     * @param backoffSeconds 退避秒数
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
            Map<String, Long> result = new java.util.HashMap<>();
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

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, String> deserializeHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
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

            return OutboxMessage.builder()
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
                    .errorMessage(rs.getString("error_message"))
                    .build();
        }
    }
}
