package com.njydsz.message.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.message.api.client.MessageSendClient;

/**
 * {@link MessageSendClient} 的 FallbackFactory。
 *
 * <p>消息中心服务不可用时降级返回统一错误码 ({@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE})， 仅记录 WARN
 * 日志，保证调用方主流程不受影响。
 *
 * <p>注意：必须返回 error 而非 success(null)， 否则调用方通过 {@code isSuccess()} 检查会误判为发送成功。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MessageSendClientFallback implements FallbackFactory<MessageSendClient> {

  @Override
  public MessageSendClient create(Throwable cause) {
    log.warn("[MessageSendClient] 降级触发: {}", cause.getMessage());
    return new MessageSendClient() {
      @Override
      public YdszResponse<String> sendMessage(MessageRequest request) {
        String errorMsg = MessageUtils.getMessage("message.service.unavailable", "消息中心服务不可用");
        log.warn(
            "[MessageSendClient] sendMessage 降级: receiver={}, subject={}, reason={}",
            request == null ? null : request.getReceiver(),
            request == null ? null : request.getSubject(),
            errorMsg);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, errorMsg);
      }
    };
  }
}
