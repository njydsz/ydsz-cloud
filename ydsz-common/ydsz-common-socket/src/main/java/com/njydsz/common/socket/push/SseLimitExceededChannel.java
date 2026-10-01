package com.njydsz.common.socket.push;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接数超限时返回的错误通道（快速失败）。
 *
 * <p>当全局 SSE 连接数达到配置上限时，{@link SsePushChannelMvcFactory} 返回此通道而非真实通道。
 * 调用方首次调用 {@link #sendEvent} 时即抛出 IOException，上层应据此向客户端返回 503。
 *
 * <p>该占位实现遵循 null-object 模式，避免调用方 null-check 分支散落。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
public class SseLimitExceededChannel implements SsePushChannel {

  /** 错误消息 */
  private static final String ERROR_MESSAGE = "SSE 连接数超限,请稍后重试";

  @Override
  public void sendEvent(String eventName, Object payload) throws IOException {
    throw new IOException(ERROR_MESSAGE);
  }

  @Override
  public void sendHeartbeat() throws IOException {
    throw new IOException(ERROR_MESSAGE);
  }

  @Override
  public void complete() {
    // no-op
  }

  @Override
  public void completeWithError(Throwable error) {
    // no-op
  }

  @Override
  public boolean isClosed() {
    return true;
  }

  @Override
  public String getSessionId() {
    return "limit-exceeded";
  }

  @Override
  public SseEmitter getEmitter() {
    throw new UnsupportedOperationException("限流错误通道无底层 SseEmitter");
  }
}
