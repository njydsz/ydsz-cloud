package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.userinfo.dto.CaptchaVO;
import com.njydsz.pmis.userinfo.dto.LoginDTO;
import com.njydsz.pmis.userinfo.dto.LoginResultVO;
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
    @DisplayName("登录成功 - 历史 MD5 密码应触发惰性升级为 BCrypt")
    @SuppressWarnings("deprecation")
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
            // 历史 MD5 格式：isBCryptFormat 返回 false，走 verifyPassword 路径
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("encryptedPwd")).thenReturn(false);
            cryptoUtil.when(() -> CryptoUtil.verifyPassword(anyString(), anyString(), anyString())).thenReturn(true);
            // 惰性升级：返回模拟的 BCrypt 哈希
            cryptoUtil.when(() -> CryptoUtil.hashPasswordBCrypt(anyString())).thenReturn("$2a$12$mockedBcryptHash");
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
            // 验证调用了惰性升级
            verify(userAccountService).upgradePasswordHash(eq(1L), eq("$2a$12$mockedBcryptHash"));
        }
    }

    @Test
    @DisplayName("登录成功 - BCrypt 密码不应触发惰性升级")
    void login_withBCryptPassword_shouldNotUpgrade() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO user = new UserAccountDO();
            user.setId(2L);
            user.setUsername("bcryptuser");
            user.setPassword("$2a$12$realBcryptHash");
            user.setSalt("");
            user.setStatus("ENABLED");
            user.setLoginFailCount(0);
            user.setLockedUntil(null);

            when(userAccountService.findByUsername("bcryptuser")).thenReturn(user);
            when(roleService.listByUserId(2L)).thenReturn(Collections.emptyList());
            when(permissionService.listPermCodesByUserId(2L)).thenReturn(Collections.emptyList());
            // BCrypt 格式：isBCryptFormat 返回 true，走 verifyPasswordBCrypt 路径
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$realBcryptHash")).thenReturn(true);
            cryptoUtil.when(() -> CryptoUtil.verifyPasswordBCrypt(anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyList(), anyList(), anyLong()))
                    .thenReturn("access-token-bcrypt");
            when(jwtTokenProvider.generateRefreshToken(anyLong(), anyLong()))
                    .thenReturn("refresh-token-bcrypt");

            LoginDTO dto = new LoginDTO();
            dto.setUsername("bcryptuser");
            dto.setPassword("correctPwd");

            LoginResultVO result = authService.login(dto);

            assertNotNull(result);
            assertEquals("access-token-bcrypt", result.getToken());
            // BCrypt 路径不应触发升级
            verify(userAccountService, never()).upgradePasswordHash(anyLong(), anyString());
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
        assertEquals(30001, ex.getCode());
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
        assertEquals(30003, ex.getCode());
    }

    @Test
    @DisplayName("登录时密码错误抛出异常")
    @SuppressWarnings("deprecation")
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
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("encryptedPwd")).thenReturn(false);
            cryptoUtil.when(() -> CryptoUtil.verifyPassword(anyString(), anyString(), anyString())).thenReturn(false);

            LoginDTO dto = new LoginDTO();
            dto.setUsername("admin");
            dto.setPassword("wrongPassword");

            BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
            assertEquals(30002, ex.getCode());
        }
    }

    @Test
    @DisplayName("登录时 BCrypt 密码错误抛出异常")
    void login_bcryptWrongPassword_shouldThrowException() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO user = new UserAccountDO();
            user.setId(3L);
            user.setUsername("bcryptuser");
            user.setPassword("$2a$12$realBcryptHash");
            user.setSalt("");
            user.setStatus("ENABLED");
            user.setLoginFailCount(0);
            user.setLockedUntil(null);

            when(userAccountService.findByUsername("bcryptuser")).thenReturn(user);
            when(roleService.listByUserId(3L)).thenReturn(Collections.emptyList());
            when(permissionService.listPermCodesByUserId(3L)).thenReturn(Collections.emptyList());
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$realBcryptHash")).thenReturn(true);
            cryptoUtil.when(() -> CryptoUtil.verifyPasswordBCrypt(anyString(), anyString())).thenReturn(false);

            LoginDTO dto = new LoginDTO();
            dto.setUsername("bcryptuser");
            dto.setPassword("wrongPwd");

            BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
            assertEquals(30002, ex.getCode());
            // 校验失败不应触发升级
            verify(userAccountService, never()).upgradePasswordHash(anyLong(), anyString());
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