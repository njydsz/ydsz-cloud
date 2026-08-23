package com.njydsz.userinfo.web.controller.selfservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.userinfo.domain.dto.AccountUnlockDTO;
import com.njydsz.userinfo.domain.dto.ForgotPasswordDTO;
import com.njydsz.userinfo.domain.dto.SelfRegisterDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.server.service.SelfServiceService;

/**
 * 自助服务 Controller（无需登录）。
 *
 * <p>提供用户自助注册、找回密码、发送验证码等能力，<b>无需认证</b>即可访问。 所有接口均有验证码 + 限流防护，防止恶意滥用。
 * 业务逻辑统一委托 {@link SelfServiceService}，保证与主链路一致的事务/密码历史/索引同步/事件/会话驱逐（P0-4）。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>发送验证码：同手机号 60 秒限流
 *   <li>注册/找回密码：验证码校验 + 密码策略校验 + 写审计日志
 *   <li>限流：全局限流 5 QPS
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/self-service")
@RequiredArgsConstructor
@Tag(name = "自助服务", description = "用户自助注册、找回密码（无需登录）")
public class SelfServiceController {

  private final SelfServiceService selfServiceService;

  /**
   * 发送验证码。
   *
   * <p>向指定手机号发送短信验证码（REGISTER / FORGOT_PASSWORD 场景）。
   *
   * @param dto 发送验证码请求
   * @return 是否发送成功
   */
  @PostMapping("/send-verify-code")
  @Operation(summary = "发送验证码")
  @RateLimit(
      resource = "userinfo.selfservice.sendCode",
      threshold = 3,
      windowMillis = 60000,
      dimension = RateLimitDimension.IP)
  public YdszResponse<Boolean> sendVerifyCode(@Valid @RequestBody SendVerifyCodeDTO dto) {
    return YdszResponse.success(selfServiceService.sendVerifyCode(dto));
  }

  /**
   * 自助注册。
   *
   * <p>用户通过手机号验证码 + 基本信息完成注册，注册成功后返回用户 ID。
   *
   * @param dto 注册请求
   * @return 新创建的用户 ID
   */
  @Audit(
      module = "自助服务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'用户自助注册: ' + #dto.username",
      excludeParams = {"password"})
  @Idempotent(key = "ydsz:userinfo:selfservice:register:#{#dto.username}", ttlSeconds = 10)
  @PostMapping("/register")
  @Operation(summary = "自助注册")
  @RateLimit(
      resource = "userinfo.selfservice.register",
      threshold = 3,
      windowMillis = 60000,
      dimension = RateLimitDimension.IP)
  public YdszResponse<String> register(@Valid @RequestBody SelfRegisterDTO dto) {
    return YdszResponse.success(selfServiceService.register(dto));
  }

  /**
   * 找回密码。
   *
   * <p>通过用户名 + 手机号 + 验证码验证身份后重置新密码，并驱逐该用户全部旧会话（强制重新登录）。
   *
   * @param dto 找回密码请求
   * @return 是否成功
   */
  @Audit(
      module = "自助服务",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'用户找回密码: ' + #dto.username",
      excludeParams = {"newPassword", "verifyCode"})
  @Idempotent(
      key = "ydsz:userinfo:selfservice:forgotPassword:#{#dto.username}",
      ttlSeconds = 10)
  @PostMapping("/forgot-password")
  @Operation(summary = "找回密码")
  @RateLimit(
      resource = "userinfo.selfservice.forgotPassword",
      threshold = 3,
      windowMillis = 60000,
      dimension = RateLimitDimension.IP)
  public YdszResponse<Boolean> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
    return YdszResponse.success(selfServiceService.forgotPassword(dto));
  }

  /**
   * 账号自助解锁。
   *
   * <p>用户因登录失败次数过多被锁定后，通过手机/邮箱验证码验证身份后自助解锁。
   *
   * @param dto 解锁请求
   * @return 是否成功
   */
  @Audit(
      module = "自助服务",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'用户自助解锁: ' + #dto.username",
      excludeParams = {"verifyCode"})
  @Idempotent(
      key = "ydsz:userinfo:selfservice:unlock:#{#dto.username}",
      ttlSeconds = 10)
  @PostMapping("/unlock")
  @Operation(summary = "账号自助解锁")
  @RateLimit(
      resource = "userinfo.selfservice.unlock",
      threshold = 3,
      windowMillis = 60000,
      dimension = RateLimitDimension.IP)
  public YdszResponse<Boolean> unlockAccount(@Valid @RequestBody AccountUnlockDTO dto) {
    return YdszResponse.success(selfServiceService.unlockAccount(dto));
  }
}
