package com.njydsz.common.feign.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.NotificationClient;
import com.njydsz.common.feign.dto.BroadcastRequestDTO;
import com.njydsz.common.feign.dto.PushRealtimeRequestDTO;

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
@Slf4j
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

  @Override
  public NotificationClient create(Throwable cause) {
    log.warn("[NotificationClient] 降级触发: {}", cause.getMessage());
    return new NotificationClient() {
      @Override
      public YdszResponse<MessageResult> sendMessage(MessageRequest request) {
        log.warn(
            "[NotificationClient] sendMessage 降级: receiver={}, subject={}, reason=消息中心服务不可用",
            request == null ? null : request.getReceiver(),
            request == null ? null : request.getSubject());
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "消息中心服务不可用");
      }

      @Override
      public YdszResponse<MessageResult> broadcast(BroadcastRequestDTO request) {
        String topic = request == null ? null : request.getTopic();
        log.warn("[NotificationClient] broadcast 降级: topic={}, reason=消息中心服务不可用", topic);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "消息中心服务不可用");
      }

      @Override
      public YdszResponse<MessageResult> pushRealtime(PushRealtimeRequestDTO request) {
        String userId = request == null ? null : request.getUserId();
        log.warn("[NotificationClient] pushRealtime 降级: userId={}, reason=消息中心服务不可用", userId);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "消息中心服务不可用");
      }
    };
  }
}
