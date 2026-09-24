package com.njydsz.common.socket.push;

/**
 * SSE 通道工厂（Spring MVC SseEmitter 适配）。
 *
 * <p>封装 SseEmitter 的创建、超时配置、异常回调注册、以及到 SsePushChannel 适配的全流程。
 * 业务 Controller 注入此 Factory 并调用 {@link #create()} 创建新的 SSE 连接通道。
 *
 * <p>业务模块通过 {@code ObjectProvider<SsePushChannelFactory>} 可选注入——classpath 无
 * spring-webmvc 时工厂不可用，业务方降级为空实现或直接提示不支持。
 *
 * <p><b>典型用法：</b>
 *
 * <pre>{@code
 * @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 * public SseEmitter streamChat(@RequestBody ChatRequest request) {
 *   SsePushChannel channel = sseFactory.create(120_000L);
 *   virtualExecutor.submit(() -> {
 *     try {
 *       llmService.stream(request, chunk -> channel.sendEvent("delta", chunk));
 *       channel.complete();
 *     } catch (Exception e) {
 *       channel.completeWithError(e);
 *     }
 *   });
 *   return channel.getEmitter();
 *   // 或直接返回 SseEmitter + 通过 channel 管理生命周期（由适配实现决定）
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.24
 * @see SsePushChannel 通道接口
 */
public interface SsePushChannelFactory {

  /**
   * 创建一个 SSE 推送通道。
   *
   * <p>默认超时 120 秒，心跳间隔 15 秒。
   *
   * @return 新创建的 SSE 通道（未启动，需业务方注入逻辑后触发 complete()）
   */
  SsePushChannel create();

  /**
   * 创建一个 SSE 推送通道（自定义超时）。
   *
   * @param timeoutMillis 通道超时毫秒数（建议 30_000 ~ 300_000）
   * @return 新创建的 SSE 通道
   */
  SsePushChannel create(long timeoutMillis);
}
