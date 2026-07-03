package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.userinfo.dto.CaptchaVO;
import com.njydsz.pmis.userinfo.dto.LoginDTO;
import com.njydsz.pmis.userinfo.dto.LoginResultVO;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.service.PermissionService;
import com.njydsz.pmis.userinfo.service.RoleService;
import com.njydsz.pmis.userinfo.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务测试")
class AuthServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private RoleService roleService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        authService.setCaptchaRequired(false);
    }

    @Test
    @DisplayName("生成图形验证码成功")
    void generateCaptcha_shouldReturnCaptchaVO() {
        CaptchaVO result = authService.generateCaptcha();
        assertNotNull(result);
        assertNotNull(result.getCaptchaKey());
        assertNotNull(result.getCaptchaImage());
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("登录成功")
    void login_shouldReturnToken() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO user = new UserAccountDO();
            user.setId(1L);
            user.setUsername("admin");
            user.setPassword("encryptedPwd");
            user.setSalt("salt123");
            user.setStatus("ENABLED");
            user.setLoginFailCount(0);
            user.setLockedUntil(null);

            when(userAccountService.findByUsername("admin")).thenReturn(user);
            when(roleService.listByUserId(1L)).thenReturn(Collections.emptyList());
            when(permissionService.listPermCodesByUserId(1L)).thenReturn(Collections.emptyList());
            cryptoUtil.when(() -> CryptoUtil.verifyPassword(anyString(), anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyList(), anyList(), anyLong()))
                    .thenReturn("access-token-xxx");
            when(jwtTokenProvider.generateRefreshToken(anyLong(), anyLong()))
                    .thenReturn("refresh-token-xxx");

            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("admin123");

            LoginResultVO result = authService.login(dto);

            assertNotNull(result);
            assertEquals("access-token-xxx", result.getToken());
            assertEquals("refresh-token-xxx", result.getRefreshToken());
            assertNotNull(result.getExpiresIn());
        }
    }

    @Test
    @DisplayName("登录时用户不存在抛出异常")
    void login_userNotFound_shouldThrowException() {
        when(userAccountService.findByUsername("unknown")).thenReturn(null);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("unknown");
        dto.setPassword("password");

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
        assertEquals(BizErrorCode.USER_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("登录时用户已禁用抛出异常")
    void login_userDisabled_shouldThrowException() {
        UserAccountDO user = new UserAccountDO();
        user.setId(1L);
        user.setUsername("disabledUser");
        user.setPassword("pwd");
        user.setSalt("salt");
        user.setStatus("DISABLED");
        user.setLoginFailCount(0);
        user.setLockedUntil(null);

        when(userAccountService.findByUsername("disabledUser")).thenReturn(user);
        when(roleService.listByUserId(1L)).thenReturn(Collections.emptyList());
        when(permissionService.listPermCodesByUserId(1L)).thenReturn(Collections.emptyList());

        LoginDTO dto = new LoginDTO();
        dto.setUsername("disabledUser");
        dto.setPassword("password");

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
        assertEquals(BizErrorCode.USER_DISABLED, ex.getCode());
    }

    @Test
    @DisplayName("登录时密码错误抛出异常")
    void login_wrongPassword_shouldThrowException() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO user = new UserAccountDO();
            user.setId(1L);
            user.setUsername("admin");
            user.setPassword("encryptedPwd");
            user.setSalt("salt123");
            user.setStatus("ENABLED");
            user.setLoginFailCount(0);
            user.setLockedUntil(null);

            when(userAccountService.findByUsername("admin")).thenReturn(user);
            when(roleService.listByUserId(1L)).thenReturn(Collections.emptyList());
            when(permissionService.listPermCodesByUserId(1L)).thenReturn(Collections.emptyList());
            cryptoUtil.when(() -> CryptoUtil.verifyPassword(anyString(), anyString(), anyString())).thenReturn(false);
            when(redisTemplate.delete(anyString())).thenReturn(true);

            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("wrongPassword");

            BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
            assertEquals(BizErrorCode.PASSWORD_INCORRECT, ex.getCode());
        }
    }

    @Test
    @DisplayName("Token黑名单校验")
    void isTokenBlacklisted_shouldReturnCorrectly() {
        when(redisTemplate.hasKey(eq("pmis:token:blacklist:test-token"))).thenReturn(true);
        when(redisTemplate.hasKey(eq("pmis:token:blacklist:valid-token"))).thenReturn(false);

        assertTrue(authService.isTokenBlacklisted("test-token"));
        assertFalse(authService.isTokenBlacklisted("valid-token"));
    }

    @Test
    @DisplayName("登出操作")
    void logout_shouldNotThrowException() {
        assertDoesNotThrow(() -> authService.logout("1"));
        assertDoesNotThrow(() -> authService.logout(null));
    }
}