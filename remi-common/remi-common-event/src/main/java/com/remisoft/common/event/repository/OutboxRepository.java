package com.remisoft.common.event.repository;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.remisoft.common.event.model.DatabaseDialect;
import com.remisoft.common.event.model.OutboxMessage;
import com.remisoft.common.event.model.OutboxStatus;
import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.exception.JsonException;
import com.remisoft.common.json.type.JsonType;

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
 * @author remi-team
 * @since 1.0.0
 */
public class OutboxRepository {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(OutboxRepository.class);

    /** headers 字段 JSON 反序列化类型 */
    private static final JsonType<Map<String, String>> MAP_TYPE = new JsonType<>() {};

    /** 表名合法字符校验正则（防 SQL 注入） */
    private static final String TABLE_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";

    /** JDBC 模板 */
    private final JdbcTemplate jdbcTemplate;

    /** Outbox 表名 */
    private final String tableName;

    /** 数据库方言 */
    private final DatabaseDialect dialect;

    /** 缓存 SimpleJdbcInsert 实例，避免每次 save 都查数据库元数据 */
    private final SimpleJdbcInsert jdbcInsert;

    /**
     * 构造函数
     *
     * @param jdbcTemplate JDBC 模板
     * @param tableName    Outbox 表名（默认 remi_outbox），需通过正则校验防 SQL 注入
     * @param dialect      数据库方言，用于适配不同数据库的 SQL 语法
     */
    public OutboxRepository(JdbcTemplate jdbcTemplate, String tableName, DatabaseDialect dialect) {
        if (tableName == null || !tableName.matches(TABLE_NAME_PATTERN)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.dialect = dialect != null ? dialect : DatabaseDialect.UNKNOWN;
        this.jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
    }

    /**
     * 插入 Outbox 消息（在当前事务中执行）
     *
     * @param message 消息实体
     */
    public void save(OutboxMessage message) {
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
        params.put("priority", message.getPriority() != null
                ? message.getPriority()
                : OutboxMessage.DEFAULT_PRIORITY);
        params.put("trace_id", message.getTraceId());
        jdbcInsert.execute(params);
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
        return jdbcTemplate.query(sql, OutboxRowMapper.INSTANCE,
                OutboxStatus.PENDING.name(), Timestamp.from(Instant.now()), limit);
    }

    /**
     * 批量原子 claim 消息（单条 SQL，避免 N+1 查询）
     *
     * <p>将指定 ID 列表中状态为 PENDING 的消息一次性改为 PROCESSING。
     * 返回成功 claim 的消息数量。
     *
     * @param ids 消息 ID 列表
     * @return 成功 claim 的数量
     */
    public int claimBatchForProcessing(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        String sql = "UPDATE " + tableName
                + " SET status = ?, updated_at = ?"
                + " WHERE id IN (" + placeholders + ") AND status = ?";

        Object[] params = new Object[ids.size() + 3];
        params[0] = OutboxStatus.PROCESSING.name();
        params[1] = Timestamp.from(Instant.now());
        for (int i = 0; i < ids.size(); i++) {
            params[i + 2] = ids.get(i);
        }
        params[ids.size() + 2] = OutboxStatus.PENDING.name();

        return jdbcTemplate.update(sql, params);
    }

    /**
     * 原子 claim 消息：将指定消息状态从 PENDING 改为 PROCESSING
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

    /**
     * 获取 Outbox 表名
     *
     * @return 表名
     */
    String getTableName() {
        return tableName;
    }

    /**
     * 序列化扩展头为 JSON 字符串
     *
     * @param headers 扩展头映射
     * @return JSON 字符串，序列化失败返回 null
     */
    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return RemiJson.toJson(headers);
        } catch (JsonException e) {
            log.warn("Failed to serialize headers", e);
            return null;
        }
    }

    /**
     * 反序列化 JSON 字符串为扩展头映射
     *
     * @param json JSON 字符串
     * @return 扩展头映射，反序列化失败返回空 Map
     */
    private static Map<String, String> deserializeHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return RemiJson.fromJson(json, MAP_TYPE);
        } catch (JsonException e) {
            log.warn("Failed to deserialize headers: {}", json, e);
            return Map.of();
        }
    }

    /**
     * Outbox 消息行映射器
     *
     * <p>静态内部类，复用单一实例，使用 ResultSetMetaData 一次性检查列是否存在，
     * 避免逐列 try-catch SQLException 的性能开销。
     */
    static final class OutboxRowMapper implements RowMapper<OutboxMessage> {

        /** 单例实例 */
        static final OutboxRowMapper INSTANCE = new OutboxRowMapper();

        /**
         * 将结果集行映射为 OutboxMessage 实体
         *
         * @param rs     结果集
         * @param rowNum 行号（从 0 开始）
         * @return OutboxMessage 实例
         * @throws SQLException 读取列数据失败
         */
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

            // 使用 ResultSetMetaData 一次性检查列是否存在，避免 try-catch SQLException 开销
            Set<String> columns = getColumnNames(rs);
            if (columns.contains("tenant_id")) {
                builder.tenantId(rs.getString("tenant_id"));
            }
            if (columns.contains("deduplication_id")) {
                builder.deduplicationId(rs.getString("deduplication_id"));
            }
            if (columns.contains("schema_version")) {
                builder.schemaVersion(rs.getString("schema_version"));
            }
            if (columns.contains("content_type")) {
                builder.contentType(rs.getString("content_type"));
            }
            if (columns.contains("priority")) {
                builder.priority(rs.getObject("priority", Integer.class));
            }
            if (columns.contains("trace_id")) {
                builder.traceId(rs.getString("trace_id"));
            }

            return builder.build();
        }

        /**
         * 获取 ResultSet 中所有列名（小写）
         *
         * @param rs 结果集
         * @return 列名集合
         * @throws SQLException 获取元数据失败
         */
        private Set<String> getColumnNames(ResultSet rs) throws SQLException {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            Set<String> names = new HashSet<>(count);
            for (int i = 1; i <= count; i++) {
                names.add(meta.getColumnLabel(i).toLowerCase());
            }
            return names;
        }
    }
}
