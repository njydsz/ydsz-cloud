package com.njydsz.system.server.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.system.server.cache.CacheInvalidationPublisher;

/**
 * 进程内缓存失效监听器 — System 模块订阅自身发布配置/字典/变量的领域事件。
 *
 * <p>当前订阅：
 *
 * <ul>
 *   <li>{@code CONFIG_CHANGED} — 配置变更事件通知，精准失效配置缓存
 *   <li>{@code DICT_TYPE_CHANGED} — 字典类型变更事件通知，精准失效字典缓存
 *   <li>{@code VARIABLE_CHANGED} — 变量变更事件通知，精准失效变量缓存
 * </ul>
 *
 * <p><b>语义说明（重要）：</b>本监听器通过 {@code @EventListener} 订阅的是 <b>进程内</b> Spring 事件（{@code
 * OutboxService} 在事务提交后经 {@code ApplicationEventPublisher} 发布），<b>不会跨 JVM 传播</b>。因此它仅是
 * 写操作所在实例的<b>防御性兜底失效</b>——各 Service 写方法已通过 {@code @CacheEvict} 完成精准失效，本监听器是二次保险。
 *
 * <p><b>跨实例一致性：</b>多实例部署下，其他实例的本地缓存通过 TTL（配置 5min / 字典 10min）自然过期回源，实现最终一致；
 * 若需更强的跨实例实时失效，应在 common-event 侧增加 RocketMQ 消费回灌（将 MQ 消息重新发布为进程内事件）或引入 Redis
 * Pub/Sub 失效总线。
 *
 * <p><b>精准失效：</b>为避免 {@code cache.clear()} 全量清空导致的跨租户缓存雪崩，本监听器按 {@code 事件租户 + 资源键}
 * 逐 key 失效（与 {@code CacheKeyBuilder} 生成的键格式一致）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

  /** 默认租户 ID（与 CacheKeyBuilder 兜底值一致） */
  private static final String DEFAULT_TENANT = "default";

  private final CacheManager cacheManager;
  private final CacheInvalidationPublisher invalidationPublisher;

  /**
   * 配置变更 — 精准失效本实例配置缓存（防御性兜底）。
   *
   * <p>按 {@code value:{tenantId}:{configKey}} 失效单键缓存，同时失效 {@code public:{tenantId}}
   * 公开配置缓存（公开标记可能发生变化）。分组缓存由写方法 {@code @CacheEvict} 在同一实例精准失效。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).CONFIG_CHANGED")
  public void onConfigChanged(OutboxMessage message) {
    try {
      String tenantId = resolveTenant(message);
      String configKey = message.getAggregateId();
      if (configKey == null || configKey.isBlank()) {
        return;
      }
      evict(CacheConstants.SYSTEM_CONFIG_CACHE, "value:" + tenantId + ":" + configKey);
      evict(CacheConstants.SYSTEM_CONFIG_CACHE, "public:" + tenantId);
      // P1-7: 跨实例缓存失效（Redis Pub/Sub 通知其他实例清除本地缓存）
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_CONFIG_CACHE, "value:" + tenantId + ":" + configKey);
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_CONFIG_CACHE, "public:" + tenantId);
      log.debug("[CrossModuleEventListener] 精准失效配置缓存: tenant={}, key={}", tenantId, configKey);
    } catch (Exception e) {
      // 监听器异常必须吞掉，不能影响事件发布方事务（《云顶编码规范》27.3 规则 25.3.2）
      log.error("[CrossModuleEventListener] 配置缓存失效失败: key={}", message.getAggregateId(), e);
    }
  }

  /**
   * 字典类型变更 — 精准失效本实例字典缓存（防御性兜底）。
   *
   * <p>按 {@code list:{tenantId}:{typeCode}} 失效字典列表缓存。单条字典项缓存无法从事件中还原
   * itemCode，交由 TTL 兜底。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).DICT_TYPE_CHANGED")
  public void onDictTypeChanged(OutboxMessage message) {
    try {
      String tenantId = resolveTenant(message);
      String typeCode = message.getAggregateId();
      if (typeCode == null || typeCode.isBlank()) {
        return;
      }
      evict(CacheConstants.SYSTEM_DICT_ITEM_CACHE, "list:" + tenantId + ":" + typeCode);
      // P1-7: 跨实例缓存失效
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_DICT_ITEM_CACHE, "list:" + tenantId + ":" + typeCode);
      log.debug("[CrossModuleEventListener] 精准失效字典缓存: tenant={}, typeCode={}", tenantId, typeCode);
    } catch (Exception e) {
      log.error("[CrossModuleEventListener] 字典缓存失效失败: typeCode={}", message.getAggregateId(), e);
    }
  }

  /**
   * 变量变更 — 精准失效本实例变量缓存（防御性兜底）。
   *
   * <p>按 {@code {tenantId}:{variableKey}} 失效变量键缓存。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).VARIABLE_CHANGED")
  public void onVariableChanged(OutboxMessage message) {
    try {
      String tenantId = resolveTenant(message);
      String variableKey = message.getAggregateId();
      if (variableKey == null || variableKey.isBlank()) {
        return;
      }
      evict(CacheConstants.SYSTEM_VARIABLE_CACHE, tenantId + ":" + variableKey);
      // P1-7: 跨实例缓存失效
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_VARIABLE_CACHE, tenantId + ":" + variableKey);
      log.debug("[CrossModuleEventListener] 精准失效变量缓存: tenant={}, key={}", tenantId, variableKey);
    } catch (Exception e) {
      log.error("[CrossModuleEventListener] 变量缓存失效失败: key={}", message.getAggregateId(), e);
    }
  }

  /**
   * 解析事件租户 ID（空值回退默认租户）。
   *
   * @param message Outbox 消息
   * @return 非空租户 ID
   */
  private String resolveTenant(OutboxMessage message) {
    String tenantId = message.getTenantId();
    return tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT;
  }

  /**
   * 按 key 精准失效指定缓存（缓存不存在或 key 不存在时为安全空操作）。
   *
   * @param cacheName 缓存名称
   * @param key 缓存键
   */
  private void evict(String cacheName, String key) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.evict(key);
    }
  }
}
