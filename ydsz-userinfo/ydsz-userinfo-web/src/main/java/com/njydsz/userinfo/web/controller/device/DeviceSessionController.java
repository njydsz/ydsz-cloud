package com.njydsz.userinfo.web.controller.device;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.server.device.DeviceSessionService;
import com.njydsz.userinfo.server.device.DeviceSessionVO;

/**
 * 设备会话管理 Controller（P3-2）。
 *
 * <p>为已登录用户提供设备管理能力：
 *
 * <ul>
 *   <li>查看当前活跃设备列表（含设备类型、登录 IP、登录时间）</li>
 *   <li>下线指定设备（强制登出）</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/devices}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "设备管理", description = "登录设备查看与下线")
public class DeviceSessionController {

  private final DeviceSessionService deviceSessionService;

  /**
   * 获取当前用户所有活跃设备会话。
   *
   * <p>返回当前登录用户的所有活跃设备信息，包括设备类型、登录 IP、登录时间等。
   * 用户可用于识别异常登录设备。
   *
   * @return 活跃设备会话列表
   */
  @GetMapping
  @RateLimit(resource = "userinfo.devices.list", threshold = 20)
  @Operation(summary = "获取活跃设备列表", description = "查询当前用户所有活跃的设备会话信息")
  public YdszResponse<List<DeviceSessionVO>> listMyDevices() {
    String userId = RequestContext.getUserId();
    return YdszResponse.success(deviceSessionService.listMyDevices(userId));
  }

  /**
   * 下线指定设备会话（强制登出）。
   *
   * <p>吊销目标设备的 access_token 与 refresh_token，该设备的用户需重新登录。
   * 可用于发现异常登录时主动防御。
   *
   * @param sessionId 设备会话 ID（来自 listMyDevices 响应）
   * @return 操作结果
   */
  @Audit(
      module = "设备管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'下线设备: ' + #sessionId")
  @DeleteMapping("/{sessionId}")
  @RateLimit(resource = "userinfo.devices.revoke", threshold = 10)
  @Operation(summary = "下线设备", description = "强制登出指定设备的会话（需二次确认）")
  public YdszResponse<Boolean> revokeDevice(@PathVariable String sessionId) {
    String userId = RequestContext.getUserId();
    return YdszResponse.success(deviceSessionService.revokeDevice(userId, sessionId));
  }
}
