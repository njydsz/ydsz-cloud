package com.njydsz.common.search.service;

import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.search.config.SearchProperties;

/**
 * 搜索词典热加载管理器 — 定期检测同义词/拼音词典文件是否被修改并在变更时触发重加载。
 *
 * <p>热加载间隔由 {@code ydsz.search.dictionary-hot-reload.reload-interval-seconds} 配置（默认 60 秒）。 修改同义词或拼音词典文件后，最多 {@code reloadIntervalSeconds}
 * 秒内自动生效，无需重启服务。
 *
 * <p><b>约束</b>：仅对文件系统路径生效（{@code classpath:} 资源在 JAR 内，运行时不可修改）。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class SearchDictionaryManager {

  private final SearchProperties properties;
  private final SearchTextProcessor textProcessor;

  /** 累计热加载次数（用于监控指标采集） */
  private final AtomicLong reloadCount = new AtomicLong(0);

  public SearchDictionaryManager(SearchProperties properties, SearchTextProcessor textProcessor) {
    this.properties = properties;
    this.textProcessor = textProcessor;
  }

  /**
   * 定时检测词典文件 {@code lastModified} 并在变更时触发重加载。
   *
   * <p>第一次调用在容器启动后 {@code reloadIntervalSeconds} 秒执行，之后按固定间隔重复。 若本次未检测到变更则静默返回，不打日志。
   */
  @Scheduled(
      fixedDelayString = "${ydsz.search.dictionary-hot-reload.reload-interval-seconds:60}000",
      initialDelayString = "${ydsz.search.dictionary-hot-reload.reload-interval-seconds:60}000")
  public void reloadIfChanged() {
    SearchProperties.DictionaryHotReloadConfig config = properties.getDictionaryHotReload();
    if (!config.isEnabled()) {
      return;
    }
    try {
      boolean reloaded = textProcessor.reloadIfChanged();
      if (reloaded) {
        reloadCount.incrementAndGet();
      }
    } catch (Exception e) {
      log.warn("[SearchDictionaryManager] 热加载异常: {}", e.getMessage());
    }
  }

  /**
   * 获取累计热加载次数。
   *
   * @return 从应用启动至今的热加载总次数
   */
  public long getReloadCount() {
    return reloadCount.get();
  }
}
