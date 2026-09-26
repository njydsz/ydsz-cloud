package com.njydsz.userinfo.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.server.config.CrossDomainSsoProperties;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * AuthServiceImpl 单元测试（纯 Mockito 模式，MockitoAnnotations.openMocks 启动）。
 *
 * <p>覆盖登录核心分支：
 *
 * <ul>
 *   <li>登录成功 — 返回 LoginVO，验证 credentialVerifier.verify 被调用</li>
 *   <li>用户不存在 — AccountStatusGuard 抛出 BusinessException</li>
 *   <li>密码错误 — CredentialVerifier 抛出 BusinessException</li>
 *   <li>HIGH 风险 + MFA — mfaService.validateLoginMfa 被调用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
class AuthServiceImplTest {

  @Mock private UserAccountRepository userAccountRepository;
  @Mock private TokenService tokenService;
  @Mock private TokenBlacklistService tokenBlacklistService;
  @Mock private CaptchaService captchaService;
  @Mock private RiskScoringService riskScoringService;
  @Mock private MfaService mfaService;
  @Mock private LoginAttemptCounterService loginAttemptCounterService;
  @Mock private UserDomainEventPublisher userDomainEventPublisher;
  @Mock private LoginHistoryService loginHistoryService;
  @Mock private UserInfoMetrics userInfoMetrics;
  @Mock private UserInfoProperties properties;
  @Mock private AccountStatusGuard accountStatusGuard;
  @Mock private CredentialVerifier credentialVerifier;
  @Mock private SessionManager sessionManager;
  @Mock private RoleCacheService roleCacheService;
  @Mock private CrossDomainTokenService crossDomainTokenService;
  @Mock private CrossDomainSsoProperties ssoProperties;
  @Mock private RememberMeService rememberMeService;
  @Mock private DbRolePermissionLoader rolePermissionLoader;

  @InjectMocks private AuthServiceImpl authService;

  private AutoCloseable mocks;

  /** 默认风险评分：SAFE（不触发验证码/MFA） */
  private RiskScoringService.RiskScore safeRisk() {
    return new RiskScoringService.RiskScore(10, RiskScoringService.RiskLevel.SAFE, List.of());
  }

  /** 高风险评分：HIGH（触发 MFA，不拒绝登录） */
  private RiskScoringService.RiskScore highRisk() {
    return new RiskScoringService.RiskScore(70, RiskScoringService.RiskLevel.HIGH, List.of("IP风险(30)"));
  }

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    // 登录主流程横切关注点默认值
    lenient().when(loginHistoryService.isIpBlocked(anyString())).thenReturn(false);
    lenient().when(loginAttemptCounterService.getIpFailCount(anyString())).thenReturn(0);
    lenient().when(loginAttemptCounterService.isNewDevice(anyString(), anyString()))
        .thenReturn(false);
    lenient().when(properties.isCaptchaEnabled()).thenReturn(false);
    lenient().when(properties.getRiskWindowSeconds()).thenReturn(3600L);
    lenient().when(properties.getTokenTtlSeconds()).thenReturn(7200L);
    lenient().when(ssoProperties.isEnabled()).thenReturn(false);
    lenient().doNothing().when(userInfoMetrics).stopTimer(any());
    lenient().doNothing().when(userInfoMetrics).recordLoginSuccess();
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Nested
  @DisplayName("login — 登录主流程")
  class Login {

    @Test
    @DisplayName("正常登录应返回 token DTO 并校验密码")
    void shouldReturnTokenDtoAndVerifyPassword() {
      // Given: 用户存在、密码正确、SAFE 风险、无验证码/MFA 拦截
      when(riskScoringService.evaluateRisk(
              eq("admin"), eq("127.0.0.1"), eq("test-agent"), eq(0), eq(false)))
          .thenReturn(safeRisk());

      UserAccountCredentialVO credential = new UserAccountCredentialVO();
      credential.setId("user-1");
      credential.setUsername("admin");
      credential.setPassword("$2a$10$hashed-pwd");
      credential.setStatus(1);
      when(accountStatusGuard.findValidUser("admin", "127.0.0.1", "test-agent"))
          .thenReturn(credential);

      doNothing().when(credentialVerifier).verify(any(), anyString(), anyString(), anyString());

      RoleVO role = new RoleVO();
      role.setRoleCode("ADMIN");
      role.setRoleName("管理员");
      lenient().when(roleCacheService.loadUserRoles("user-1")).thenReturn(List.of(role));
      lenient().when(tokenService.issueAccessToken(any(), anyString()))
          .thenReturn("access-token-xyz");
      lenient().when(tokenService.issueRefreshToken(any(), anyString()))
          .thenReturn("refresh-token-xyz");
      lenient().doNothing().when(sessionManager).createSession(any());
      lenient().doNothing().when(userAccountRepository).resetLoginSuccess(anyString(), anyString());
      lenient().doNothing().when(loginHistoryService)
          .recordLoginAttempt(any(), anyString(), anyString(), anyString());
      lenient().doNothing().when(loginAttemptCounterService)
          .markDeviceSeen(anyString(), anyString(), anyInt());
      lenient().doNothing().when(userDomainEventPublisher)
          .publishLoginSuccess(anyString(), anyString(), anyString(), anyString(), anyString());

      LoginDTO dto = new LoginDTO();
      dto.setUsername("admin");
      dto.setPassword("plain-pwd");
      dto.setLoginIp("127.0.0.1");
      dto.setUserAgent("test-agent");

      // When
      LoginVO result = authService.login(dto, null);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getAccessToken()).isEqualTo("access-token-xyz");
      assertThat(result.getRefreshToken()).isEqualTo("refresh-token-xyz");
      assertThat(result.getTokenType()).isEqualTo("Bearer");
      assertThat(result.getUserInfo()).isNotNull();
      assertThat(result.getUserInfo().getUserId()).isEqualTo("user-1");
      assertThat(result.getUserInfo().getUsername()).isEqualTo("admin");
      // 验证密码校验被调用
      verify(credentialVerifier).verify(credential, "plain-pwd", "127.0.0.1", "test-agent");
    }

