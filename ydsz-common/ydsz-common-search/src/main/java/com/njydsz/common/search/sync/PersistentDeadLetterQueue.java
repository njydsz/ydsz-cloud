package com.njydsz.common.search.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.core.IndexOperation;

/**
 * 持久化死信队列 — 将索引写入失败的操作落库到 PostgreSQL。
 *
 * <p>死信表结构（{@code ydsz_search_dead_letter}）：
 *
 * <pre>
 *   id            BIGSERIAL PRIMARY KEY
 *   operation     VARCHAR(20)   NOT NULL  -- UPSERT / DELETE / BULK
 *   doc_type      VARCHAR(64)             -- 实体类型
 *   document_id   VARCHAR(128)            -- 文档主键（DELETE 时使用）
 *   document_json TEXT                    -- 文档 JSON（UPSERT 时使用）
 *   error_msg     TEXT                    -- 最后一次失败原因
 *   retry_count   INT DEFAULT 0           -- 已重试次数
 *   status        VARCHAR(20) DEFAULT 'PENDING'  -- PENDING / RETRYING / RESOLVED / DISCARDED
 *   created_at    TIMESTAMPTZ DEFAULT NOW()
 *   resolved_at   TIMESTAMPTZ             -- 解决时间
 *   INDEX idx_dlq_status_created (status, created_at)
 * </pre>
 *
 * <p>对标行业：
 *
 * <ul>
 *   <li>Spring Retry + Dead Letter Queue 模式：失败消息落库 + 定时补偿
 *   <li>RabbitMQ DLX（Dead Letter Exchange）：死信路由到专用队列处理
 *   <li>Kafka 死信主题（DLQ Topic）：生产级别失败消息持久化 + 告警
 * </ul>
 *
 * <p>可靠性保证：
 *
 * <ul>
 *   <li>写入失败时降级到内存队列，不阻塞重试流程
 *   <li>定时扫描 status='PENDING' 的记录批量重放
 *   <li>重试次数超过阈值后标记为 DISCARDED，需人工介入
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class PersistentDeadLetterQueue {

  /** 死信表名 */
  private static final String DLQ_TABLE = "ydsz_search_dead_letter";

  /** 最大重试次数（落库后） */
  private static final int MAX_DB_RETRY = 5;

  private final JdbcTemplate jdbcTemplate;
  private final boolean dbAvailable;

  public PersistentDeadLetterQueue(Optional<DataSource> dataSource) {
    if (dataSource.isPresent()) {
      this.jdbcTemplate = new JdbcTemplate(dataSource.get());
      this.dbAvailable = checkTableExists();
    } else {
      this.jdbcTemplate = null;
      this.dbAvailable = false;
    }
  }

  /**
   * 将失败的索引操作入队（持久化到 DB 或内存降级）。
   *
   * @param operation 失败的索引操作
   * @param errorMsg 失败原因
   * @return 入队成功返回 true（DB 不可用时返回 false，需降级到内存）
   */
  public boolean enqueue(IndexOperation operation, String errorMsg) {
    if (!dbAvailable || operation == null) {
      return false;
    }
    try {
      String sql =
          """
                    INSERT INTO %s (operation, doc_type, document_id, document_json, error_msg, retry_count, status)
                    VALUES (?, ?, ?, ?, ?, 0, 'PENDING')
                    """
              .formatted(DLQ_TABLE);

      String docJson = null;
      if (operation.getDocument() != null) {
        docJson = YdszJson.toJson(operation.getDocument());
      } else if (operation.getDocuments() != null) {
        docJson = YdszJson.toJson(operation.getDocuments());
      }

      jdbcTemplate.update(
          sql,
          operation.getOperation().name(),
          operation.getType(),
          operation.getDocumentId(),
          docJson,
          errorMsg != null && errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);

      log.info(
          "[DLQ] 死信入队: type={}, id={}, op={}",
          operation.getType(),
          operation.getDocumentId(),
          operation.getOperation());
      return true;
    } catch (Exception e) {
      log.warn("[DLQ] 死信入队失败（降级到内存）: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 批量重放待处理的死信记录。
   *
   * <p>每次最多处理 {@code batchSize} 条，避免一次性拉取过多造成压力。 重试成功时更新状态为 RESOLVED，失败时递增 retry_count 或标记
   * DISCARDED。
   *
   * @param batchSize 批次大小
   * @param replayFn 重放函数（实际执行索引操作）
   * @return 成功处理的记录数
   */
  public int replayPending(int batchSize, java.util.function.Consumer<DlqRecord> replayFn) {
    if (!dbAvailable) {
      return 0;
    }
    try {
      String selectSql =
          """
                    SELECT id, operation, doc_type, document_id, document_json, retry_count
                    FROM %s
                    WHERE status = 'PENDING' AND retry_count < ?
                    ORDER BY created_at ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """
              .formatted(DLQ_TABLE);

      List<DlqRecord> records =
          jdbcTemplate.query(selectSql, new DlqRowMapper(), MAX_DB_RETRY, batchSize);

      int successCount = 0;
      for (DlqRecord record : records) {
        try {
          // 标记为 RETRYING
          jdbcTemplate.update(
              "UPDATE " + DLQ_TABLE + " SET status = 'RETRYING' WHERE id = ?", record.id);

          // 执行重放
          replayFn.accept(record);

          // 标记为 RESOLVED
          jdbcTemplate.update(
              "UPDATE " + DLQ_TABLE + " SET status = 'RESOLVED', resolved_at = NOW() WHERE id = ?",
              record.id);
          successCount++;
        } catch (Exception e) {
          // 递增重试次数或标记 DISCARDED
          if (record.retryCount + 1 >= MAX_DB_RETRY) {
            jdbcTemplate.update(
                "UPDATE " + DLQ_TABLE + " SET status = 'DISCARDED', error_msg = ? WHERE id = ?",
                "Max retries exceeded: " + e.getMessage(),
                record.id);
          } else {
            jdbcTemplate.update(
                "UPDATE "
                    + DLQ_TABLE
                    + " SET retry_count = retry_count + 1, status = 'PENDING', error_msg = ? WHERE id = ?",
                e.getMessage(),
                record.id);
          }
        }
      }

      if (successCount > 0) {
        log.info("[DLQ] 重放完成: success={}, total={}", successCount, records.size());
      }
      return successCount;
    } catch (Exception e) {
      log.error("[DLQ] 重放失败: {}", e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 获取待处理死信数量（用于监控告警）。
   *
   * @return 待处理记录数
   */
  public long getPendingCount() {
    if (!dbAvailable) {
      return 0;
    }
    try {
      Long count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(1) FROM " + DLQ_TABLE + " WHERE status IN ('PENDING', 'RETRYING')",
              Long.class);
      return count != null ? count : 0L;
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * 清理已解决的历史记录（保留最近 N 天）。
   *
   * @param retainDays 保留天数
   * @return 清理的记录数
   */
  public int cleanupResolved(int retainDays) {
    if (!dbAvailable) {
      return 0;
    }
    try {
      int deleted =
          jdbcTemplate.update(
              "DELETE FROM "
                  + DLQ_TABLE
                  + " WHERE status = 'RESOLVED' AND resolved_at < NOW() - INTERVAL '? DAY'",
              retainDays);
      if (deleted > 0) {
        log.info("[DLQ] 清理历史: {} records deleted (retain {} days)", deleted, retainDays);
      }
      return deleted;
    } catch (Exception e) {
      log.warn("[DLQ] 清理失败: {}", e.getMessage());
      return 0;
    }
  }

  // ==================== 私有方法 ====================

  private boolean checkTableExists() {
    try {
      jdbcTemplate.queryForObject("SELECT 1 FROM " + DLQ_TABLE + " LIMIT 1", Integer.class);
      return true;
    } catch (Exception e) {
      log.info("[DLQ] 死信表不存在，仅使用内存队列");
      return false;
    }
  }

  // ==================== 数据模型 ====================

  /**
   * 死信记录数据模型。
   *
   * @param id 记录 ID
   * @param operation 操作类型（UPSERT/DELETE/BULK）
   * @param docType 实体类型
   * @param documentId 文档 ID
   * @param documentJson 文档 JSON
   * @param retryCount 已重试次数
   */
  public record DlqRecord(
      long id,
      String operation,
      String docType,
      String documentId,
      String documentJson,
      int retryCount) {}

  /** 死信记录行映射器。 */
  private static class DlqRowMapper implements RowMapper<DlqRecord> {
    @Override
    public DlqRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new DlqRecord(
          rs.getLong("id"),
          rs.getString("operation"),
          rs.getString("doc_type"),
          rs.getString("document_id"),
          rs.getString("document_json"),
          rs.getInt("retry_count"));
    }
  }
}
