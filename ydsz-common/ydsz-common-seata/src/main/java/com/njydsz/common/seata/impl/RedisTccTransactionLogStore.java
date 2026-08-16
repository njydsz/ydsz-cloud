package com.njydsz.common.seata.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.seata.api.TccBranchStatus;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;

/**
 * 基于 Redis 的 TCC 事务日志存储
 *
 * <p>使用 Redis Hash 存储事务日志，适用于生产环境的分布式部署。 相比 {@link InMemoryTccTransactionLogStore}，本实现支持：
 *
 * <ul>
 *   <li><b>跨服务共享</b>：TCC 协调器可在多个服务实例间共享事务状态， 任一实例均可执行 Confirm/Cancel 恢复
 *   <li><b>持久化</b>：服务重启后事务状态不丢失，支持启动时恢复未完成事务
 *   <li><b>自动过期</b>：终态事务日志在 TTL 后自动清理，避免无限累积
 *   <li><b>SCAN 遍历</b>：使用 {@code SCAN} 命令遍历 key，避免 {@code KEYS} 阻塞 Redis
 * </ul>
 *
 * <p><b>存储结构</b>：
 *
 * <pre>
 *   Key:   {keyPrefix}:{xid}:{branchId}    (Redis Hash)
 *   Field: xid / branchId / transactionName / status / contextSnapshot /
 *          tryStartedAt / tryCompletedAt / finishedAt / retryCount / lastError
 *   TTL:   retention (默认 24 小时)
 * </pre>
 *
 * <p><b>线程安全</b>：{@link RedisTemplate} 自身线程安全，本实现无额外共享状态。
 *
 * <p><b>注册方式</b>：通过 {@link com.njydsz.common.seata.config.SeataAutoConfiguration} 在 {@code
 * RedisTemplate} 可用时自动注册，可通过 {@code ydsz.seata.tcc-log-store=redis} 显式启用，{@code =memory} 回退到内存版。
 *
 * <p><b>序列化兼容</b>：所有 Hash field/value 均以 String 写入，避免与不同 {@code RedisSerializer}（JDK / JSON /
 * String）的兼容性问题；{@link TccTransactionLog#getContextSnapshot()} 已是 JSON 字符串，原样存储；时间戳使用 {@link
 * DateTimeFormatter#ISO_LOCAL_DATE_TIME} 格式化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisTccTransactionLogStore implements TccTransactionLogStore {

  private static final Logger LOG = LoggerFactory.getLogger(RedisTccTransactionLogStore.class);

  private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private static final String FIELD_XID = "xid";
  private static final String FIELD_BRANCH_ID = "branchId";
  private static final String FIELD_TX_NAME = "transactionName";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_CTX_SNAPSHOT = "contextSnapshot";
  private static final String FIELD_TRY_STARTED_AT = "tryStartedAt";
  private static final String FIELD_TRY_COMPLETED_AT = "tryCompletedAt";
  private static final String FIELD_FINISHED_AT = "finishedAt";
  private static final String FIELD_RETRY_COUNT = "retryCount";
  private static final String FIELD_LAST_ERROR = "lastError";

  /** SCAN 单次返回的 key 数量上限，避免大 key 集合下阻塞 Redis */
  private static final long SCAN_BATCH_SIZE = 200L;

  private final RedisTemplate<String, Object> redisTemplate;
  private final String keyPrefix;
  private final Duration retention;

  /**
   * 构造 Redis 事务日志存储
   *
   * @param redisTemplate Redis 操作模板（不能为 null）
   * @param keyPrefix key 前缀，如 {@code "ydsz:tcc:log:"}
   * @param retention 日志保留时长，超过此时间的终态日志可清理（同时作为 Redis TTL）
   */
  public RedisTccTransactionLogStore(
      RedisTemplate<String, Object> redisTemplate, String keyPrefix, Duration retention) {
    this.redisTemplate = redisTemplate;
    this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "ydsz:tcc:log:" : keyPrefix;
    this.retention =
        (retention == null || retention.isZero() || retention.isNegative())
            ? Duration.ofHours(24)
            : retention;
  }

  /**
   * 保存事务日志到 Redis Hash
   *
   * @param txLog 事务日志
   */
  @Override
  public void save(TccTransactionLog txLog) {
    String key = buildKey(txLog.getXid(), txLog.getBranchId());
    Map<String, String> hash = toHash(txLog);
    redisTemplate.opsForHash().putAll(key, hash);
    redisTemplate.expire(key, retention.toSeconds(), TimeUnit.SECONDS);
    if (LOG.isDebugEnabled()) {
      LOG.debug("TCC log saved: key={}, status={}", key, txLog.getStatus());
    }
  }

  /**
   * 更新分支事务状态
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @param status 新状态
   */
  @Override
  public void updateStatus(String xid, String branchId, TccBranchStatus status) {
    String key = buildKey(xid, branchId);
    redisTemplate.opsForHash().put(key, FIELD_STATUS, status.name());
    if (status.isFinal()) {
      String now = LocalDateTime.now().format(TS_FMT);
      redisTemplate.opsForHash().put(key, FIELD_FINISHED_AT, now);
      // 终态日志保留至 retention，到点自动过期
      redisTemplate.expire(key, retention.toSeconds(), TimeUnit.SECONDS);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("TCC log status updated: key={}, status={}", key, status);
    }
  }

  /**
   * 查询超时未完成的分支事务数量（高效计数，不加载完整日志）
   *
   * <p>使用 SCAN 遍历 Redis，仅统计超时未完成的分支数， 避免加载完整事务日志到内存，降低健康检查的性能开销。
   *
   * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
   * @return 超时未完成的分支事务数量
   */
  @Override
  public long countTimeoutPending(LocalDateTime threshold) {
    long count = 0L;
    String pattern = keyPrefix + "*";
    ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
    Cursor<String> cursor = redisTemplate.scan(options);
    try {
      while (cursor.hasNext()) {
        String key = cursor.next();
        Object statusObj = redisTemplate.opsForHash().get(key, FIELD_STATUS);
        if (statusObj == null || !TccBranchStatus.TRIED.name().equals(statusObj.toString())) {
          continue;
        }
        Object tryCompletedObj = redisTemplate.opsForHash().get(key, FIELD_TRY_COMPLETED_AT);
        if (tryCompletedObj == null) {
          continue;
        }
        try {
          LocalDateTime tryCompletedAt = LocalDateTime.parse(tryCompletedObj.toString(), TS_FMT);
          if (tryCompletedAt.isBefore(threshold)) {
            count++;
          }
        } catch (Exception e) {
          LOG.debug("Failed to parse tryCompletedAt for key={}, skip", key);
        }
      }
    } finally {
      cursor.close();
    }
    return count;
  }

  /**
   * 分页查询超时未完成的分支事务（P1-2 新增）
   *
   * <p>使用 SCAN 遍历 Redis，仅返回前 limit 条超时未完成的分支记录， 避免一次性加载全部超时事务到内存。
   *
   * @param threshold 超时阈值
   * @param limit 单次返回最大记录数
   * @return 超时分支列表
   */
  @Override
  public List<TccTransactionLog> findTimeoutPendingPaged(LocalDateTime threshold, int limit) {
    List<TccTransactionLog> result = new ArrayList<>();
    String pattern = keyPrefix + "*";
    ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
    Cursor<String> cursor = redisTemplate.scan(options);
    try {
      while (cursor.hasNext() && result.size() < limit) {
        String key = cursor.next();
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
          continue;
        }
        TccTransactionLog logEntry = fromHash(raw);
        if (logEntry == null) {
          continue;
        }
        if (logEntry.getStatus() == TccBranchStatus.TRIED
            && logEntry.getTryCompletedAt() != null
            && logEntry.getTryCompletedAt().isBefore(threshold)) {
          result.add(logEntry);
        }
      }
    } finally {
      cursor.close();
    }
    return result;
  }

  /**
   * 根据 XID 和分支 ID 从 Redis 查询事务日志
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @return 事务日志（Optional）
   */
  @Override
  public Optional<TccTransactionLog> findByXidAndBranchId(String xid, String branchId) {
    String key = buildKey(xid, branchId);
    Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
    if (raw == null || raw.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(fromHash(raw));
  }

  /**
   * 使用 SCAN 命令遍历 Redis，查询超时未完成的分支事务
   *
   * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
   * @return 超时分支列表
   */
  @Override
  public List<TccTransactionLog> findTimeoutPending(LocalDateTime threshold) {
    List<TccTransactionLog> result = new ArrayList<>();
    String pattern = keyPrefix + "*";
    ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
    Cursor<String> cursor = redisTemplate.scan(options);
    try {
      while (cursor.hasNext()) {
        String key = cursor.next();
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
          continue;
        }
        TccTransactionLog logEntry = fromHash(raw);
        if (logEntry == null) {
          continue;
        }
        if (logEntry.getStatus() == TccBranchStatus.TRIED
            && logEntry.getTryCompletedAt() != null
            && logEntry.getTryCompletedAt().isBefore(threshold)) {
          result.add(logEntry);
        }
      }
    } finally {
      cursor.close();
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("TCC timeout pending scan: matched {} entries before {}", result.size(), threshold);
    }
    return result;
  }

  /**
   * 删除 Redis 中的事务日志
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   */
  @Override
  public void delete(String xid, String branchId) {
    String key = buildKey(xid, branchId);
    Boolean deleted = redisTemplate.delete(key);
    if (LOG.isDebugEnabled()) {
      LOG.debug("TCC log deleted: key={}, result={}", key, deleted);
    }
  }

  /**
   * 增加重试次数（用于恢复扫描时记录尝试次数）
   *
   * <p>注意：此方法不在 {@link TccTransactionLogStore} 接口中，仅供恢复扫描器内部使用。
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @return 当前累计重试次数
   */
  public int incrementRetryCount(String xid, String branchId) {
    String key = buildKey(xid, branchId);
    Long next = redisTemplate.opsForHash().increment(key, FIELD_RETRY_COUNT, 1L);
    return next == null ? 0 : next.intValue();
  }

  /**
   * 更新最近一次错误信息
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @param error 错误信息（null 时清空）
   */
  public void updateLastError(String xid, String branchId, String error) {
    String key = buildKey(xid, branchId);
    redisTemplate.opsForHash().put(key, FIELD_LAST_ERROR, error == null ? "" : error);
  }

  /**
   * 保存上下文快照（用于 Confirm/Cancel 阶段恢复）
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @param context TCC 上下文
   */
  public void saveContextSnapshot(String xid, String branchId, Map<String, Object> context) {
    String key = buildKey(xid, branchId);
    String json = YdszJson.toJson(context);
    redisTemplate.opsForHash().put(key, FIELD_CTX_SNAPSHOT, json);
  }

  // ============= 私有辅助方法 =============

  private String buildKey(String xid, String branchId) {
    return keyPrefix + xid + ":" + branchId;
  }

  private Map<String, String> toHash(TccTransactionLog txLog) {
    Map<String, String> hash = new HashMap<>(16);
    hash.put(FIELD_XID, nullSafe(txLog.getXid()));
    hash.put(FIELD_BRANCH_ID, nullSafe(txLog.getBranchId()));
    hash.put(FIELD_TX_NAME, nullSafe(txLog.getTransactionName()));
    hash.put(
        FIELD_STATUS,
        txLog.getStatus() == null ? TccBranchStatus.INIT.name() : txLog.getStatus().name());
    hash.put(FIELD_CTX_SNAPSHOT, nullSafe(txLog.getContextSnapshot()));
    hash.put(FIELD_TRY_STARTED_AT, formatTime(txLog.getTryStartedAt()));
    hash.put(FIELD_TRY_COMPLETED_AT, formatTime(txLog.getTryCompletedAt()));
    hash.put(FIELD_FINISHED_AT, formatTime(txLog.getFinishedAt()));
    hash.put(FIELD_RETRY_COUNT, String.valueOf(txLog.getRetryCount()));
    hash.put(FIELD_LAST_ERROR, nullSafe(txLog.getLastError()));
    return hash;
  }

  private TccTransactionLog fromHash(Map<Object, Object> raw) {
    String xid = str(raw.get(FIELD_XID));
    String branchId = str(raw.get(FIELD_BRANCH_ID));
    if (xid == null || branchId == null) {
      return null;
    }
    String txName = str(raw.get(FIELD_TX_NAME));
    TccTransactionLog logEntry = new TccTransactionLog(xid, branchId, txName);

    String statusName = str(raw.get(FIELD_STATUS));
    if (statusName != null) {
      try {
        logEntry.setStatus(TccBranchStatus.valueOf(statusName));
      } catch (IllegalArgumentException e) {
        LOG.warn("Unknown TCC branch status in Redis: {}, fallback to INIT", statusName);
      }
    }

    logEntry.setContextSnapshot(str(raw.get(FIELD_CTX_SNAPSHOT)));
    logEntry.setTryStartedAt(parseTime(str(raw.get(FIELD_TRY_STARTED_AT))));
    logEntry.setTryCompletedAt(parseTime(str(raw.get(FIELD_TRY_COMPLETED_AT))));
    logEntry.setFinishedAt(parseTime(str(raw.get(FIELD_FINISHED_AT))));

    String retryStr = str(raw.get(FIELD_RETRY_COUNT));
    if (retryStr != null) {
      try {
        int retryCount = Integer.parseInt(retryStr);
        for (int i = 0; i < retryCount; i++) {
          logEntry.incrementRetryCount();
        }
      } catch (NumberFormatException e) {
        LOG.warn("Invalid retryCount in Redis: {}", retryStr);
      }
    }

    logEntry.setLastError(str(raw.get(FIELD_LAST_ERROR)));
    return logEntry;
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }

  private static String str(Object o) {
    if (o == null) {
      return null;
    }
    String s = o.toString();
    return s.isEmpty() ? null : s;
  }

  private static String formatTime(LocalDateTime time) {
    return time == null ? "" : time.format(TS_FMT);
  }

  private static LocalDateTime parseTime(String s) {
    if (s == null || s.isEmpty()) {
      return null;
    }
    try {
      return LocalDateTime.parse(s, TS_FMT);
    } catch (Exception e) {
      return null;
    }
  }
}
