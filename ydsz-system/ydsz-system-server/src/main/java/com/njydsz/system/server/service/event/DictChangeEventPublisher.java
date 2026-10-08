package com.njydsz.system.server.service.event;

import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisPubSubOps;

@Slf4j
@Component
public class DictChangeEventPublisher {

  /** Redis Pub/Sub 操作组件——替代直接注入 StringRedisTemplate，符合 ydzs-common-redis 封装规范 */
  private final RedisPubSubOps redisPubSubOps;

  public DictChangeEventPublisher(ObjectProvider<RedisPubSubOps> redisPubSubOpsProvider) {
    this.redisPubSubOps = redisPubSubOpsProvider.getIfAvailable();
    if (this.redisPubSubOps == null) {
      log.info("[DictChangeEventPublisher] RedisPubSubOps not available, "
          + "dict change events will be silently dropped");
    }
  }

  public void publishDictTypeEvent(String dictCode, String eventType) {
    publishEvent(DictChangeEvent.builder()
        .dictCode(dictCode)
        .eventType(eventType)
        .operatorId(resolveOperatorId())
        .operatorName(resolveOperatorName())
        .timestamp(LocalDateTime.now())
        .build());
  }

  public void publishDictItemEvent(String dictCode, String dictItemCode, String eventType) {
    publishEvent(DictChangeEvent.builder()
        .dictCode(dictCode)
        .dictItemCode(dictItemCode)
        .eventType(eventType)
        .operatorId(resolveOperatorId())
        .operatorName(resolveOperatorName())
        .timestamp(LocalDateTime.now())
        .build());
  }

  private static String resolveOperatorId() {
    LoginUser user = AuthContextUtils.getCurrentOrNull();
    return user != null ? user.getUserId() : "SYSTEM";
  }

  private static String resolveOperatorName() {
    LoginUser user = AuthContextUtils.getCurrentOrNull();
    return user != null ? user.getUsername() : "SYSTEM";
  }

  private void publishEvent(DictChangeEvent event) {
    if (redisPubSubOps == null) {
      log.debug("[DictChangeEventPublisher] Redis unavailable, skip event: dictCode={}",
          event.getDictCode());
      return;
    }
    try {
      String json = YdszJson.toJson(event);
      redisPubSubOps.publish(DictChangeEventConstants.REDIS_CHANNEL, json);
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
