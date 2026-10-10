package com.njydsz.message.server.event;


import com.njydsz.common.locales.util.I18n;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.event.MessageDomainEvent;

/**
 * 领域事件发布器。
 *
 * <p>封装 Spring {@link ApplicationEventPublisher}，提供统一的领域事件发布入口， 并增加异常隔离：事件发布失败不应影响主业务逻辑。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  /**
   * 发布领域事件。
   *
   * <p>发布异常仅记录日志，不向上抛出，保证主流程不受影响。
   *
   * @param event 领域事件
   */
  public void publish(MessageDomainEvent event) {
    if (event == null) {
      return;
    }
    try {
      applicationEventPublisher.publishEvent(event);
      log.debug(I18n.message("message.log.other.DomainEvent_type_{}_eventId_{}.ef3ea9"), event.eventType(), event.getEventId());
    } catch (Exception e) {
      log.warn(I18n.message("message.log.other.DomainEvent_type_{}_err_{}.1f2d1b"), event.eventType(), e.getMessage());
    }
  }
}
