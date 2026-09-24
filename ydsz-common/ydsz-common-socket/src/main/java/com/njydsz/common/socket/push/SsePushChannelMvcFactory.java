package com.njydsz.common.socket.push;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;

/**
 * SSE 通道工厂的 Spring MVC 实现（创建 {@link SsePushChannelMvcAdapter} 实例）。
 *
 * <p>自动管理心跳共享调度器（daemon 线程，corePoolSize=2）。业务模块注入此 Factory
 * 后每次 HTTP SSE 请求调用 {@link #create()} 创建新通道。
 *
 * <p><b>装配条件</b>：classpath 中存在 {@code SseEmitter}（即 spring-webmvc），
 * 通过 {@code @ConditionalOnClass(SseEmitter.class)} 控制自动配置。
 *
 * @author ydsz-team
 * @since 26.09.24
 */
public class SsePushChannelMvcFactory implements SsePushChannelFactory, DisposableBean {

  /** 心跳共享调度器 */
  private final ScheduledExecutorService heartbeatScheduler =
      Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sse-heartbeat-" + UUID.randomUUID().toString().substring(0, 8));
        t.setDaemon(true);
        return t;
      });

  @Override
  public SsePushChannel create() {
    return new SsePushChannelMvcAdapter(generateSessionId(), heartbeatScheduler);
  }

  @Override
  public SsePushChannel create(long timeoutMillis) {
    return new SsePushChannelMvcAdapter(generateSessionId(), timeoutMillis, heartbeatScheduler, 15L);
  }

  @Override
  public void destroy() {
    heartbeatScheduler.shutdown();
    try {
      if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        heartbeatScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      heartbeatScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private String generateSessionId() {
    return "sse-" + UUID.randomUUID().toString().substring(0, 12);
  }
}
