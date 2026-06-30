package com.njydsz.pmis.auth.service.impl;

import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;
import com.njydsz.pmis.auth.feign.UserAuthClient;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.user.dto.LoginContextDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单元测试
 */
@DisplayName("AuthServiceImpl 认证服务测试")
class AuthServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private JwtTokenProvider jwtTokenProvider;
    private UserAuthClient userAuthClient;
    private AuthServiceImpl service;

    private LoginContextDTO mockContext(String username) {
        return LoginContextDTO.builder()
                .userId(1L)
                .username(username)
                .password("e10adc3949ba59abbe56e057f20f883e") // MD5("123456")
                .salt("")
                .status("ENABLED")
                .realName("管理员")
                .departmentId(1L)
                .departmentName("研发中心")
                .levelCode("L5")
                .levelName("P5")
                .dataScope("DEPT")
                .roles(List.of("ADMIN"))
                .permissions(List.of("system:user:list"))
                .loginFailCount(0)
                .lockedUntil(0L)
                .build();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        jwtTokenProvider = mock(JwtTokenProvider.class);
        when(jwtTokenProvider.generateToken(eq(1L), eq("admin"), any(Long.class)))
                .thenReturn("access-token-xxx");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), any(Long.class)))
                .thenReturn("refresh-token-xxx");
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUserId(anyString())).thenReturn(1L);
        when(jwtTokenProvider.getUsername(anyString())).thenReturn("admin");

        userAuthClient = mock(UserAuthClient.class);
        when(userAuthClient.getLoginContextByUsername("admin"))
                .thenReturn(R.ok(mockContext("admin")));

        service = new AuthServiceImpl(redisTemplate, jwtTokenProvider, userAuthClient);
    }

    @Test
    @DisplayName("generateCaptcha 应返回 CaptchaVO 并写入 Redis")
    void generateCaptcha() {
        CaptchaVO vo = service.generateCaptcha();
        assertThat(vo).isNotNull();
        assertThat(vo.getCaptchaKey()).isNotBlank();
        assertThat(vo.getCaptchaImage()).isNotBlank();
    }

    @Test
    @DisplayName("login 正确凭证应返回 token")
    void login_success() {
        // 使用 service 暴露的测试钩子跳过图形验证码
        service.setCaptchaRequired(false);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");

        LoginResultVO result = service.login(dto);
        assertThat(result.getToken()).isEqualTo("access-token-xxx");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-xxx");
        assertThat(result.getExpiresIn()).isPositive();
    }

    @Test
    @DisplayName("login 错误密码应抛 PASSWORD_INCORRECT")
    void login_wrongPassword() {
        service.setCaptchaRequired(false);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("wrong");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.PASSWORD_INCORRECT.getCode());
    }

    @Test
    @DisplayName("login 验证码错误应抛 BAD_REQUEST")
    void login_wrongCaptcha() {
        when(valueOps.get(anyString())).thenReturn("1234");

        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");
        dto.setCaptchaKey("ck-1");
        dto.setCaptchaCode("0000");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("login 验证码正确应放行")
    void login_captchaOk() {
        when(valueOps.get(anyString())).thenReturn("1234");

        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");
        dto.setCaptchaKey("ck-1");
        dto.setCaptchaCode("1234");

        LoginResultVO r = service.login(dto);
        assertThat(r.getToken()).isEqualTo("access-token-xxx");
    }

    @Test
    @DisplayName("refresh Token 有效应返回新 Token")
    void refresh_success() {
        LoginResultVO r = service.refresh("refresh-token-xxx");
        assertThat(r.getToken()).isEqualTo("access-token-xxx");
    }

    @Test
    @DisplayName("refresh Token 无效应抛 TOKEN_INVALID")
    void refresh_invalid() {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);
        assertThatThrownBy(() -> service.refresh("bad"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("logout 不应抛异常")
    void logout() {
        service.logout("1");
        service.logout(null);
        service.logout("");
    }

    @Test
    @DisplayName("构造期设置 Redis 过期时间")
    void captchaExpire() {
        service.generateCaptcha();
        org.mockito.Mockito.verify(valueOps)
                .set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), eq(TimeUnit.MINUTES));
    }
}
