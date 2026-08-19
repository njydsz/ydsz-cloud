package com.njydsz.userinfo.server.service.impl;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.userinfo.domain.dto.AccountUnlockDTO;
import com.njydsz.userinfo.domain.dto.ForgotPasswordDTO;
import com.njydsz.userinfo.domain.dto.SelfRegisterDTO;
import com.njydsz.userinfo.domain.dto.SendVerifyCodeDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.CaptchaService;
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
 * <p><b>DDD 合规：</b>通过 {@link UserAccountRepository} 访问数据，不直接依赖 Mapper。
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

  /** 账号解锁场景验证码类型 */
  private static final String CODE_TYPE_UNLOCK = "UNLOCK";

  /** 默认租户 ID（自助注册场景） */
  private static final String DEFAULT_TENANT_ID = "1";

  private final VerifyCodeService verifyCodeService;
  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final UserPasswordHistoryService passwordHistoryService;
  private final AuthService authService;
  private final CaptchaService captchaService;
  private final UserInfoProperties properties;
  private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;
  private final UserDomainEventPublisher eventPublisher;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean sendVerifyCode(SendVerifyCodeDTO dto) {
    // P0-5: 发送验证码前置图形验证码校验（防短信轰炸）
    captchaService.validate(dto.getCaptchaKey(), dto.getCaptcha());
    verifyCodeService.sendCode(dto.getType(), dto.getTargetType(), dto.getTarget());
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
    // 0. P0-5: 图形验证码校验（防批量注册）
    captchaService.validate(dto.getCaptchaKey(), dto.getCaptcha());

    // 1. 校验验证码（一次性）
    if (!verifyCodeService.verifyCode(CODE_TYPE_REGISTER, dto.getPhone(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }

    // 2. 用户名唯一性校验
    if (userAccountRepository.existsByUsername(dto.getUsername())) {
      throw new BusinessException(UserInfoExceptionCode.USERNAME_DUPLICATE);
    }

    // 3. 密码策略校验
    passwordPolicyValidator.validate(dto.getPassword(), dto.getUsername());

    // 4. 创建用户（启用状态，默认租户）
    String passwordHash = passwordEncoder.encode(dto.getPassword());
    UserAccountCreateDTO createDTO = buildCreateDTO(dto, passwordHash);
    UserAccountVO createdUser = userAccountRepository.create(createDTO);
    log.info("用户自助注册成功: userId={}, username={}", createdUser.getId(), createdUser.getUsername());

    // 5. 记录初始密码到历史（防重用）
    passwordHistoryService.recordPasswordHistory(
        createdUser.getId(), passwordHash, properties.getPasswordHistoryCount());

    // 6. 搜索索引同步
    indexUpsert(createdUser);

    // 7. 发布领域事件
    eventPublisher.publishUserCreated(createdUser);
    return createdUser.getId();
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 用户不存在、手机号不匹配、验证码错误或密码不符合策略时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean forgotPassword(ForgotPasswordDTO dto) {
    // 0. P0-5: 图形验证码校验（防撞库批量找回密码）
    captchaService.validate(dto.getCaptchaKey(), dto.getCaptcha());

    // 1. 查询用户
    Optional<UserAccountVO> userOpt = userAccountRepository.findByUsername(dto.getUsername());
    if (userOpt.isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_USER_NOT_FOUND);
    }
    UserAccountVO userVO = userOpt.get();

    // 2. 手机号匹配校验（防账号探测）
    if (userVO.getPhone() == null || !userVO.getPhone().equals(dto.getPhone())) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_PHONE_MISMATCH);
    }

    // 3. 校验验证码（一次性）
    if (!verifyCodeService.verifyCode(CODE_TYPE_FORGOT_PASSWORD, dto.getPhone(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }

    // 4. 密码策略校验（含历史密码防重用）
    passwordPolicyValidator.validate(
        dto.getNewPassword(), userVO.getUsername(), userVO.getId(), passwordHistoryService);

    // 5. 更新密码并重置失败计数/锁定状态
    String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());
    userAccountRepository.updatePasswordAndResetFailCount(userVO.getId(), newPasswordHash);

    // 6. 记录新密码到历史
    passwordHistoryService.recordPasswordHistory(
        userVO.getId(), newPasswordHash, properties.getPasswordHistoryCount());

    // 7. 驱逐旧会话，强制重新登录
    authService.evictAllSessions(userVO.getId());

    // 8. 搜索索引同步 + 领域事件
    indexUpsert(userVO);
    eventPublisher.publishUserUpdated(userVO);
    log.info("用户找回密码成功: userId={}, username={}", userVO.getId(), userVO.getUsername());
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 用户不存在、验证目标不匹配、验证码错误时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean unlockAccount(AccountUnlockDTO dto) {
    // 0. 图形验证码校验（防暴力破解）
    captchaService.validate(dto.getCaptchaKey(), dto.getCaptcha());

    // 1. 查询用户凭据（含锁定状态）
    Optional<UserAccountCredentialVO> credentialOpt =
        userAccountRepository.findCredentialByUsername(dto.getUsername());
    if (credentialOpt.isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_USER_NOT_FOUND);
    }
    UserAccountCredentialVO credential = credentialOpt.get();

    // 2. 校验账号是否已锁定
    if (!credential.isLocked()) {
      throw new BusinessException(UserInfoExceptionCode.ACCOUNT_NOT_LOCKED);
    }

    // 3. 查询用户基本信息（用于验证目标匹配）
    Optional<UserAccountVO> userOpt = userAccountRepository.findByUsername(dto.getUsername());
    if (userOpt.isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_USER_NOT_FOUND);
    }
    UserAccountVO userVO = userOpt.get();

    // 4. 验证目标匹配校验（手机或邮箱）
    boolean targetMatched = false;
    if ("PHONE".equalsIgnoreCase(dto.getTargetType())) {
      targetMatched = userVO.getPhone() != null && userVO.getPhone().equals(dto.getTarget());
    } else if ("EMAIL".equalsIgnoreCase(dto.getTargetType())) {
      targetMatched = userVO.getEmail() != null && userVO.getEmail().equals(dto.getTarget());
    }
    if (!targetMatched) {
      throw new BusinessException(UserInfoExceptionCode.FORGOT_PASSWORD_PHONE_MISMATCH);
    }

    // 5. 校验验证码（一次性）
    if (!verifyCodeService.verifyCode(CODE_TYPE_UNLOCK, dto.getTarget(), dto.getVerifyCode())) {
      throw new BusinessException(UserInfoExceptionCode.ACCOUNT_UNLOCK_VERIFY_CODE_INVALID);
    }

    // 6. 解锁账号
    int affected = userAccountRepository.unlockAccount(credential.getId());
    if (affected > 0) {
      log.info("账号自助解锁成功: userId={}, username={}", credential.getId(), credential.getUsername());
      // 7. 发布领域事件
      eventPublisher.publishUserUpdated(userVO);
    }
    return affected > 0;
  }

  /**
   * 构建用户创建 DTO。
   *
   * @param dto 自助注册请求 DTO
   * @param passwordHash 密码哈希
   * @return 用户创建 DTO
   */
  private UserAccountCreateDTO buildCreateDTO(SelfRegisterDTO dto, String passwordHash) {
    UserAccountCreateDTO createDTO = new UserAccountCreateDTO();
    createDTO.setUsername(dto.getUsername());
    createDTO.setPassword(passwordHash);
    createDTO.setRealName(dto.getRealName());
    createDTO.setPhone(dto.getPhone());
    createDTO.setEmail(dto.getEmail());
    createDTO.setStatus(EnableStatusEnum.ENABLED);
    createDTO.setTenantId(DEFAULT_TENANT_ID);
    return createDTO;
  }

  /**
   * 同步用户数据到搜索索引。
   *
   * @param userVO 用户账号 VO
   */
  private void indexUpsert(UserAccountVO userVO) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("user", userVO);
    }
  }
}
