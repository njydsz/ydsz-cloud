package com.njydsz.nextwiki.server.metrics;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * NextWiki Micrometer 指标采集。
 *
 * <p>P0-2 架构优化：继承 {@link SentryMetricsAdapter}，统一指标前缀 {@code ydsz_nextwiki_}， 消除手动 Counter 创建和
 * {@code @PostConstruct} 样板代码。
 *
 * <p>暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 *
 * <ul>
 *   <li>{@code ydsz_nextwiki_file_upload_total} — 文件上传次数
 *   <li>{@code ydsz_nextwiki_file_download_total} — 文件下载次数
 *   <li>{@code ydsz_nextwiki_file_delete_total} — 文件删除次数
 *   <li>{@code ydsz_nextwiki_share_create_total} — 分享创建次数
 *   <li>{@code ydsz_nextwiki_search_total} — 搜索请求次数
 *   <li>{@code ydsz_nextwiki_preview_generate_total} — 预览生成次数
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class NextwikiMetrics extends SentryMetricsAdapter {

  public NextwikiMetrics() {
    super("ydsz_nextwiki_");
    log.info("[NextwikiMetrics] 初始化完成，指标前缀 ydsz_nextwiki_");
  }

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
   * <p>逻辑删除（回收站）与物理删除均计入本指标，二者无法从该计数区分； 短时间内的异常尖峰可作为误删/批量清理的告警信号。
   */
  public void recordDelete() {
    incrementCounter("file_delete_total");
  }

  /**
   * 记录一次分享链接创建，累加 {@code ydsz_nextwiki_share_create_total}。
   *
   * <p>仅统计创建动作，分享链接被访问的次数不在此计数内； 该指标用于观测外链外发规模，是数据外泄风险的辅助监控项。
   */
  public void recordShare() {
    incrementCounter("share_create_total");
  }

  /**
   * 记录一次搜索请求，累加 {@code ydsz_nextwiki_search_total}。
   *
   * <p>无论是否命中结果均计数，用于评估检索链路（ES/DB）的真实负载； 若需区分命中率，应另行埋点，勿复用本计数器。
   */
  public void recordSearch() {
    incrementCounter("search_total");
  }

  /**
   * 记录一次预览生成，累加 {@code ydsz_nextwiki_preview_generate_total}。
   *
   * <p>仅在<b>实际触发转换</b>时调用；命中预览缓存的请求不计数， 因此该指标反映的是转换服务的算力消耗，而非预览访问量。
   */
  public void recordPreview() {
    incrementCounter("preview_generate_total");
  }
}
