package com.njyzsz.system.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.system.server.config.DictSseEmitterRegistry;

/**
 * 字典变更 SSE 推送 Controller — 为前端建立持久化 SSE 长连接。
 *
 * <p>连接建立后，所有通过 Redis {@code ydsz:dict:change} 通道广播的字典变更事件
 * 会实时推送到已连接的前端标签页，解决多标签页 / 多子应用间字典缓存无法感知对方变更的问题。
 *
 * <p><b>前端接入方式：</b>
 * <pre>{@code
 * const source = new EventSource('/api/v1/dict/sse');
 * source.addEventListener('message', (e) => {
 *   const event = JSON.parse(e.data);
 *   console.log('dict changed:', event.dictCode, event.eventType);
 *   // 触发本地字典缓存刷新
 * });
 * }</pre>
 *
 * <p><b>心跳保活：</b>服务端每 30s 发送 {@code :keepalive} 注释行，
 * 防止 Nginx/CDN/浏览器空闲断开连接。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see DictSseEmitterRegistry Emitter 注册表（server 模块）
 */
@Slf4j
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
@ApiVersion("26.10.01")
public class DictSseEmitterController {

  /** SSE Emitter 注册表 — 从 server 模块注入 */
  private final DictSseEmitterRegistry emitterRegistry;

  /**
   * SSE 端点 — 字典变更实时推送流。
   *
   * <p>超时设置为 0（由心跳保活维持连接，直到前端主动关闭）。
   *
   * @return SseEmitter 实例
   */
  @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter dictSseStream() {
    SseEmitter emitter = new SseEmitter(0L);
    String sessionId = emitterRegistry.register(emitter);
    log.debug("[DictSseEmitterController] SSE stream opened: sessionId={}", sessionId);
    return emitter;
  }
}
