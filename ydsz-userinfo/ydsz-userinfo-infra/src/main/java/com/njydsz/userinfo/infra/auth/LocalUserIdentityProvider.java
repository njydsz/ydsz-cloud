package com.njydsz.userinfo.infra.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.auth.UserIdentityProvider;
import com.njydsz.userinfo.domain.enums.IdentityProviderType;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.common.exception.custom.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地用户身份提供者实现。
 *
 * <p>处理 LOCAL 类型用户的身份验证，使用 BCrypt 密码校验逻辑。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalUserIdentityProvider implements UserIdentityProvider {

  private final UserAccountRepository userAccountRepository;

  @Override
  public IdentityProviderType getType() {
    return IdentityProviderType.LOCAL;
  }

  @Override
  public Map<String, String> authenticate(String username, String credentials) {
    if (username == null || credentials == null) {
      return null;
    }

    try {
      UserAccountCredentialVO credential = userAccountRepository
          .findCredentialByUsername(username)
          .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

      if (credential.getPassword() != null
          && BCrypt.checkpw(credentials, credential.getPassword())) {
        Map<String, String> result = new HashMap<>();
        result.put("userId", credential.getId());
        result.put("username", credential.getUsername());
        result.put("tenantId", credential.getTenantId());
        result.put("provider", IdentityProviderType.LOCAL.getCode());
        return result;
      }

      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("本地用户认证异常: username={}", username, e);
      return null;
    }
  }

  @Override
  public boolean supports(String userIdentityProvider) {
    return IdentityProviderType.LOCAL.getCode().equals(userIdentityProvider);
  }
}
