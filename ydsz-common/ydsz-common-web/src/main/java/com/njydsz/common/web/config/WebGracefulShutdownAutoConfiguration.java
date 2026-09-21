package com.njydsz.common.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;

import com.njydsz.common.safe.config.SecurityHeaderProperties;

/**
 * Web 端优雅停机自动配置
 *
 * <p>提供优雅停机（Graceful Shutdown）的可观测性支持：
 *
 * <ul>
 *   <li>监听 {@link ContextClosedEvent}，记录停机开始时间点；
 *   <li>监听 {@link WebServerInitializedEvent}，记录启动完成时间点及组件状态；
 *   <li>监听 {@link ApplicationFailedEvent}，记录应用启动失败事件；
 *   <li>支持通过 {@code ydsz.web.shutdown.log-enabled=false} 关闭停机日志。
 * </ul>
 *
 * <p><b>启动日志：</b>服务就绪时输出一行组件状态摘要，便于运维快速确认当前模块配置：
 *
 * <pre>
 * [YDSZ] Platform: WEB | Port: 9001 | CORS: enabled | Trace: enabled | Security Headers: enabled | Session: none | UA: enabled
 * </pre>
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
@AutoConfigureAfter(WebMvcConfiguration.class)
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
   * @param corsProvider Web CORS 配置属性（可选，依赖 WebMvcConfiguration 上下文）
   * @param traceProvider Web Trace 配置属性（可选）
   * @param securityHeaderProvider 安全响应头配置属性（可选，依赖 safe 模块）
   * @return ShutdownEventListener 实例
   */
  @Bean
  public ShutdownEventListener shutdownEventListener(
      ObjectProvider<WebCorsProperties> corsProvider,
      ObjectProvider<WebTraceProperties> traceProvider,
      ObjectProvider<SecurityHeaderProperties> securityHeaderProvider) {
    return new ShutdownEventListener(corsProvider.getIfAvailable(), traceProvider.getIfAvailable(),
        securityHeaderProvider.getIfAvailable());
  }

  /**
   * 优雅停机事件监听器
   *
   * <p>统一处理 WebServer 启动完成、Context 关闭、应用启动失败等事件。 通过实现 {@link ApplicationListener} 而非
   * {@code @EventListener} 注解， 确保事件在 Bean 初始化阶段也能被捕获。
   */
  public static class ShutdownEventListener implements ApplicationListener<ApplicationEvent> {

    private final WebCorsProperties corsProperties;
    private final WebTraceProperties traceProperties;
    private final SecurityHeaderProperties securityHeaderProperties;

    ShutdownEventListener(WebCorsProperties corsProperties,
        WebTraceProperties traceProperties,
        SecurityHeaderProperties securityHeaderProperties) {
      this.corsProperties = corsProperties;
      this.traceProperties = traceProperties;
      this.securityHeaderProperties = securityHeaderProperties;
    }

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
      ApplicationContext ctx = event.getApplicationContext();

      // 构建组件状态摘要
      String corsStatus = corsProperties != null && corsProperties.isEnabled() ? "enabled" : "disabled";
      String traceStatus = traceProperties != null && traceProperties.isEnabled() ? "enabled" : "disabled";
      String securityStatus = securityHeaderProperties != null && securityHeaderProperties.isEnabled()
          ? "enabled" : "disabled";
      String sessionStatus = isSessionRedisEnabled(ctx) ? "redis" : "none";
      String uaStatus = isUserAgentAnalyzerAvailable(ctx) ? "enabled" : "disabled";

      LOG.info(
          "[YDSZ] Platform: WEB | Port: {} | Context: {} | "
              + "CORS: {} | Trace: {} | Security Headers: {} | Session: {} | UA: {}",
          port, contextPath, corsStatus, traceStatus, securityStatus, sessionStatus, uaStatus);
    }

    private void handleContextClosed() {
      LOG.info(
          "[Shutdown] 应用上下文开始关闭，等待在飞请求完成"
              + "（受 spring.lifecycle.timeout-per-shutdown-phase 限制）");
    }

    private void handleApplicationFailed(ApplicationFailedEvent event) {
      LOG.error("[Shutdown] 应用启动失败", event.getException());
    }

    /**
     * 运行时探测 Redis Session 是否启用（通过检查 SessionRepository Bean 存在性）。
     *
     * @param context Spring 应用上下文
     * @return {@code true} 表示 Redis Session 可用
     */
    private boolean isSessionRedisEnabled(ApplicationContext context) {
      try {
        Class<?> repoClass = Class.forName("org.springframework.session.SessionRepository");
        String[] names = context.getBeanNamesForType(repoClass, false, false);
        return names.length > 0;
      } catch (ClassNotFoundException e) {
        return false;
      }
    }

    /**
     * 运行时探测 User-Agent 解析器是否可用（通过 ObjectProvider 获取 Bean）。
     *
     * @param context Spring 应用上下文
     * @return {@code true} 表示 UA 解析器已装配
     */
    private boolean isUserAgentAnalyzerAvailable(ApplicationContext context) {
      try {
        String[] names = context.getBeanNamesForType(
            Class.forName("nl.basjes.parse.useragent.UserAgentAnalyzer"), false, false);
        return names.length > 0;
      } catch (ClassNotFoundException e) {
        return false;
      }
    }
  }
}
