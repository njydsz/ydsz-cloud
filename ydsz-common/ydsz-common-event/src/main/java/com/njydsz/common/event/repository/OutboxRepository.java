package com.njydsz.common.event.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;

/**
 * Outbox 消息 JDBC 仓储
 *
 * <p>使用 {@link JdbcTemplate} 直接操作数据库，不依赖 ORM 框架。 所有多写操作在调用方的数据库事务中执行。
 *
 * <p>支持多实例部署的原子 claim 机制：通过 {@code UPDATE ... WHERE status = 'PENDING'} 原子地将消息状态从 PENDING 改为
 * PROCESSING，确保同一消息只被一个实例处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 精简字段：移除 headers/schemaVersion/contentType/priority 四个未验证字段的读写
 * @since 26.09.19 E-2 字段对齐：Java 字段 idempotencyKey ↔ DDL 列名 idempotency_key
 * @since 26.09.19 E-1 error_message 智能截断：保留首部 800 字符 + 尾部 1200 字符，确保根因不丢
 * @since 26.09.19 O-4 增加 schema_version 列读写，默认值 1
 * @since 26.09.19 P3 增加 compressed 列读写，标记 payload 是否 GZIP 压缩
 */
public class OutboxRepository {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxRepository.class);

  /** 表名合法字符校验正则（防 SQL 注入） */
  private static final String TABLE_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_]*$";

  /** error_message 截断保留的前缀字符数 */
  private static final int ERROR_HEAD_LENGTH = 800;

  /** error_message 截断保留的后缀字符数 */
  private static final int ERROR_TAIL_LENGTH = 1200;

  /** 截断占位符 */
  private static final String TRUNCATE_MARKER = "\n...[truncated]...\n";

  /** payload 超过此字节数自动启用 GZIP 压缩 */
  private static final int COMPRESS_THRESHOLD_BYTES = 4096;

  /** JDBC 模板 */
  private final JdbcTemplate jdbcTemplate;

  /** Outbox 表名 */
  private final String tableName;

  /** 缓存 SimpleJdbcInsert 实例，避免每次 save 都查数据库元数据 */
  private final SimpleJdbcInsert jdbcInsert;

  /**
   * 构造函数
   *
   * @param jdbcTemplate JDBC 模板
   * @param tableName Outbox 表名（默认 ydsz_com_outbox），需通过正则校验防 SQL 注入
   */
  public OutboxRepository(JdbcTemplate jdbcTemplate, String tableName) {
    if (tableName == null || !tableName.matches(TABLE_NAME_PATTERN)) {
      throw new IllegalArgumentException("Invalid table name: " + tableName);
    }
    this.jdbcTemplate = jdbcTemplate;
    this.tableName = tableName;
    this.jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
  }

  /**
   * 插入 Outbox 消息（在当前事务中执行）
   *
   * <p>当 payload 超过 {@value #COMPRESS_THRESHOLD_BYTES} 字节时自动启用 GZIP 压缩存储， 读取时通过 {@link
   * OutboxRowMapper} 自动解压还原。
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
    params.put("status", message.getStatus().name());
    params.put("retry_count", message.getRetryCount());
    params.put("max_retries", message.getMaxRetries());
    params.put("next_retry_at", Timestamp.from(message.getNextRetryAt()));
    params.put("created_at", Timestamp.from(message.getCreatedAt()));
    params.put("updated_at", Timestamp.from(message.getUpdatedAt()));
    params.put("tenant_id", message.getTenantId());
    params.put("idempotency_key", message.getIdempotencyKey());
    params.put("trace_id", message.getTraceId());
    params.put("schema_version", message.getSchemaVersion());
    params.put("compressed", message.isCompressed());
    jdbcInsert.execute(params);
  }

  /**
   * 批量插入 Outbox 消息（在当前事务中执行）
   *
   * <p>使用 {@link JdbcTemplate#batchUpdate} 实现真正的批量插入， 相比逐条 {@link #save} 可显著减少数据库往返次数。
   * 适用于批量事件写入场景（如聚合根批量操作、事件溯源回放等）。
   *
   * @param messages 消息列表（不能为空）
   */
  public void saveBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    String sql =
        "INSERT INTO "
            + tableName
            + " (id, aggregate_id, aggregate_type, event_type, payload,"
            + " status, retry_count, max_retries, next_retry_at, created_at, updated_at,"
            + " tenant_id, idempotency_key, trace_id, schema_version, compressed)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    jdbcTemplate.batchUpdate(
        sql,
        new BatchPreparedStatementSetter() {
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
            ps.setTimestamp(
                9, msg.getNextRetryAt() != null ? Timestamp.from(msg.getNextRetryAt()) : null);
            ps.setTimestamp(
                10, msg.getCreatedAt() != null ? Timestamp.from(msg.getCreatedAt()) : null);
            ps.setTimestamp(
                11, msg.getUpdatedAt() != null ? Timestamp.from(msg.getUpdatedAt()) : null);
            ps.setString(12, msg.getTenantId());
            ps.setString(13, msg.getIdempotencyKey());
            ps.setString(14, msg.getTraceId());
            ps.setInt(15, msg.getSchemaVersion());
            ps.setBoolean(16, msg.isCompressed());
          }

          @Override
          public int getBatchSize() {
            return messages.size();
          }
        });
  }

  /**
   * 查询待投递的消息（按创建时间升序）
   *
   * <p>查询条件：状态为 PENDING 且（next_retry_at 为空或已到重试时间）。 RowMapper 自动处理 compressed 字段的解压还原。
   *
   * @param limit 最大条数
   * @return 待投递消息列表
   */
  public List<OutboxMessage> findPending(int limit) {
    String sql =
        "SELECT * FROM "
            + tableName
            + " WHERE status = ? AND (next_retry_at IS NULL OR next_retry_at <= ?)"
            + " ORDER BY created_at ASC"
            + " LIMIT ?";
    return jdbcTemplate.query(
        sql,
        OutboxRowMapper.INSTANCE,
        OutboxStatus.PENDING.name(),
        Timestamp.from(Instant.now()),
        limit);
  }

  /**
   * 原子 claim 消息：将指定消息状态从 PENDING 改为 PROCESSING
   *
   * <p>逐条 CAS 策略（P-2）：相比批量 claim 在大批量高并发场景下冲突率更低。 每条消息独立竞争，失败的 ID 直接跳过（已被其他实例 claim），无需重试。
   *
   * @param id 消息 ID
   * @return true 表示 claim 成功，false 表示消息已被其他实例 claim 或不存在
   */
  public boolean claimForProcessing(String id) {
    String sql =
        "UPDATE " + tableName + " SET status = ?, updated_at = ?" + " WHERE id = ? AND status = ?";
    int affected =
        jdbcTemplate.update(
            sql,
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
    String sql =
        "UPDATE "
            + tableName
            + " SET status = ?, updated_at = ?"
            + " WHERE status = ? AND updated_at < ?";
    int affected =
        jdbcTemplate.update(
            sql,
            OutboxStatus.PENDING.name(),
            Timestamp.from(Instant.now()),
            OutboxStatus.PROCESSING.name(),
            Timestamp.from(cutoff));
    if (affected > 0) {
      LOG.warn(
          "Reclaimed {} stale PROCESSING messages older than {} minutes",
          affected,
          thresholdMinutes);
    }
    return affected;
  }

  /**
   * 更新消息状态为已投递
   *
   * @param id 消息 ID
   */
  public void markAsSent(String id) {
    String sql =
        "UPDATE "
            + tableName
            + " SET status = ?, sent_at = ?, updated_at = ?, error_message = NULL"
            + " WHERE id = ?";
    jdbcTemplate.update(
        sql,
        OutboxStatus.SENT.name(),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        id);
  }

  /**
   * 更新消息为失败，增加重试计数
   *
   * <p>error_message 智能截断策略（E-1）：当异常消息超过 {@value #ERROR_HEAD_LENGTH} + {@value
   * #ERROR_TAIL_LENGTH} 字符时， 保留首部（异常类型 + 消息）和尾部（根因 Caused by），中间以 {@value #TRUNCATE_MARKER}
   * 替代， 确保核心诊断信息（异常类型、根因链）不丢失。
   *
   * @param id 消息 ID
   * @param errorMessage 错误信息（将被智能截断以控制在 VARCHAR(2000) 范围内）
   * @param backoffSeconds 退避秒数
   */
  public void markAsFailed(String id, String errorMessage, long backoffSeconds) {
    String truncatedMsg = truncateErrorMessage(errorMessage);
    String sql =
        "UPDATE "
            + tableName
            + " SET retry_count = retry_count + 1, error_message = ?,"
            + " next_retry_at = ?, updated_at = ?,"
            + " status = CASE WHEN retry_count + 1 >= max_retries THEN ? ELSE ? END"
            + " WHERE id = ?";
    jdbcTemplate.update(
        sql,
        truncatedMsg,
        Timestamp.from(Instant.now().plusSeconds(backoffSeconds)),
        Timestamp.from(Instant.now()),
        OutboxStatus.DEAD_LETTER.name(),
        OutboxStatus.PENDING.name(),
        id);
  }

  /**
   * 智能截断错误信息
   *
   * <p>策略：总长度不超过 {@value #ERROR_HEAD_LENGTH} + {@value #ERROR_TAIL_LENGTH} + {@value
   * #TRUNCATE_MARKER} 字符。 当原始消息超长时保留首部（异常类和消息）和尾部（根因链），便于排查定位。
   *
   * @param errorMessage 原始错误信息，可为 null
   * @return 截断后的错误信息（长度可控）
   */
  public static String truncateErrorMessage(String errorMessage) {
    if (errorMessage == null) {
      return null;
    }
    int maxLength = ERROR_HEAD_LENGTH + ERROR_TAIL_LENGTH + TRUNCATE_MARKER.length();
    if (errorMessage.length() <= maxLength) {
      return errorMessage;
    }
    return errorMessage.substring(0, ERROR_HEAD_LENGTH)
        + TRUNCATE_MARKER
        + errorMessage.substring(errorMessage.length() - ERROR_TAIL_LENGTH);
  }

  /**
   * 统计各状态消息数
   *
   * <p>默认开启时间窗口缓存（缓存时间由 {@code statusCountCacheSeconds} 配置）， 减少全表 COUNT 查询对数据库的压力。当 {@code
   * useCache=false} 时 直接查询数据库获取精确值。
   *
   * @param useCache 是否使用缓存
   * @return 状态 → 数量
   */
  public Map<String, Long> countByStatus(boolean useCache) {
    if (useCache) {
      return countByStatusCached();
    }
    return countByStatusFromDb();
  }

  /**
   * 统计各状态消息数（始终查询数据库）
   *
   * @return 状态 → 数量
   */
  public Map<String, Long> countByStatus() {
    return countByStatusFromDb();
  }

  /**
   * 从数据库查询各状态消息数
   *
   * @return 状态 → 数量
   */
  private Map<String, Long> countByStatusFromDb() {
    String sql = "SELECT status, COUNT(*) as cnt FROM " + tableName + " GROUP BY status";
    return jdbcTemplate.query(
        sql,
        rs -> {
          Map<String, Long> result = new HashMap<>(16);
          while (rs.next()) {
            result.put(rs.getString("status"), rs.getLong("cnt"));
          }
          return result;
        });
  }

  /** 缓存的计数结果 */
  private volatile Map<String, Long> cachedStatusCounts = null;

  /** 缓存过期时间（毫秒） */
  private volatile long cacheExpireAt = 0L;

  /**
   * 从缓存获取各状态消息数（时间窗口缓存，过期后自动回源）
   *
   * @return 状态 → 数量（可能为空 Map）
   */
  private Map<String, Long> countByStatusCached() {
    long now = System.currentTimeMillis();
    if (cachedStatusCounts != null && now < cacheExpireAt) {
      return cachedStatusCounts;
    }
    // 缓存过期，回源查询
    Map<String, Long> fresh = countByStatusFromDb();
    cachedStatusCounts = fresh;
    cacheExpireAt = now + cacheTtlMillis;
    return fresh;
  }

  /** 缓存 TTL（毫秒），由 EventProperties 初始化时设置 */
  private long cacheTtlMillis = 5000L;

  /**
   * 设置缓存 TTL
   *
   * @param ttlMillis 缓存毫秒数
   */
  public void setCacheTtlMillis(long ttlMillis) {
    this.cacheTtlMillis = Math.max(ttlMillis, 1000L);
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
   * 根据 idempotencyKey 查询是否已存在
   *
   * <p>仅检查 PENDING 和 PROCESSING 状态的消息（SENT/DEAD_LETTER 已投递完成或放弃）。
   *
   * @param idempotencyKey 幂等去重键
   * @return true 表示已存在
   */
  public boolean existsByIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return false;
    }
    String sql =
        "SELECT COUNT(*) FROM " + tableName + " WHERE idempotency_key = ? AND status IN (?, ?)";
    Long count =
        jdbcTemplate.queryForObject(
            sql,
            Long.class,
            idempotencyKey,
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
   * 判断 compress 功能是否启用（基于 payload 大小自动检测）
   *
   * @param payload 消息负载
   * @return true 表示建议启用压缩
   */
  static boolean shouldCompress(String payload) {
    return payload != null && payload.getBytes().length > COMPRESS_THRESHOLD_BYTES;
  }

  // ==================== 运维管理 API ====================

  /**
   * 分页查询指定状态的消息
   *
   * <p>支持按事件类型过滤，按创建时间倒序排列。
   *
   * @param status 消息状态
   * @param pageable 分页参数
   * @param eventTypeFilter 事件类型过滤（可为 null）
   * @return 分页消息列表
   */
  public Page<OutboxMessage> findByStatus(
      OutboxStatus status, Pageable pageable, String eventTypeFilter) {
    StringBuilder sql =
        new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE status = ?");
    List<Object> params = new ArrayList<>(16);
    params.add(status.name());

    if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
      sql.append(" AND event_type = ?");
      params.add(eventTypeFilter);
    }
    sql.append(" ORDER BY created_at DESC");
    sql.append(" LIMIT ?");
    params.add(pageable.getPageSize());
    sql.append(" OFFSET ?");
    params.add(pageable.getOffset());

    List<OutboxMessage> messages =
        jdbcTemplate.query(sql.toString(), OutboxRowMapper.INSTANCE, params.toArray());

    // COUNT 查询
    StringBuilder countSql =
        new StringBuilder("SELECT COUNT(*) FROM ").append(tableName).append(" WHERE status = ?");
    List<Object> countParams = new ArrayList<>(16);
    countParams.add(status.name());
    if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
      countSql.append(" AND event_type = ?");
      countParams.add(eventTypeFilter);
    }
    Long total =
        jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

    return new PageImpl<>(messages, pageable, total != null ? total : 0L);
  }

  /**
   * CAS 重置消息为 PENDING（仅当当前状态为指定 fromStatus 时）
   *
   * @param id 消息 ID
   * @param fromStatus 原始状态（CAS 条件）
   * @return 成功更新的行数
   */
  public int resetToPending(String id, OutboxStatus fromStatus) {
    String sql =
        "UPDATE "
            + tableName
            + " SET status = ?, retry_count = 0, next_retry_at = ?, updated_at = ?, error_message = NULL"
            + " WHERE id = ? AND status = ?";
    return jdbcTemplate.update(
        sql,
        OutboxStatus.PENDING.name(),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        id,
        fromStatus.name());
  }

  /**
   * 批量重置指定状态的消息为 PENDING
   *
   * @param fromStatus 原始状态
   * @param eventTypeFilter 事件类型过滤（可为 null）
   * @return 成功更新的行数
   */
  public int resetAllToPending(OutboxStatus fromStatus, String eventTypeFilter) {
    StringBuilder sql =
        new StringBuilder("UPDATE ")
            .append(tableName)
            .append(
                " SET status = ?, retry_count = 0, next_retry_at = ?, updated_at = ?, error_message = NULL")
            .append(" WHERE status = ?");
    List<Object> params = new ArrayList<>(16);
    params.add(OutboxStatus.PENDING.name());
    params.add(Timestamp.from(Instant.now()));
    params.add(Timestamp.from(Instant.now()));
    params.add(fromStatus.name());

    if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
      sql.append(" AND event_type = ?");
      params.add(eventTypeFilter);
    }
    return jdbcTemplate.update(sql.toString(), params.toArray());
  }

  /**
   * 仅当消息处于终态时删除
   *
   * @param id 消息 ID
   * @param terminalStatuses 允许删除的终态状态列表
   * @return 成功删除的行数
   */
  public int deleteIfTerminal(String id, Collection<OutboxStatus> terminalStatuses) {
    if (terminalStatuses == null || terminalStatuses.isEmpty()) {
      return 0;
    }
    StringBuilder sql =
        new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE id = ? AND status IN (");
    List<Object> params = new ArrayList<>(16);
    params.add(id);
    int i = 0;
    for (OutboxStatus s : terminalStatuses) {
      if (i > 0) {
        sql.append(",");
      }
      sql.append("?");
      params.add(s.name());
      i++;
    }
    sql.append(")");
    return jdbcTemplate.update(sql.toString(), params.toArray());
  }

  /**
   * Outbox 消息行映射器
   *
   * <p>静态内部类，复用单一实例。自动读取 compressed 字段，压缩的 payload 在读取时需由调用方解压 （当前为透明映射，consumer 根据 isCompressed 判断）。
   */
  static final class OutboxRowMapper implements RowMapper<OutboxMessage> {

    /** 单例实例 */
    static final OutboxRowMapper INSTANCE = new OutboxRowMapper();

    /**
     * 将结果集行映射为 OutboxMessage 实体
     *
     * @param rs 结果集
     * @param rowNum 行号（从 0 开始）
     * @return OutboxMessage 实例
     * @throws java.sql.SQLException 读取列数据失败
     */
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
          .status(OutboxStatus.valueOf(rs.getString("status")))
          .retryCount(rs.getLong("retry_count"))
          .maxRetries(rs.getLong("max_retries"))
          .nextRetryAt(nextRetry != null ? nextRetry.toInstant() : null)
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
