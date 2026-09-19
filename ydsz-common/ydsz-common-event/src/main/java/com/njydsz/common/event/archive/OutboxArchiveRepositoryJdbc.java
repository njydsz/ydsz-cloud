package com.njydsz.common.event.archive;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 归档仓储 JDBC 实现（F-4）
 *
 * <p>使用独立的归档表 {@code ydsz_com_outbox_archive} 存储已处理完成的消息。 通过配置 {@link
 * com.njydsz.common.event.config.EventProperties.Archive} 启用归档功能。 *
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>需要追溯历史事件（如审计、问题排查）但不想让 Outbox 主表无限增长
 *   <li>流式消费场景：归档表可作为事件回溯的数据源
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class OutboxArchiveRepositoryJdbc implements OutboxArchiveRepository {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxArchiveRepositoryJdbc.class);

  /** 归档表名合法字符校验正则 */
  private static final String TABLE_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";

  /** JDBC 模板 */
  private final JdbcTemplate jdbcTemplate;

  /** 归档表名 */
  private final String archiveTableName;

  /**
   * 构造归档仓储
   *
   * @param jdbcTemplate JDBC 模板
   * @param archiveTableName 归档表名（默认 ydsz_com_outbox_archive）
   */
  public OutboxArchiveRepositoryJdbc(JdbcTemplate jdbcTemplate, String archiveTableName) {
    if (archiveTableName == null || !archiveTableName.matches(TABLE_NAME_PATTERN)) {
      throw new IllegalArgumentException("Invalid archive table name: " + archiveTableName);
    }
    this.jdbcTemplate = jdbcTemplate;
    this.archiveTableName = archiveTableName;
  }

  @Override
  public void archive(OutboxMessage message) {
    if (message == null) {
      return;
    }
    String sql =
        "INSERT INTO "
            + archiveTableName
            + " (id, aggregate_id, aggregate_type, event_type, payload,"
            + " status, retry_count, max_retries, tenant_id, idempotency_key,"
            + " trace_id, schema_version, compressed,"
            + " created_at, updated_at, sent_at, archived_at, error_message)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    jdbcTemplate.update(
        sql,
        message.getId(),
        message.getAggregateId(),
        message.getAggregateType(),
        message.getEventType(),
        message.getPayload(),
        message.getStatus().name(),
        message.getRetryCount(),
        message.getMaxRetries(),
        message.getTenantId(),
        message.getIdempotencyKey(),
        message.getTraceId(),
        message.getSchemaVersion(),
        message.isCompressed(),
        Timestamp.from(message.getCreatedAt()),
        Timestamp.from(message.getUpdatedAt()),
        message.getSentAt() != null ? Timestamp.from(message.getSentAt()) : null,
        Timestamp.from(Instant.now()),
        message.getErrorMessage());
  }

  @Override
  public void archiveBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    String sql =
        "INSERT INTO "
            + archiveTableName
            + " (id, aggregate_id, aggregate_type, event_type, payload,"
            + " status, retry_count, max_retries, tenant_id, idempotency_key,"
            + " trace_id, schema_version, compressed,"
            + " created_at, updated_at, sent_at, archived_at, error_message)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        OutboxMessage msg = messages.get(i);
        ps.setString(1, msg.getId());
        ps.setString(2, msg.getAggregateId());
        ps.setString(3, msg.getAggregateType());
        ps.setString(4, msg.getEventType());
        ps.setString(5, msg.getPayload());
        ps.setString(6, msg.getStatus().name());
        ps.setLong(7, msg.getRetryCount());
        ps.setLong(8, msg.getMaxRetries());
        ps.setString(9, msg.getTenantId());
        ps.setString(10, msg.getIdempotencyKey());
        ps.setString(11, msg.getTraceId());
        ps.setInt(12, msg.getSchemaVersion());
        ps.setBoolean(13, msg.isCompressed());
        ps.setTimestamp(
            14, msg.getCreatedAt() != null ? Timestamp.from(msg.getCreatedAt()) : null);
        ps.setTimestamp(
            15, msg.getUpdatedAt() != null ? Timestamp.from(msg.getUpdatedAt()) : null);
        ps.setTimestamp(
            16, msg.getSentAt() != null ? Timestamp.from(msg.getSentAt()) : null);
        ps.setTimestamp(17, Timestamp.from(Instant.now()));
        ps.setString(18, msg.getErrorMessage());
      }

      @Override
      public int getBatchSize() {
        return messages.size();
      }
    });
  }

  @Override
  public Optional<OutboxMessage> findById(String messageId) {
    String sql = "SELECT * FROM " + archiveTableName + " WHERE id = ?";
    try {
      OutboxMessage message = jdbcTemplate.queryForObject(sql, ArchiveRowMapper.INSTANCE, messageId);
      return Optional.ofNullable(message);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  @Override
  public List<OutboxMessage> findByAggregateId(String aggregateId) {
    String sql = "SELECT * FROM " + archiveTableName + " WHERE aggregate_id = ? ORDER BY created_at DESC";
    return jdbcTemplate.query(sql, ArchiveRowMapper.INSTANCE, aggregateId);
  }

  @Override
  public Page<OutboxMessage> findArchives(String eventType, Instant startTime, Instant endTime,
      Pageable pageable) {
    StringBuilder sql = new StringBuilder("SELECT * FROM " + archiveTableName + " WHERE 1=1");
    StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM " + archiveTableName + " WHERE 1=1");
    List<Object> params = new ArrayList<>(16);
    List<Object> countParams = new ArrayList<>(16);

    if (eventType != null && !eventType.isBlank()) {
      sql.append(" AND event_type = ?");
      countSql.append(" AND event_type = ?");
      params.add(eventType);
      countParams.add(eventType);
    }
    if (startTime != null) {
      sql.append(" AND created_at >= ?");
      countSql.append(" AND created_at >= ?");
      params.add(Timestamp.from(startTime));
      countParams.add(Timestamp.from(startTime));
    }
    if (endTime != null) {
      sql.append(" AND created_at <= ?");
      countSql.append(" AND created_at <= ?");
      params.add(Timestamp.from(endTime));
      countParams.add(Timestamp.from(endTime));
    }
    sql.append(" ORDER BY created_at DESC");
    sql.append(" LIMIT ?");
    params.add(pageable.getPageSize());
    sql.append(" OFFSET ?");
    params.add(pageable.getOffset());

    List<OutboxMessage> messages = jdbcTemplate.query(sql.toString(), ArchiveRowMapper.INSTANCE,
        params.toArray());
    Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
    return new PageImpl<>(messages, pageable, total != null ? total : 0L);
  }

  @Override
  public int deleteArchivedBefore(Instant beforeTime) {
    String sql = "DELETE FROM " + archiveTableName + " WHERE archived_at < ?";
    return jdbcTemplate.update(sql, Timestamp.from(beforeTime));
  }

  @Override
  public boolean isArchiveTableAvailable() {
    try {
      jdbcTemplate.queryForObject("SELECT 1 FROM " + archiveTableName + " WHERE 1=0",
          Integer.class);
      return true;
    } catch (Exception e) {
      LOG.debug("Archive table {} not accessible: {}", archiveTableName, e.getMessage());
      return false;
    }
  }

  /**
   * 归档表行映射器
   */
  static final class ArchiveRowMapper implements RowMapper<OutboxMessage> {
    static final ArchiveRowMapper INSTANCE = new ArchiveRowMapper();

    @Override
    public OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
      Timestamp sentAt = rs.getTimestamp("sent_at");
      Timestamp createdAt = rs.getTimestamp("created_at");
      Timestamp updatedAt = rs.getTimestamp("updated_at");

      return OutboxMessage.builder()
          .id(rs.getString("id"))
          .aggregateId(rs.getString("aggregate_id"))
          .aggregateType(rs.getString("aggregate_type"))
          .eventType(rs.getString("event_type"))
          .payload(rs.getString("payload"))
          .status(com.njydsz.common.event.model.OutboxStatus.valueOf(rs.getString("status")))
          .retryCount(rs.getLong("retry_count"))
          .maxRetries(rs.getLong("max_retries"))
          .nextRetryAt(null)
          .createdAt(createdAt != null ? createdAt.toInstant() : null)
          .updatedAt(updatedAt != null ? updatedAt.toInstant() : null)
          .sentAt(sentAt != null ? sentAt.toInstant() : null)
          .errorMessage(rs.getString("error_message"))
          .tenantId(rs.getString("tenant_id"))
          .idempotencyKey(rs.getString("idempotency_key"))
          .schemaVersion(rs.getInt("schema_version"))
          .isCompressed(rs.getBoolean("compressed"))
          .traceId(rs.getString("trace_id"))
          .build();
    }
  }
}
