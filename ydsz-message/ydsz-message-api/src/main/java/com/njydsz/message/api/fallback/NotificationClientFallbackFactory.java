package com.njydsz.message.api.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.dto.BroadcastRequestDTO;
import com.njydsz.common.feign.dto.PushRealtimeRequestDTO;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.message.api.client.NotificationClient;

/**
 * {@link NotificationClient} 的降级工厂。
 *
 * <p>消息中心服务不可用时降级处理，保证调用方主流程不受影响 （消息发送是辅助功能，不应阻断业务）。
 *
 * <p>降级策略：
 *
 * <ul>
 *   <li>sendMessage：返回 {@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，让调用方明确感知服务不可用
 *   <li>broadcast：返回服务不可用错误（P0-3-fix 不再静默忽略，使调用方可感知）
 *   <li>pushRealtime：返回服务不可用错误
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

  private static final Logger log = LoggerFactory.getLogger(NotificationClientFallbackFactory.class);

  /** 降级消息（走 i18n，不可用时回退到中文） */
  private static final String MESSAGE_UNAVAILABLE =
      MessageUtils.getMessage("message.service.unavailable", "消息中心服务不可用");

  @Override
  public NotificationClient create(Throwable cause) {
    log.warn("[NotificationClient] 降级触发: {}", cause.getMessage());
    return new NotificationClient() {
      @Override
      public YdszResponse<MessageResult> sendMessage(MessageRequest request) {
        log.warn(
            "[NotificationClient] sendMessage 降级: receiver={}, subject={}, reason={}",
            request == null ? null : request.getReceiver(),
            request == null ? null : request.getSubject(),
            MESSAGE_UNAVAILABLE);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, MESSAGE_UNAVAILABLE);
      }

      @Override
      public YdszResponse<MessageResult> broadcast(BroadcastRequestDTO request) {
        String topic = request == null ? null : request.getTopic();
        log.warn("[NotificationClient] broadcast 降级: topic={}, reason={}", topic, MESSAGE_UNAVAILABLE);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, MESSAGE_UNAVAILABLE);
      }

      @Override
      public YdszResponse<MessageResult> pushRealtime(PushRealtimeRequestDTO request) {
        String userId = request == null ? null : request.getUserId();
        log.warn("[NotificationClient] pushRealtime 降级: userId={}, reason={}", userId, MESSAGE_UNAVAILABLE);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, MESSAGE_UNAVAILABLE);
      }
    };
  }
}
