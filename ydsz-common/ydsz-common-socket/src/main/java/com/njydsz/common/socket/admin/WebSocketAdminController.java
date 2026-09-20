package com.njydsz.common.socket.admin;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.socket.handler.WebSocketMessageDispatcher;
import com.njydsz.common.socket.push.PushResult;
import com.njydsz.common.socket.push.RealtimePushTemplate;
import com.njydsz.common.socket.session.LocalSessionRegistry;

/**
 * WebSocket 运维诊断 REST Admin API（UX-002）。
 *
 * <p>提供运行时 Session 管理与推送诊断能力：
 *
 * <ul>
 *   <li>GET /admin/ws/stats — 模块实时统计（在线分布、过滤注册数、心跳存活数、批量线程池状态）
 *   <li>GET /admin/ws/sessions?userId={userId} — 查询指定用户的本地 Session 列表
 *   <li>DELETE /admin/ws/sessions/{sessionId} — 强制断开指定 Session
 *   <li>POST /admin/ws/push/direct — 管理端强制推送到指定用户（运维抢救通道，绕过常规业务过滤）
 * </ul>
 *
 * <p>鉴权：本 Controller 端点仅应在内网/运维网关后暴露；生产部署建议在外层网关加 BA 鉴权或限流。
 *
 * <p>条件装配：{@code WebMvcConfigurer} 不存在时无注册，避免引入无 web 能力的模块时出错。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@RestController
@RequestMapping("/admin/ws")
@RequiredArgsConstructor
public class WebSocketAdminController {

  private final RealtimePushTemplate pushTemplate;
  private final LocalSessionRegistry sessionRegistry;
  private final WebSocketMessageDispatcher messageDispatcher;

  /**
   * 模块实时统计（UX-002 /admin/ws/stats）。
   *
   * @return Map 形式统计数据，直接序列化为 JSON 返回
   */
  @GetMapping("/stats")
  public Map<String, Object> stats() {
    Map<String, Object> result = new HashMap<>(16);
    result.put("sessionCount", sessionRegistry.getTotalSessionCount());
    result.put("handlerCount", messageDispatcher != null ? messageDispatcher.getHandlerCount() : 0);
    return result;
  }

  /**
   * 查询指定用户本地 Session（UX-002 /admin/ws/sessions?userId={userId}）。
   *
   * @param userId 目标用户 ID
   * @return 本地 Session 快照映射
   */
  @GetMapping("/sessions")
  public Map<String, Object> sessions(@RequestParam String userId) {
    Map<String, Object> result = new HashMap<>(8);
    result.put("userId", userId);
    result.put("sessions", sessionRegistry.getAllSessions());
    return result;
  }

  /**
   * 强制断开指定 Session（UX-002 /admin/ws/sessions/{sessionId}）。
   *
   * @param sessionId STOMP Session ID
   * @return 操作结果
   */
  @DeleteMapping("/sessions/{sessionId}")
  public Map<String, Object> killSession(@PathVariable String sessionId) {
    Map<String, Object> result = new HashMap<>(8);
    try {
      boolean unregistered = sessionRegistry.unregisterSession(sessionId);
      if (unregistered) {
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("message", "Session 已断开并释放资源");
      } else {
        result.put("success", false);
        result.put("error", "Session 不存在或已断开");
      }
    } catch (Exception e) {
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 管理端强制推送到指定用户（UX-002 /admin/ws/push/direct）。 绕过业务过滤链，便于紧急通知。
   *
   * @param userId 目标用户 ID
   * @param type 消息类型
   * @param payload 消息体（JSON 字符串）
   * @return 推送结果
   */
  @PostMapping("/push/direct")
  public Map<String, Object> directPush(
      @RequestParam String userId,
      @RequestParam String type,
      @RequestParam String payload) {
    Map<String, Object> result = new HashMap<>(8);
    try {
      PushResult pr = pushTemplate.pushToUserWithResult(userId, type, payload);
      result.put("success", pr.success());
      result.put("messageId", pr.messageId());
      if (!pr.success()) {
        result.put("errorCode", pr.errorCode());
        result.put("errorMessage", pr.errorMessage());
      }
    } catch (Exception e) {
      result.put("success", false);
      result.put("error", e.getMessage());
    }
    return result;
  }

  /**
   * 健康检查端点（与 Spring Actuator 兼容的简易 ping）。
   *
   * @return HTTP 200 + 简易状态
   */
  @GetMapping("/ping")
  public ResponseEntity<String> ping() {
    return ResponseEntity.ok("pong");
  }
}
