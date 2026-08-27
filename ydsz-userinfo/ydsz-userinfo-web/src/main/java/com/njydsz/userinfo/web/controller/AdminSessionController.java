package com.njydsz.userinfo.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.annotation.SensitiveOperation;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.dto.UserBanRequestDTO;
import com.njydsz.userinfo.domain.enums.BanType;
import com.njydsz.userinfo.domain.vo.BanInfoVO;
import com.njydsz.userinfo.domain.vo.UserSessionStatistics;
import com.njydsz.userinfo.domain.vo.UserSessionVO;
import com.njydsz.userinfo.server.auth.UserBanService;
import com.njydsz.userinfo.server.auth.UserSessionAdminService;

/**
 * 管理员会话治理 Controller。
 *
 * <p>提供账号封禁与解封、在线会话查看与强制下线能力，仅限具备管理员权限的用户访问。
 *
 * <p><b>接口路径：</b>{@code /api/v1/admin/users/{userId}/*} 和 {@code /api/v1/admin/sessions/*}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>封禁用户（临时/永久）：{@code POST /api/v1/admin/users/{userId}/ban}</li>
 *   <li>解封用户：{@code POST /api/v1/admin/users/{userId}/unban}</li>
 *   <li>查询封禁信息：{@code GET /api/v1/admin/users/{userId}/ban-info}</li>
 *   <li>查询用户会话：{@code GET /api/v1/admin/users/{userId}/sessions}</li>
 *   <li>强制下线指定会话：{@code DELETE /api/v1/admin/users/{userId}/sessions/{accessToken}}</li>
 *   <li>查询所有在线会话：{@code GET /api/v1/admin/sessions}</li>
 *   <li>会话统计：{@code GET /api/v1/admin/sessions/statistics}</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link SensitiveOperation} 二次认证（需先通过密码确认）</li>
 *   <li>写接口启用 {@link Idempotent} 防重复提交</li>
 *   <li>写接口启用 {@link Audit} 审计日志</li>
 *   <li>写接口启用 {@link RateLimit} 接口级限流</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserBanService 账号封禁服务
 * @see UserSessionAdminService 会话治理服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "管理员封禁与会话治理", description = "账号封禁/解封、在线会话管理与强制下线")
@ApiVersion("1")
public class AdminSessionController {

  private final UserBanService userBanService;
  private final UserSessionAdminService userSessionAdminService;

  /**
   * 封禁用户。
   *
   * <p>支持临时封禁（指定到期时间）和永久封禁。封禁后自动驱逐用户全部活跃会话。
   *
   * @param userId 目标用户 ID
   * @param dto 封禁请求参数（banType/banReason/banExpireAt）
   * @return 操作结果
   */
  @SensitiveOperation("封禁用户")
  @Audit(
      module = "管理员封禁治理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'封禁用户: ' + #userId + ', 类型: ' + #dto.banType + ', 原因: ' + #dto.banReason")
  @Idempotent(key = "ydsz:userinfo:AdminSessionController:ban:lock:#{#userId}", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.admin.ban", threshold = 10)
  @PostMapping("/users/{userId}/ban")
  @Operation(summary = "封禁用户", description = "封禁指定用户，支持临时封禁和永久封禁")
  public YdszResponse<Boolean> banUser(
      @PathVariable String userId, @Valid @RequestBody UserBanRequestDTO dto) {
    userBanService.ban(
        userId,
        BanType.valueOf(dto.getBanType()),
        dto.getBanReason(),
        dto.getBanExpireAt());
    return YdszResponse.success(true);
  }

  /**
   * 解封用户。
   *
   * <p>清除封禁字段，恢复用户登录权限（不恢复生命周期状态）。
   *
   * @param userId 目标用户 ID
   * @return 操作结果
   */
  @SensitiveOperation("解封用户")
  @Audit(
      module = "管理员封禁治理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'解封用户: ' + #userId")
  @Idempotent(key = "ydsz:userinfo:AdminSessionController:unban:lock:#{#userId}", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.admin.unban", threshold = 10)
  @PostMapping("/users/{userId}/unban")
  @Operation(summary = "解封用户", description = "解除用户的封禁状态")
  public YdszResponse<Boolean> unbanUser(@PathVariable String userId) {
    userBanService.unban(userId);
    return YdszResponse.success(true);
  }

  /**
   * 查询账号封禁信息。
   *
   * @param userId 用户 ID
   * @return 封禁信息 VO
   */
  @GetMapping("/users/{userId}/ban-info")
  @Operation(summary = "查询封禁信息", description = "查询指定用户的封禁状态详情")
  public YdszResponse<BanInfoVO> getBanInfo(@PathVariable String userId) {
    return YdszResponse.success(userBanService.getBanInfo(userId));
  }

  /**
   * 查询用户在线会话列表。
   *
   * @param userId 用户 ID
   * @return 会话 VO 列表
   */
  @GetMapping("/users/{userId}/sessions")
  @Operation(summary = "查询用户会话", description = "查询指定用户的活跃会话列表")
  public YdszResponse<List<UserSessionVO>> getUserSessions(@PathVariable String userId) {
    return YdszResponse.success(userSessionAdminService.listUserSessions(userId));
  }

  /**
   * 强制下线用户指定会话。
   *
   * @param userId 用户 ID
   * @param accessToken 会话 accessToken
   * @return 操作结果
   */
  @SensitiveOperation("强制下线会话")
  @Audit(
      module = "管理员会话治理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'强制下线会话: userId=' + #userId")
  @Idempotent(key = "ydsz:userinfo:AdminSessionController:forceLogout:lock:#{#userId}", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.admin.forceLogout", threshold = 10)
  @DeleteMapping("/users/{userId}/sessions/{accessToken}")
  @Operation(summary = "强制下线指定会话", description = "吊销用户的指定会话 Token")
  public YdszResponse<Boolean> forceLogout(
      @PathVariable String userId, @PathVariable String accessToken) {
    userSessionAdminService.forceLogout(userId, accessToken);
    return YdszResponse.success(true);
  }

  /**
   * 查询全平台在线会话（分页）。
   *
   * <p>当前实现受限于缺乏全局会话索引，返回空列表。
   *
   * @param page 页码（默认 1）
   * @param size 每页大小（默认 20）
   * @return 会话 VO 分页列表
   */
  @GetMapping("/sessions")
  @Operation(summary = "查询所有在线会话", description = "查询全平台活跃会话（分页），当前需全局会话索引支持")
  public YdszResponse<List<UserSessionVO>> getAllActiveSessions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return YdszResponse.success(userSessionAdminService.listAllActiveSessions(page, size));
  }

  /**
   * 查询会话统计信息。
   *
   * @return 会话统计 record
   */
  @GetMapping("/sessions/statistics")
  @Operation(summary = "查询会话统计", description = "查询全会话统计：总数/活跃用户数/分端分布")
  public YdszResponse<UserSessionStatistics> getSessionStatistics() {
    return YdszResponse.success(userSessionAdminService.getSessionStatistics());
  }
}
