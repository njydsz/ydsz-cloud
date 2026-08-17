package com.njydsz.userinfo.web.controller.selfservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.ForgotPasswordDTO;
import com.njydsz.userinfo.domain.dto.SelfRegisterDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.server.auth.PasswordPolicyValidator;
import com.njydsz.userinfo.server.auth.VerifyCodeService;

/**
 * 自助服务 Controller（无需登录）。
 *
 * <p>提供用户自助注册、找回密码、发送验证码等能力，<b>无需认证</b>即可访问。 所有接口均有图形验证码 + 限流防护，防止恶意滥用。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>发送验证码：同手机号 60 秒限流
 *   <li>注册/找回密码：验证码校验 + 密码策略校验
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

  private final VerifyCodeService verifyCodeService;
  private final UserAccountMapper userAccountMapper;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;

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
  @RateLimit(resource = "userinfo.selfservice.sendCode", threshold = 5)
  public BaseResponse<Boolean> sendVerifyCode(@Valid @RequestBody SendVerifyCodeDTO dto) {
    verifyCodeService.sendCode(dto.getType(), dto.getPhone());
    return BaseResponse.success(true);
  }

  /**
   * 自助注册。
   *
   * <p>用户通过手机号验证码 + 基本信息完成注册。 注册成功后返回用户 ID。
   *
   * @param dto 注册请求
   * @return 新创建的用户 ID
   */
  @PostMapping("/register")
  @Operation(summary = "自助注册")
  @RateLimit(resource = "userinfo.selfservice.register", threshold = 5)
  public BaseResponse<String> register(@Valid @RequestBody SelfRegisterDTO dto) {
    // 校验验证码
    if (!verifyCodeService.verifyCode("REGISTER", dto.getPhone(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }

    // 校验用户名唯一性
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, dto.getUsername());
    UserAccount existingUser = userAccountMapper.selectOne(wrapper);
    if (existingUser != null) {
      throw new BusinessException(UserInfoExceptionCode.USERNAME_DUPLICATE);
    }

    // 校验密码策略（密码不能与用户名相同）
    passwordPolicyValidator.validate(dto.getPassword(), dto.getUsername());

    // 创建用户
    UserAccount user = new UserAccount();
    user.setUsername(dto.getUsername());
    user.setRealName(dto.getRealName());
    user.setPassword(passwordEncoder.encode(dto.getPassword()));
    user.setPhone(dto.getPhone());
    user.setEmail(dto.getEmail());
    user.setStatusEnum(EnableStatusEnum.ENABLED);

    userAccountMapper.insert(user);

    log.info("用户自助注册成功: userId={}, username={}", user.getId(), user.getUsername());
    return BaseResponse.success(user.getId());
  }

  /**
   * 找回密码。
   *
   * <p>通过用户名 + 手机号 + 验证码验证身份后重置新密码。
   *
   * @param dto 找回密码请求
   * @return 是否成功
   */
  @PostMapping("/forgot-password")
  @Operation(summary = "找回密码")
  @RateLimit(resource = "userinfo.selfservice.forgotPassword", threshold = 5)
  public BaseResponse<Boolean> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
    // 查询用户
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, dto.getUsername());
    UserAccount user = userAccountMapper.selectOne(wrapper);
    if (user == null) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_USER_NOT_FOUND);
    }

    // 校验手机号是否匹配
    if (user.getPhone() == null || !user.getPhone().equals(dto.getPhone())) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_PHONE_MISMATCH);
    }

    // 校验验证码
    if (!verifyCodeService.verifyCode("FORGOT_PASSWORD", dto.getPhone(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }

    // 校验密码策略
    passwordPolicyValidator.validate(dto.getNewPassword(), dto.getUsername());

    // 更新密码
    user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
    userAccountMapper.updateById(user);

    log.info("用户找回密码成功: userId={}, username={}", user.getId(), user.getUsername());
    return BaseResponse.success(true);
  }
}
