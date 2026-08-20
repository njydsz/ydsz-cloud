package com.njydsz.system.server.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.system.server.cache.CacheInvalidationPublisher;

/**
 * 跨实例缓存失效消息转发器 — System 模块订阅配置/字典/变量的领域事件，向 Redis Pub/Sub 频道发布失效通知。
 *
 * <p>当前订阅：
 *
 * <ul>
 *   <li>{@code CONFIG_CHANGED} — 配置变更事件通知，发布跨实例缓存失效消息
 *   <li>{@code DICT_TYPE_CHANGED} — 字典类型变更事件通知，发布跨实例缓存失效消息
 *   <li>{@code VARIABLE_CHANGED} — 变量变更事件通知，发布跨实例缓存失效消息
 * </ul>
 *
 * <p><b>架构定位（v2.23 过度设计整改）：</b>根据《云顶编码规范》规则 35.4.1，缓存失效必须遵循"单点精准失效"原则，
 * 禁止 @CacheEvict + 监听器本地失效 + Pub/Sub 三重叠加。本监听器职责已精简为<b>跨实例消息转发器</b>，不再执行本地缓存失效。
 *
 * <p><b>缓存失效链路（整改后）：</b>
 * <ol>
 *   <li>写操作所在 Service 方法通过 {@code @CacheEvict} 在<b>本地实例</b>精准失效（第一层）</li>
 *   <li>本监听器接收领域事件后，通过 {@link CacheInvalidationPublisher} 向 Redis Pub/Sub 频道发布消息</li>
 *   <li>其他实例的 {@code CacheInvalidationSubscriber} 接收消息后清除<b>各自本地缓存</b></li>
 * </ol>
 *
 * <p><b>跨实例一致性：</b>多实例部署下，其他实例的本地缓存通过 TTL（配置 5min / 字典 10min）自然过期回源，实现最终一致；
 * 若需更强的跨实例实时失效，启用 {@code ydsz.system.cache.cross-instance-enabled=true}。
 *
 * <p><b>启用条件：</b>跨实例缓存失效功能通过 {@code @ConditionalOnProperty} 在 {@link CacheInvalidationPublisher} 和
 * {@code CacheInvalidationSubscriber} 上控制，本监听器始终订阅事件，仅当 {@code cross-instance-enabled=true} 时
 * 发布的消息才会被订阅端实际处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CacheInvalidationPublisher 跨实例缓存失效消息发布者
 * @see com.njydsz.system.server.cache.CacheInvalidationSubscriber 跨实例缓存失效消息订阅者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

  /** 默认租户 ID（与 CacheKeyBuilder 兜底值一致） */
  private static final String DEFAULT_TENANT = "default";

  private final CacheInvalidationPublisher invalidationPublisher;

  /**
   * 配置变更 — 发布跨实例缓存失效消息。
   *
   * <p>按 {@code value:{tenantId}:{configKey}} 和 {@code public:{tenantId}} 构造失效键，
   * 通过 Redis Pub/Sub 通知其他实例清除对应缓存。
   *
   * <p>注意：<b>不执行本地缓存失效</b>，写方法已通过 {@code @CacheEvict} 完成，避免重复失效（规范 35.4.1）。
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
      // 跨实例缓存失效：向 Redis Pub/Sub 发布消息，通知其他实例清除本地缓存
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_CONFIG_CACHE, "value:" + tenantId + ":" + configKey);
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_CONFIG_CACHE, "public:" + tenantId);
      log.debug("[CrossModuleEventListener] 发布配置缓存跨实例失效消息: tenant={}, key={}", tenantId, configKey);
    } catch (Exception e) {
      // 监听器异常必须吞掉，不能影响事件发布方事务（《云顶编码规范》规则 35.2.2）
      log.error("[CrossModuleEventListener] 发布配置缓存跨实例失效消息失败: key={}", message.getAggregateId(), e);
    }
  }

  /**
   * 字典类型变更 — 发布跨实例缓存失效消息。
   *
   * <p>按 {@code list:{tenantId}:{typeCode}} 构造失效键，通过 Redis Pub/Sub 通知其他实例清除对应缓存。
   * 单条字典项缓存无法从事件中还原 itemCode，交由 TTL 兜底。
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
      // 跨实例缓存失效
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_DICT_ITEM_CACHE, "list:" + tenantId + ":" + typeCode);
      log.debug("[CrossModuleEventListener] 发布字典缓存跨实例失效消息: tenant={}, typeCode={}", tenantId, typeCode);
    } catch (Exception e) {
      log.error("[CrossModuleEventListener] 发布字典缓存跨实例失效消息失败: typeCode={}", message.getAggregateId(), e);
    }
  }

  /**
   * 变量变更 — 发布跨实例缓存失效消息。
   *
   * <p>按 {@code {tenantId}:{variableKey}} 构造失效键，通过 Redis Pub/Sub 通知其他实例清除对应缓存。
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
      // 跨实例缓存失效
      invalidationPublisher.publishEviction(CacheConstants.SYSTEM_VARIABLE_CACHE, tenantId + ":" + variableKey);
      log.debug("[CrossModuleEventListener] 发布变量缓存跨实例失效消息: tenant={}, key={}", tenantId, variableKey);
    } catch (Exception e) {
      log.error("[CrossModuleEventListener] 发布变量缓存跨实例失效消息失败: key={}", message.getAggregateId(), e);
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
}
