package com.njydsz.userinfo.web.controller.sse;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.userinfo.server.sse.SseEmitterRegistry;

/**
 * 认证事件 SSE 实时推送端点（P3-1）。
 *
 * <p>为已登录用户提供 Server-Sent Events 连接，实时推送认证事件：
 *
 * <ul>
 *   <li>登录成功/失败（含设备信息）</li>
 *   <li>MFA 触发/验证</li>
 *   <li>会话驱逐（强制下线通知）</li>
 *   <li>账号锁定/解锁</li>
 *   <li>密码修改</li>
 *   <li>账号封禁/解封</li>
 * </ul>
 *
 * <p><b>连接管理：</b>
 *
 * <ul>
 *   <li>Emitter 超时时间通过 {@code ydsz.userinfo.sse.timeout} 配置（默认 30 分钟）</li>
 *   <li>支持单用户多设备（多 Tab/浏览器）同时订阅</li>
 *   <li>连接断开后自动清理资源</li>
 * </ul>
 *
 * <p><b>使用方式（前端示例）：</b>
 *
 * <pre>{@code
 * const eventSource = new EventSource('/api/v1/auth/events/stream', { withCredentials: true });
 * eventSource.addEventListener('auth.login.success', (e) => console.log(JSON.parse(e.data)));
 * eventSource.addEventListener('auth.session.evicted', (e) => redirectToLogin());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/events")
@RequiredArgsConstructor
@Tag(name = "认证事件 SSE", description = "认证事件 Server-Sent Events 实时推送")
public class AuthEventSseController {

  private final SseEmitterRegistry emitterRegistry;

  /**
   * 建立 SSE 连接，订阅当前登录用户的认证事件流。
   *
   * <p>首次连接会发送 {@code connected} 事件作为心跳确认。前端可通过该事件确认订阅成功。
   *
   * @param request HTTP 请求（用于连接异常检测）
   * @return SseEmitter 实例
   */
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "订阅认证事件流",
      description = "建立 SSE 连接，实时接收当前用户的认证事件（登录、MFA、会话驱逐等）")
  public SseEmitter streamEvents(HttpServletRequest request) {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new IllegalStateException("未登录用户无法建立 SSE 连接");
    }

    // 创建 SSE Emitter，超时 30 分钟
    SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

    // 注册连接
    emitterRegistry.register(userId, emitter);

    // 发送连接确认事件
    try {
      emitter.send(SseEmitter.event()
          .name("connected")
          .data(Map.of(
              "message", "SSE 连接已建立",
              "timestamp", java.time.LocalDateTime.now().toString()),
              MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      log.debug("SSE 连接确认发送失败: userId={}", userId);
      emitterRegistry.remove(userId, emitter);
      return emitter;
    }

    // 连接完成/超时/错误时清理
    emitter.onCompletion(() -> {
      emitterRegistry.remove(userId, emitter);
      log.debug("SSE 连接正常关闭: userId={}", userId);
    });
    emitter.onTimeout(() -> {
      emitterRegistry.remove(userId, emitter);
      log.debug("SSE 连接超时关闭: userId={}", userId);
    });
    emitter.onError(ex -> {
      emitterRegistry.remove(userId, emitter);
      log.debug("SSE 连接异常关闭: userId={}, error={}", userId, ex.getMessage());
    });

    log.info("SSE 连接建立: userId={}, 当前连接数={}", userId,
        emitterRegistry.getConnectionCount(userId));
    return emitter;
  }
}
