package com.njydsz.userinfo.server.service;

import com.njydsz.userinfo.domain.dto.AccountUnlockDTO;
import com.njydsz.userinfo.domain.dto.ForgotPasswordDTO;
import com.njydsz.userinfo.domain.dto.SelfRegisterDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;

/**
 * 自助服务接口（无需登录）。
 *
 * <p>提供用户自助注册、找回密码、发送验证码、账号解锁能力。业务逻辑统一收敛到 Service 层，
 * 保证与 {@link UserAccountService} 的创建/改密链路一致（事务、密码历史、索引同步、领域事件、会话驱逐、审计）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SelfRegisterDTO 注册请求 DTO
 * @see ForgotPasswordDTO 找回密码请求 DTO
 * @see AccountUnlockDTO 账号解锁请求 DTO
 */
public interface SelfServiceService {

  /**
   * 发送验证码。
   *
   * @param dto 发送请求（type + targetType + target）
   * @return 是否发送成功
   */
  boolean sendVerifyCode(SendVerifyCodeDTO dto);

  /**
   * 用户自助注册。
   *
   * <p>完整链路：验证码校验 → 用户名唯一性校验 → 密码策略校验 → BCrypt 加密 → 写入用户 → 记录密码历史 →
   * 搜索索引同步 → 发布领域事件。
   *
   * @param dto 注册请求
   * @return 新创建的用户 ID
   */
  String register(SelfRegisterDTO dto);

  /**
   * 找回密码。
   *
   * <p>完整链路：用户查询 → 手机号匹配 → 验证码校验 → 密码策略校验（含历史）→ BCrypt 更新 →
   * 重置失败计数/锁定 → 记录密码历史 → 驱逐旧会话 → 发布领域事件。
   *
   * @param dto 找回密码请求
   * @return 是否成功
   */
  boolean forgotPassword(ForgotPasswordDTO dto);

  /**
   * 账号自助解锁。
   *
   * <p>用户因登录失败次数过多被锁定后，通过手机/邮箱验证码验证身份后自助解锁。
   *
   * <p>完整链路：图形验证码校验 → 用户查询 → 验证目标匹配 → 验证码校验 → 解锁账号 → 发布领域事件。
   *
   * @param dto 解锁请求
   * @return 是否成功
   */
  boolean unlockAccount(AccountUnlockDTO dto);
}
