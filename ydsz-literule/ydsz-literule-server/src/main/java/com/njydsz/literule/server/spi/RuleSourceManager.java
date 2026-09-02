package com.njydsz.literule.server.spi;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;

/**
 * 规则数据源管理器（P1-5）
 *
 * <p>管理多个 {@link RuleConfigProvider} 实例，提供统一的数据源选择和切换能力。
 *
 * <p>功能：
 *
 * <ul>
 *   <li>注册/注销数据源
 *   <li>按类型选择主数据源
 *   <li>自动监听支持 Watch 的数据源变更
 *   <li>故障切换：主数据源不可用时自动降级到备选数据源
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class RuleSourceManager {

  private final Map<RuleConfigProvider.SourceType, RuleConfigProvider> sources = new ConcurrentHashMap<>();
  private volatile RuleConfigProvider activeSource;
  private final List<Consumer<List<RuleDefinitionDTO>>> globalListeners = new CopyOnWriteArrayList<>();

  /**
   * 注册数据源
   *
   * @param source 数据源实例
   */
  public synchronized void registerSource(RuleConfigProvider source) {
    if (source == null || !source.isAvailable()) {
      log.debug("[RuleSourceManager] 数据源 {} 不可用，跳过注册", source != null ? source.getType() : "null");
      return;
    }
    sources.put(source.getType(), source);
    // 首个数据源自动设为主数据源
    if (activeSource == null) {
      activeSource = source;
      // 注册全局监听器到新主数据源
      if (source.supportsWatch()) {
        source.addChangeListener(
            rules -> {
              log.info("[RuleSourceManager] {} 数据源规则变更: count={}", source.getType(), rules.size());
              notifyGlobalListeners(rules);
            });
      }
      log.info("[RuleSourceManager] 主数据源已设置: type={}", source.getType());
    }
  }

  /**
   * 切换主数据源
   *
   * @param type 目标数据源类型
   * @return true=切换成功
   */
  public synchronized boolean switchSource(RuleConfigProvider.SourceType type) {
    RuleConfigProvider target = sources.get(type);
    if (target == null || !target.isAvailable()) {
      log.warn("[RuleSourceManager] 数据源 {} 不可用，切换失败", type);
      return false;
    }
    activeSource = target;
    log.info("[RuleSourceManager] 主数据源已切换: type={}", type);
    return true;
  }

  /**
   * 加载启用的规则
   *
   * <p>从主数据源加载；若主数据源不可用，自动尝试其他可用数据源。
   *
   * @return 启用的规则定义列表
   */
  public List<RuleDefinitionDTO> loadEnabledRules() {
    RuleConfigProvider source = getAvailableSource();
    if (source == null) {
      log.warn("[RuleSourceManager] 无可用数据源，返回空列表");
      return List.of();
    }
    return source.loadEnabledRules();
  }

  /**
   * 获取可用的数据源（优先主数据源，故障时降级）
   *
   * @return 可用数据源；全部不可用返回 null
   */
  private RuleConfigProvider getAvailableSource() {
    if (activeSource != null && activeSource.isAvailable()) {
      return activeSource;
    }
    // 主数据源不可用，尝试其他数据源
    for (RuleConfigProvider source : sources.values()) {
      if (source.isAvailable()) {
        log.warn("[RuleSourceManager] 主数据源不可用，降级到: type={}", source.getType());
        activeSource = source;
        return source;
      }
    }
    return null;
  }

  /**
   * 注册全局规则变更监听器
   *
   * <p>当任意支持 Watch 的数据源检测到规则变更时，回调此监听器。
   *
   * @param listener 监听器
   */
  public void addGlobalChangeListener(Consumer<List<RuleDefinitionDTO>> listener) {
    globalListeners.add(listener);
    // 向所有支持 Watch 的数据源注册监听
    for (RuleConfigProvider source : sources.values()) {
      if (source.supportsWatch()) {
        source.addChangeListener(listener);
      }
    }
  }

  /** 通知全局监听器 */
  private void notifyGlobalListeners(List<RuleDefinitionDTO> rules) {
    for (Consumer<List<RuleDefinitionDTO>> listener : globalListeners) {
      try {
        listener.accept(rules);
      } catch (Exception e) {
        log.warn("[RuleSourceManager] 全局监听器回调异常: {}", e.getMessage());
      }
    }
  }

  /**
   * 获取主数据源
   *
   * @return 主数据源；未设置返回 null
   */
  public RuleConfigProvider getActiveSource() {
    return activeSource;
  }

  /**
   * 获取全部已注册的数据源
   *
   * @return 数据源映射
   */
  public Map<RuleConfigProvider.SourceType, RuleConfigProvider> getSources() {
    return Map.copyOf(sources);
  }

  /** 销毁全部数据源 */
  public synchronized void destroy() {
    for (RuleConfigProvider source : sources.values()) {
      try {
        source.destroy();
      } catch (Exception e) {
        log.warn("[RuleSourceManager] 销毁数据源 {} 异常: {}", source.getType(), e.getMessage());
      }
    }
    sources.clear();
    activeSource = null;
    globalListeners.clear();
  }
}
