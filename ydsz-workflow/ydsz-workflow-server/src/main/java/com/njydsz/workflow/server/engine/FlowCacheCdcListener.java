package com.njydsz.workflow.server.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 流程定义缓存 CDC 失效监听器。
 *
 * <p>监听 {@link FlowCacheInvalidateEvent} 事件，异步调用 {@link FlowDefinitionCacheService#evict(String)}
 * 失效本地缓存（及其集群广播）。
 *
 * <p><b>调用方说明：</b>
 *
 * <ul>
 *   <li>CDC 管道（Canal/Debezium）消费者通过注入 {@code ApplicationEventPublisher} 发布 {@link FlowCacheInvalidateEvent}</li>
 *   <li>运维工具 / 管理后台主动调用</li>
 *   <li>DBA 改库后手动触发缓存刷新</li>
 * </ul>
 *
 * <p><b>异步执行：</b>{@code @Async} 确保 CDC 事件消费不阻塞 Canal 消费线程。
 *
 * @author ydsz-team
 * @since 26.09.23
 * @see FlowCacheInvalidateEvent 缓存失效事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowCacheCdcListener {

  private final FlowDefinitionCacheService cacheService;

  /**
   * 异步处理缓存失效事件。
   *
   * @param event 缓存失效事件
   */
  @Async
  @EventListener
  public void onCacheInvalidate(FlowCacheInvalidateEvent event) {
    if (event == null || event.getDefinitionId() == null) {
      log.warn("[FlowCacheCdc] 收到无效缓存失效事件");
      return;
    }
    try {
      log.info("[FlowCacheCdc] 缓存失效: definitionId={} source={}",
          event.getDefinitionId(), event.getSource());
      cacheService.evict(event.getDefinitionId());
    } catch (Exception e) {
      log.warn("[FlowCacheCdc] 缓存失效处理异常 definitionId={}: {}",
          event.getDefinitionId(), e.getMessage());
    }
  }
}
