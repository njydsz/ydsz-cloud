package com.njydsz.common.file.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 文件存储监控指标收集器
 *
 * <p>基于 Micrometer 实现，收集文件上传/下载/删除、文件去重命中/未命中、 病毒检测、上传/下载错误等监控指标。
 *
 * <p><b>监控指标列表：</b>
 *
 * <ul>
 *   <li>{@code file.upload.count} - 上传总次数
 *   <li>{@code file.download.count} - 下载总次数
 *   <li>{@code file.delete.count} - 删除总次数
 *   <li>{@code file.dedup.hit} - 秒传命中次数
 *   <li>{@code file.dedup.miss} - 秒传未命中次数
 *   <li>{@code file.virus.detected} - 病毒检测命中次数
 *   <li>{@code file.upload.duration} - 上传耗时
 *   <li>{@code file.download.duration} - 下载耗时
 *   <li>{@code file.upload.errors} - 上传错误（按错误码标签区分）
 *   <li>{@code file.download.errors} - 下载错误（按错误码标签区分）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FileMetrics {

  /** 指标名称前缀 */
  private static final String PREFIX = "file.";

  /** Micrometer 注册中心 */
  private final MeterRegistry registry;

  /** 上传次数计数器 */
  private final Counter uploadCounter;

  /** 下载次数计数器 */
  private final Counter downloadCounter;

  /** 删除次数计数器 */
  private final Counter deleteCounter;

  /** 秒传命中计数器 */
  private final Counter dedupHitCounter;

  /** 秒传未命中计数器 */
  private final Counter dedupMissCounter;

  /** 病毒检测命中计数器 */
  private final Counter virusDetectedCounter;

  /** 上传耗时计时器 */
  private final Timer uploadTimer;

  /** 下载耗时计时器 */
  private final Timer downloadTimer;

  /** 上传错误计数器（按错误码分组） */
  private final ConcurrentMap<String, Counter> uploadErrorCounters = new ConcurrentHashMap<>();

  /** 下载错误计数器（按错误码分组） */
  private final ConcurrentMap<String, Counter> downloadErrorCounters = new ConcurrentHashMap<>();

  /**
   * 构造文件指标收集器
   *
   * @param registry Micrometer 指标注册中心，传 null 时所有指标变为无操作（禁用状态）
   */
  public FileMetrics(MeterRegistry registry) {
    this.registry = registry;
    if (registry != null) {
      this.uploadCounter =
          Counter.builder(PREFIX + "upload.count")
              .description("Total file upload count")
              .register(registry);
      this.downloadCounter =
          Counter.builder(PREFIX + "download.count")
              .description("Total file download count")
              .register(registry);
      this.deleteCounter =
          Counter.builder(PREFIX + "delete.count")
              .description("Total file delete count")
              .register(registry);
      this.dedupHitCounter =
          Counter.builder(PREFIX + "dedup.hit")
              .description("File dedup hit count")
              .register(registry);
      this.dedupMissCounter =
          Counter.builder(PREFIX + "dedup.miss")
              .description("File dedup miss count")
              .register(registry);
      this.virusDetectedCounter =
          Counter.builder(PREFIX + "virus.detected")
              .description("Virus detected count")
              .register(registry);
      this.uploadTimer =
          Timer.builder(PREFIX + "upload.duration")
              .description("File upload duration")
              .register(registry);
      this.downloadTimer =
          Timer.builder(PREFIX + "download.duration")
              .description("File download duration")
              .register(registry);
    } else {
      this.uploadCounter = null;
      this.downloadCounter = null;
      this.deleteCounter = null;
      this.dedupHitCounter = null;
      this.dedupMissCounter = null;
      this.virusDetectedCounter = null;
      this.uploadTimer = null;
      this.downloadTimer = null;
    }
  }

  /**
   * 记录一次上传操作
   *
   * @param durationNanos 上传耗时（纳秒）
   */
  public void recordUpload(long durationNanos) {
    if (uploadCounter != null) uploadCounter.increment();
    if (uploadTimer != null) uploadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * 记录一次下载操作
   *
   * @param durationNanos 下载耗时（纳秒）
   */
  public void recordDownload(long durationNanos) {
    if (downloadCounter != null) downloadCounter.increment();
    if (downloadTimer != null) downloadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /** 记录一次删除操作 */
  public void recordDelete() {
    if (deleteCounter != null) deleteCounter.increment();
  }

  /** 记录秒传命中 */
  public void recordDedupHit() {
    if (dedupHitCounter != null) dedupHitCounter.increment();
  }

  /** 记录秒传未命中 */
  public void recordDedupMiss() {
    if (dedupMissCounter != null) dedupMissCounter.increment();
  }

  /** 记录病毒检测命中 */
  public void recordVirusDetected() {
    if (virusDetectedCounter != null) virusDetectedCounter.increment();
  }

  /**
   * 记录上传错误（按错误码分类统计）
   *
   * @param errorCode 错误码，为 null 时归类为 "unknown"
   */
  public void recordUploadError(String errorCode) {
    if (registry != null) {
      String code = errorCode != null ? errorCode : "unknown";
      uploadErrorCounters
          .computeIfAbsent(
              code,
              c ->
                  Counter.builder(PREFIX + "upload.errors")
                      .tag("code", c)
                      .description("File upload error count")
                      .register(registry))
          .increment();
    }
  }

  /**
   * 记录下载错误（按错误码分类统计）
   *
   * @param errorCode 错误码，为 null 时归类为 "unknown"
   */
  public void recordDownloadError(String errorCode) {
    if (registry != null) {
      String code = errorCode != null ? errorCode : "unknown";
      downloadErrorCounters
          .computeIfAbsent(
              code,
              c ->
                  Counter.builder(PREFIX + "download.errors")
                      .tag("code", c)
                      .description("File download error count")
                      .register(registry))
          .increment();
    }
  }

  /**
   * 判断指标收集器是否可用
   *
   * @return true 表示已注册 MeterRegistry，指标正常采集
   */
  public boolean isAvailable() {
    return registry != null;
  }
}
