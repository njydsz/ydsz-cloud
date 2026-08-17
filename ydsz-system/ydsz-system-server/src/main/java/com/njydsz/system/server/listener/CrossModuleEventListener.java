package com.njydsz.system.server.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.event.model.OutboxMessage;

/**
 * 跨模块事件监听器 — System 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 *
 * <ul>
 *   <li>{@code CONFIG_CHANGED} — 配置变更事件通知，触发本地配置缓存清空
 *   <li>{@code DICT_TYPE_CHANGED} — 字典类型变更事件通知，触发本地字典缓存清空
 *   <li>{@code VARIABLE_CHANGED} — 变量变更事件通知，触发本地变量缓存清空
 * </ul>
 *
 * <p><b>跨实例一致性：</b>在多实例部署下，实例 A 修改配置/字典/变量后通过 Outbox 发布事件， 实例 B / C / ... 监听事件后清空本地 ydsz-common-cache 缓存，
 * 下次读取时自动从 DB 重新加载最新值。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

  private final CacheManager cacheManager;

  /**
   * 配置变更 — 清空本地配置缓存（跨实例一致性保障）。
   *
   * <p>其他实例修改配置并通过 Outbox 发布 {@code CONFIG_CHANGED} 事件后， 本实例通过清空 {@link
   * CacheConstants#SYSTEM_CONFIG_CACHE} 缓存， 使下次读取自动回源到 DB 获取最新值。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).CONFIG_CHANGED")
  public void onConfigChanged(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收配置变更事件，清空本地配置缓存: configId={}", message.getAggregateId());
    cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).clear();
  }

  /**
   * 字典类型变更 — 清空本地字典缓存（跨实例一致性保障）。
   *
   * <p>其他实例修改字典类型并通过 Outbox 发布 {@code DICT_TYPE_CHANGED} 事件后， 本实例通过清空 {@link
   * CacheConstants#SYSTEM_DICT_ITEM_CACHE} 缓存， 使下次读取自动回源到 DB 获取最新值。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).DICT_TYPE_CHANGED")
  public void onDictTypeChanged(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收字典类型变更事件，清空本地字典缓存: typeCode={}", message.getAggregateId());
    cacheManager.getCache(CacheConstants.SYSTEM_DICT_ITEM_CACHE).clear();
  }

  /**
   * 变量变更 — 清空本地变量缓存（跨实例一致性保障）。
   *
   * <p>其他实例修改变量并通过 Outbox 发布 {@code VARIABLE_CHANGED} 事件后， 本实例通过清空 {@link
   * CacheConstants#SYSTEM_VARIABLE_CACHE} 缓存， 使下次读取自动回源到 DB 获取最新值。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).VARIABLE_CHANGED")
  public void onVariableChanged(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收变量变更事件，清空本地变量缓存: variableKey={}", message.getAggregateId());
    cacheManager.getCache(CacheConstants.SYSTEM_VARIABLE_CACHE).clear();
  }
}
