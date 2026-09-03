package com.njydsz.nextwiki.server.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;

/**
 * NextWiki Micrometer 指标采集。
 *
 * <p>继承 {@link SentryMetricsAdapter}，统一指标前缀 {@code ydsz_nextwiki_}。
 *
 * <p>暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 *
 * <ul>
 *   <li>Counter — 操作次数：{@code file_upload_total} / {@code file_download_total} / {@code
 *       file_delete_total} / {@code share_create_total} / {@code search_total} / {@code
 *       preview_generate_total}
 *   <li>Timer — 操作耗时：{@code file_upload_duration} / {@code file_download_duration} / {@code
 *       operation_duration}（通用，带 operation Tag 区分）
 * </ul>
 *
 * <p><b>设计优化（S2-P1-3）：</b>
 *
 * <ul>
 *   <li>所有 Counter/Timer 通过 {@link SentryMetricsAdapter} 统一管理，符合《云顶编码规范》第 27.2.1 节</li>
 * </ul>
 *
 * <p><b>TODO：</b>DistributionSummary（file_upload_size_bytes / file_download_size_bytes）暂未迁移，
 * 因 {@link SentryMetricsAdapter} 暂不支持 DistributionSummary。待适配器扩展后恢复。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class NextwikiMetrics extends SentryMetricsAdapter {

  private final StorageQuotaRepository quotaRepository;

  /** 配额用量缓存（用于 Gauge） */
  private final AtomicLong quotaUsageCached = new AtomicLong(0);

  public NextwikiMetrics(StorageQuotaRepository quotaRepository) {
    super("ydsz_nextwiki_");
    this.quotaRepository = quotaRepository;
    // 注册配额用量 Gauge（动态引用模式）
    gaugeRef("quota_usage_bytes", quotaUsageCached, AtomicLong::get,
        "scopeType", "user", "scopeId", "global");
    log.info("[NextwikiMetrics] 初始化完成，指标前缀 ydsz_nextwiki_");
  }

  // ==================== Counter：操作次数 ====================

  /**
   * 记录一次文件上传成功，累加 {@code ydsz_nextwiki_file_upload_total}。
   *
   * <p>应在上传事务提交、文件落存储之后调用；计数器只增不减， 上传失败或事务回滚时<b>不得</b>调用，否则容量增长趋势会失真。
   *
   * <p><b>线程安全：</b>底层 Micrometer Counter 为原子累加，可并发调用。 指标写入失败仅内部吞掉，不会向业务主流程抛异常。
   */
  public void recordUpload() {
    incrementCounter("file_upload_total");
  }

  /**
   * 记录一次文件下载，累加 {@code ydsz_nextwiki_file_download_total}。
   *
   * <p>与限流指标配合可定位「谁在刷下载」：该计数持续陡增而 分享/搜索指标平稳时，通常意味着存在批量拉取行为。 断点续传的多次 Range 请求按<b>每次请求</b>计数，而非按文件计数。
   */
  public void recordDownload() {
    incrementCounter("file_download_total");
  }

  /**
   * 记录一次文件删除，累加 {@code ydsz_nextwiki_file_delete_total}。
   *
   * <p>逻辑删除（回收站）与物理删除均计入本指标；短时间内的异常尖峰可作为误删/批量清理的告警信号。
   */
  public void recordDelete() {
    incrementCounter("file_delete_total");
  }

  /**
   * 记录一次分享链接创建，累加 {@code ydsz_nextwiki_share_create_total}。
   */
  public void recordShare() {
    incrementCounter("share_create_total");
  }

  /**
   * 记录一次搜索请求，累加 {@code ydsz_nextwiki_search_total}。
   */
  public void recordSearch() {
    incrementCounter("search_total");
  }

  /**
   * 记录一次预览生成，累加 {@code ydsz_nextwiki_preview_generate_total}。
   */
  public void recordPreview() {
    incrementCounter("preview_generate_total");
  }

  // ==================== Counter：失败次数 ====================

  /** 记录一次文件上传失败。 */
  public void recordUploadFailure() {
    incrementCounter("file_upload_failures_total");
  }

  /** 记录一次文件下载失败。 */
  public void recordDownloadFailure() {
    incrementCounter("file_download_failures_total");
  }

  /** 记录一次配额校验失败。 */
  public void recordQuotaCheckFailure() {
    incrementCounter("quota_check_failures_total");
  }

  // ==================== Counter：缓存命中/未命中 ====================

  /**
   * 记录一次缓存命中，累加 {@code ydsz_nextwiki_cache_hit_total}。
   *
   * <p>在 {@link com.njydsz.nextwiki.server.cache.NextwikiCacheService} 查询到缓存命中时调用。
   *
   * @param cacheType 缓存类型（file / children / quota 等）
   */
  public void recordCacheHit(String cacheType) {
    try {
      incrementCounter("cache_hit_total", "cache_type", cacheType != null ? cacheType : "unknown");
    } catch (Exception e) {
      log.warn("[NextwikiMetrics] 记录缓存命中失败: err={}", e.getMessage(), e);
    }
  }

  /**
   * 记录一次缓存未命中，累加 {@code ydsz_nextwiki_cache_miss_total}。
   *
   * <p>在 {@link com.njydsz.nextwiki.server.cache.NextwikiCacheService} 缓存未命中需要回查 DB 时调用。
   *
   * @param cacheType 缓存类型（file / children / quota 等）
   */
  public void recordCacheMiss(String cacheType) {
    try {
      incrementCounter("cache_miss_total", "cache_type", cacheType != null ? cacheType : "unknown");
    } catch (Exception e) {
      log.warn("[NextwikiMetrics] 记录缓存未命中失败: err={}", e.getMessage(), e);
    }
  }

  // ==================== Timer：操作耗时 ====================

  /**
   * 记录文件上传耗时（毫秒）。
   *
   * @param durationMs 上传耗时毫秒数
   */
  public void recordUploadDuration(long durationMs) {
    recordTimer("file_upload_duration", durationMs);
  }

  /**
   * 记录文件下载耗时（毫秒）。
   *
   * @param durationMs 下载耗时毫秒数
   */
  public void recordDownloadDuration(long durationMs) {
    recordTimer("file_download_duration", durationMs);
  }

  /**
   * 记录通用操作耗时（带 operation Tag 区分）。
   *
   * @param operation 操作名（如 share_create、permission_check）
   * @param durationMs 耗时毫秒数
   */
  public void recordOperationDuration(String operation, long durationMs) {
    try {
      recordTimer("operation_duration", durationMs, "operation", operation != null ? operation : "unknown");
    } catch (Exception e) {
      log.warn("[NextwikiMetrics] 记录操作耗时失败: err={}", e.getMessage(), e);
    }
  }

  /**
   * 在提供的 Runnable 执行期间计时（用于便捷地测量操作耗时）。
   *
   * @param operation 操作名
   * @param action 待测量的操作
   */
  public void timedOperation(String operation, Runnable action) {
    long start = System.currentTimeMillis();
    try {
      action.run();
    } finally {
      recordOperationDuration(operation, System.currentTimeMillis() - start);
    }
  }

  /**
   * 在提供的 Consumer 执行期间计时（用于上传/下载业务流程）。
   *
   * @param operation 操作名
   * @param action 待测量的操作
   * @param input 传入参数
   * @param <T> 输入参数类型
   */
  public <T> void timedOperation(String operation, Consumer<T> action, T input) {
    long start = System.currentTimeMillis();
    try {
      action.accept(input);
    } finally {
      recordOperationDuration(operation, System.currentTimeMillis() - start);
    }
  }

  // ==================== Gauge：配额用量 ====================

  /**
   * 刷新配额用量 Gauge 值（应定时调用，如每分钟刷新一次）。
   *
   * <p>查询失败不影响业务，Gauge 保留上次有效值。
   *
   * @param scopeType 配额维度
   * @param scopeId 配额 ID
   */
  public void refreshQuotaGauge(String scopeType, String scopeId) {
    try {
      StorageQuotaVO quota = quotaRepository.findByScope(scopeType, scopeId).orElse(null);
      if (quota != null && quota.getQuotaUsed() != null) {
        quotaUsageCached.set(quota.getQuotaUsed());
      }
    } catch (Exception e) {
      log.warn("[NextwikiMetrics] 配额 Gauge 刷新失败: {}", e.getMessage(), e);
    }
  }
}
