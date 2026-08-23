package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.auth.UserIdentityProvider;
import com.njydsz.userinfo.domain.enums.IdentityProviderType;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;

/**
 * 本地用户身份提供者实现。
 *
 * <p>处理 LOCAL 类型用户的身份验证，通过 {@link PasswordEncoder}（BCrypt）完成密码校验。
 * 认证成功后返回用户 ID、用户名、租户 ID 等身份属性。
 *
 * <p><b>安全机制：</b>
 *
 * <ul>
 *   <li>使用 {@link PasswordEncoder#matches} 进行恒定时间比较，防止计时攻击</li>
 *   <li>账号不存在与密码错误使用不同错误码，便于审计日志区分</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalUserIdentityProvider implements UserIdentityProvider {

  /** 密码编码器（BCrypt，由 UserInfoConfiguration 注册） */
  private final PasswordEncoder passwordEncoder;

  /** 账号数据仓储 */
  private final UserAccountRepository userAccountRepository;

  @Override
  public IdentityProviderType getType() {
    return IdentityProviderType.LOCAL;
  }

  @Override
  public Map<String, String> authenticate(String username, String credentials) {
    if (username == null || username.isBlank() || credentials == null) {
      return Collections.emptyMap();
    }

    UserAccountCredentialVO credential = userAccountRepository
        .findCredentialByUsername(username)
        .orElseThrow(() -> {
          log.warn("本地用户认证失败[用户不存在]: username={}", username);
          return new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
        });

    if (passwordEncoder.matches(credentials, credential.getPassword())) {
      Map<String, String> result = new HashMap<>();
      result.put("userId", credential.getId());
      result.put("username", credential.getUsername());
      result.put("tenantId", credential.getTenantId());
      result.put("provider", IdentityProviderType.LOCAL.getCode());
      return result;
    }

    log.warn("本地用户认证失败[密码错误]: username={}", username);
    throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
  }

  @Override
  public boolean supports(String userIdentityProvider) {
    return IdentityProviderType.LOCAL.getCode().equals(userIdentityProvider);
  }
}
