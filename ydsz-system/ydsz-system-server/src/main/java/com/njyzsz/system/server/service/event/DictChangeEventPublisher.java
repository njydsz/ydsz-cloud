package com.njydsz.system.server.service.event;

import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.context.AuthContextUtils;
import com.njydsz.common.json.YdszJson;

@Slf4j
@Component
public class DictChangeEventPublisher {

  private final StringRedisTemplate stringRedisTemplate;

  public DictChangeEventPublisher(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
    this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    if (this.stringRedisTemplate == null) {
      log.info("[DictChangeEventPublisher] StringRedisTemplate not available, "
          + "dict change events will be silently dropped");
    }
  }

  public void publishDictTypeEvent(String dictCode, String eventType) {
    publishEvent(DictChangeEvent.builder()
        .dictCode(dictCode)
        .eventType(eventType)
        .operatorId(AuthContextUtils.getUserIdOrDefault("SYSTEM"))
        .operatorName(AuthContextUtils.getUserNameOrDefault("SYSTEM"))
        .timestamp(LocalDateTime.now())
        .build());
  }

  public void publishDictItemEvent(String dictCode, String dictItemCode, String eventType) {
    publishEvent(DictChangeEvent.builder()
        .dictCode(dictCode)
        .dictItemCode(dictItemCode)
        .eventType(eventType)
        .operatorId(AuthContextUtils.getUserIdOrDefault("SYSTEM"))
        .operatorName(AuthContextUtils.getUserNameOrDefault("SYSTEM"))
        .timestamp(LocalDateTime.now())
        .build());
  }

  private void publishEvent(DictChangeEvent event) {
    if (stringRedisTemplate == null) {
      log.debug("[DictChangeEventPublisher] Redis unavailable, skip event: dictCode={}",
          event.getDictCode());
      return;
    }
    try {
      String json = YdszJson.toJson(event);
      stringRedisTemplate.convertAndSend(DictChangeEventConstants.REDIS_CHANNEL, json);
      log.debug("[DictChangeEventPublisher] Published dict change event: dictCode={}, eventType={}",
          event.getDictCode(), event.getEventType());
    } catch (Exception e) {
      log.warn(
          "[DictChangeEventPublisher] Failed to publish dict change event "
              + "(non-blocking): dictCode={}, eventType={}, err={}",
          event.getDictCode(), event.getEventType(), e.getMessage());
    }
  }
}
