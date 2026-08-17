package com.njydsz.userinfo.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.userinfo.domain.dto.ForgotPasswordDTO;
import com.njydsz.userinfo.domain.dto.SelfRegisterDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.PasswordPolicyValidator;
import com.njydsz.userinfo.server.auth.UserPasswordHistoryService;
import com.njydsz.userinfo.server.auth.VerifyCodeService;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.service.SelfServiceService;

/**
 * 自助服务实现。
 *
 * <p>实现 {@link SelfServiceService}，将注册/找回密码的完整业务链路收敛到 Service 层：
 * 与 {@link UserAccountServiceImpl#create} / {@code resetPassword} 保持一致的关注点（事务、密码历史、
 * 搜索索引同步、领域事件、会话驱逐），修复原 Controller 直连 Mapper 导致的逻辑重复与缺失
 * （P0-4：缺事务、密码历史、索引同步、事件、审计、改密后会话驱逐）。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see VerifyCodeService 验证码服务
 * @see PasswordPolicyValidator 密码策略校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceServiceImpl implements SelfServiceService {

  /** 注册场景验证码类型 */
  private static final String CODE_TYPE_REGISTER = "REGISTER";

  /** 找回密码场景验证码类型 */
  private static final String CODE_TYPE_FORGOT_PASSWORD = "FORGOT_PASSWORD";

  private final VerifyCodeService verifyCodeService;
  private final UserAccountMapper userAccountMapper;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final UserPasswordHistoryService passwordHistoryService;
  private final AuthService authService;
  private final UserInfoProperties properties;
  private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;
  private final UserDomainEventPublisher eventPublisher;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean sendVerifyCode(SendVerifyCodeDTO dto) {
    verifyCodeService.sendCode(dto.getType(), dto.getPhone());
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 验证码错误、用户名重复或密码不符合策略时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String register(SelfRegisterDTO dto) {
    // 1. 校验验证码（一次性）
    if (!verifyCodeService.verifyCode(CODE_TYPE_REGISTER, dto.getPhone(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }

    // 2. 用户名唯一性校验
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, dto.getUsername());
    if (userAccountMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(UserInfoExceptionCode.USERNAME_DUPLICATE);
    }

    // 3. 密码策略校验
    passwordPolicyValidator.validate(dto.getPassword(), dto.getUsername());

    // 4. 创建用户（启用状态，默认租户）
    String passwordHash = passwordEncoder.encode(dto.getPassword());
    UserAccountDO user = new UserAccountDO();
    user.setUsername(dto.getUsername());
    user.setRealName(dto.getRealName());
    user.setPassword(passwordHash);
    user.setPhone(dto.getPhone());
    user.setEmail(dto.getEmail());
    user.setStatusEnum(EnableStatusEnum.ENABLED);
    user.setLoginFailCount(0);
    user.setTenantId("1");
    userAccountMapper.insert(user);
    log.info("用户自助注册成功: userId={}, username={}", user.getId(), user.getUsername());

    // 5. 记录初始密码到历史（防重用）
    passwordHistoryService.recordPasswordHistory(
        user.getId(), passwordHash, properties.getPasswordHistoryCount());

    // 6. 搜索索引同步
    indexUpsert(user);

    // 7. 发布领域事件
    eventPublisher.publishUserCreated(user);
    return user.getId();
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 用户不存在、手机号不匹配、验证码错误或密码不符合策略时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean forgotPassword(ForgotPasswordDTO dto) {
    // 1. 查询用户
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, dto.getUsername());
    UserAccountDO user = userAccountMapper.selectOne(wrapper);
    if (user == null) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_USER_NOT_FOUND);
    }

    // 2. 手机号匹配校验（防账号探测）
    if (user.getPhone() == null || !user.getPhone().equals(dto.getPhone())) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_PHONE_MISMATCH);
    }

    // 3. 校验验证码（一次性）
    if (!verifyCodeService.verifyCode(CODE_TYPE_FORGOT_PASSWORD, dto.getPhone(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }

    // 4. 密码策略校验（含历史密码防重用）
    passwordPolicyValidator.validate(
        dto.getNewPassword(), user.getUsername(), user.getId(), passwordHistoryService);

    // 5. 更新密码并重置失败计数/锁定状态
    String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());
    user.setPassword(newPasswordHash);
    user.setLoginFailCount(0);
    user.setLockedUntil(null);
    userAccountMapper.updateById(user);

    // 6. 记录新密码到历史
    passwordHistoryService.recordPasswordHistory(
        user.getId(), newPasswordHash, properties.getPasswordHistoryCount());

    // 7. 驱逐旧会话，强制重新登录
    authService.evictAllSessions(user.getId());

    // 8. 搜索索引同步 + 领域事件
    indexUpsert(user);
    eventPublisher.publishUserUpdated(user);
    log.info("用户找回密码成功: userId={}, username={}", user.getId(), user.getUsername());
    return true;
  }

  private void indexUpsert(UserAccountDO entity) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("user", entity);
    }
  }
}
