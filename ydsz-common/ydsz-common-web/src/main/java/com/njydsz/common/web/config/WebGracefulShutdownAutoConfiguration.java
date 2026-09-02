package com.njydsz.common.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Web 端优雅停机自动配置
 *
 * <p>提供优雅停机（Graceful Shutdown）的可观测性支持：
 *
 * <ul>
 *   <li>监听 {@link ContextClosedEvent}，记录停机开始时间点；
 *   <li>监听 {@link WebServerInitializedEvent}，记录启动完成时间点；
 *   <li>监听 {@link ApplicationFailedEvent}，记录应用启动失败事件；
 *   <li>支持通过 {@code ydsz.web.shutdown.log-enabled=false} 关闭停机日志。
 * </ul>
 *
 * <p><b>使用前提：</b>本配置仅提供停机可观测性，真正的「优雅停机」需要应用层显式启用：
 *
 * <pre>
 * server:
 *   shutdown: graceful                # 启用 Spring Boot 优雅停机
 * spring:
 *   lifecycle:
 *     timeout-per-shutdown-phase: 30s # 单个生命周期阶段的超时时间
 * </pre>
 *
 * <p><b>原理：</b>启用 {@code server.shutdown=graceful} 后，Spring Boot 的 Web 服务器 （Tomcat / Jetty /
 * Undertow）会拒绝新请求并等待在飞请求（in-flight requests）完成。 本配置的 {@link ShutdownEventListener}
 * 会在停机开始时输出日志，便于运维观测。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "ydsz.web.shutdown",
    name = "log-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WebGracefulShutdownAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(WebGracefulShutdownAutoConfiguration.class);

  /**
   * 注册优雅停机事件监听器 Bean。
   *
   * <p>监听 Spring 容器的关闭事件、WebServer 启动完成事件、应用启动失败事件， 输出可观测性日志。
   *
   * @return ShutdownEventListener 实例
   */
  @Bean
  public ShutdownEventListener shutdownEventListener() {
    return new ShutdownEventListener();
  }

  /**
   * 优雅停机事件监听器
   *
   * <p>统一处理 WebServer 启动完成、Context 关闭、应用启动失败等事件。 通过实现 {@link ApplicationListener} 而非
   * {@code @EventListener} 注解， 确保事件在 Bean 初始化阶段也能被捕获。
   */
  public static class ShutdownEventListener implements ApplicationListener<ApplicationEvent> {

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
      if (event instanceof WebServerInitializedEvent initializedEvent) {
        handleWebServerInitialized(initializedEvent);
      } else if (event instanceof ContextClosedEvent) {
        handleContextClosed();
      } else if (event instanceof ApplicationFailedEvent failedEvent) {
        handleApplicationFailed(failedEvent);
      }
    }

    private void handleWebServerInitialized(WebServerInitializedEvent event) {
      int port = event.getWebServer().getPort();
      String contextPath =
          event
              .getApplicationContext()
              .getEnvironment()
              .getProperty("server.servlet.context-path", "/");
      LOG.info("[Shutdown] Web 服务已就绪 | port={} | contextPath={}", port, contextPath);
    }

    private void handleContextClosed() {
      LOG.info(
          "[Shutdown] 应用上下文开始关闭，等待在飞请求完成" + "（受 spring.lifecycle.timeout-per-shutdown-phase 限制）");
    }

    private void handleApplicationFailed(ApplicationFailedEvent event) {
      LOG.error("[Shutdown] 应用启动失败", event.getException());
    }
  }
}