    @Test
    @DisplayName("用户不存在时应抛出 BusinessException")
    void shouldThrowWhenUserNotFound() {
      when(accountStatusGuard.findValidUser("ghost", "127.0.0.1", "test-agent"))
          .thenThrow(new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT));

      LoginDTO dto = new LoginDTO();
      dto.setUsername("ghost");
      dto.setPassword("any-pwd");
      dto.setLoginIp("127.0.0.1");
      dto.setUserAgent("test-agent");

      assertThatThrownBy(() -> authService.login(dto, null))
          .isInstanceOf(BusinessException.class);

      verify(credentialVerifier, never()).verify(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("密码错误时应抛出 BusinessException")
    void shouldThrowWhenPasswordIncorrect() {
      // Given: 用户存在但密码不匹配
      when(riskScoringService.evaluateRisk(
              eq("admin"), eq("127.0.0.1"), eq("test-agent"), eq(0), eq(false)))
          .thenReturn(safeRisk());

      UserAccountCredentialVO credential = new UserAccountCredentialVO();
      credential.setId("user-1");
      credential.setUsername("admin");
      credential.setPassword("$2a$10$hashed-pwd");
      credential.setStatus(1);
      when(accountStatusGuard.findValidUser("admin", "127.0.0.1", "test-agent"))
          .thenReturn(credential);

      doThrow(new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT))
          .when(credentialVerifier).verify(any(), anyString(), anyString(), anyString());

      LoginDTO dto = new LoginDTO();
      dto.setUsername("admin");
      dto.setPassword("wrong-pwd");
      dto.setLoginIp("127.0.0.1");
      dto.setUserAgent("test-agent");

      assertThatThrownBy(() -> authService.login(dto, null))
          .isInstanceOf(BusinessException.class);

      verify(credentialVerifier).verify(credential, "wrong-pwd", "127.0.0.1", "test-agent");
    }

    @Test
    @DisplayName("HIGH 风险时应触发 MFA 校验")
    void shouldTriggerMfaWhenHighRisk() {
      // Given: 风险评分 HIGH → validateMfaIfRequired → mfaService.validateLoginMfa
      when(riskScoringService.evaluateRisk(
              eq("admin"), eq("127.0.0.1"), eq("test-agent"), eq(0), eq(false)))
          .thenReturn(highRisk());

      UserAccountCredentialVO credential = new UserAccountCredentialVO();
      credential.setId("user-1");
      credential.setUsername("admin");
      credential.setPassword("$2a$10$hashed-pwd");
      credential.setStatus(1);
      credential.setPhone("1*********0");
      when(accountStatusGuard.findValidUser("admin", "127.0.0.1", "test-agent"))
          .thenReturn(credential);

      doNothing().when(credentialVerifier).verify(any(), anyString(), anyString(), anyString());
      doNothing().when(mfaService).validateLoginMfa(any(), anyString());

      RoleVO role = new RoleVO();
      role.setRoleCode("ADMIN");
      role.setRoleName("管理员");
      lenient().when(roleCacheService.loadUserRoles("user-1")).thenReturn(List.of(role));
      lenient().when(tokenService.issueAccessToken(any(), anyString()))
          .thenReturn("access-token-xyz");
      lenient().when(tokenService.issueRefreshToken(any(), anyString()))
          .thenReturn("refresh-token-xyz");
      lenient().doNothing().when(sessionManager).createSession(any());
      lenient().doNothing().when(userAccountRepository).resetLoginSuccess(anyString(), anyString());
      lenient().doNothing().when(loginHistoryService)
          .recordLoginAttempt(any(), anyString(), anyString(), anyString());
      lenient().doNothing().when(loginAttemptCounterService)
          .markDeviceSeen(anyString(), anyString(), anyInt());
      lenient().doNothing().when(userDomainEventPublisher)
          .publishLoginSuccess(anyString(), anyString(), anyString(), anyString(), anyString());

      LoginDTO dto = new LoginDTO();
      dto.setUsername("admin");
      dto.setPassword("plain-pwd");
      dto.setMfaCode("123456");
      dto.setLoginIp("127.0.0.1");
      dto.setUserAgent("test-agent");

      // When
      authService.login(dto, null);

      // Then: MFA 校验被调用
      verify(mfaService).validateLoginMfa(credential, "123456");
    }
  }
}
