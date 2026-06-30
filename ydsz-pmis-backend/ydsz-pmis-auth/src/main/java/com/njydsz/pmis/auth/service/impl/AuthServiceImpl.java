package com.njydsz.pmis.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;
import com.njydsz.pmis.auth.service.AuthService;
import com.njydsz.pmis.auth.token.JwtTokenProvider;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String CAPTCHA_KEY_PREFIX = "pmis:captcha:";
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;
    private static final long TOKEN_EXPIRE_HOURS = 8;
    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 7;

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public CaptchaVO generateCaptcha() {
        // TODO: 接入图形验证码库 (例如 easy-captcha)
        String key = IdUtil.fastSimpleUUID();
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 1000));

        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + key, code, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);

        return CaptchaVO.builder()
                .captchaKey(key)
                .captchaImage("data:image/png;base64,...") // 实际项目中生成图片
                .build();
    }

    @Override
    public LoginResultVO login(LoginDTO dto) {
        // 1. 校验验证码（若启用）
        if (StrUtil.isNotBlank(dto.getCaptchaKey())) {
            String stored = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + dto.getCaptchaKey());
            if (stored == null || !stored.equalsIgnoreCase(dto.getCaptchaCode())) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "验证码错误或已过期");
            }
            redisTemplate.delete(CAPTCHA_KEY_PREFIX + dto.getCaptchaKey());
        }

        // 2. TODO: 调用 user 服务校验用户名密码
        // 此处为脚手架，模拟 admin/admin123
        if (!"admin".equals(dto.getUsername()) || !"admin123".equals(dto.getPassword())) {
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT);
        }

        Long userId = 1L;
        String username = dto.getUsername();

        // 3. 生成 Token
        String token = jwtTokenProvider.generateToken(userId, username, TOKEN_EXPIRE_HOURS * 3600);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, REFRESH_TOKEN_EXPIRE_DAYS * 24 * 3600);

        log.info("[Auth] 用户登录成功 userId={} username={}", userId, username);

        return LoginResultVO.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(TOKEN_EXPIRE_HOURS * 3600)
                .build();
    }

    @Override
    public LoginResultVO refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);

        String newToken = jwtTokenProvider.generateToken(userId, username, TOKEN_EXPIRE_HOURS * 3600);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, REFRESH_TOKEN_EXPIRE_DAYS * 24 * 3600);

        return LoginResultVO.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .expiresIn(TOKEN_EXPIRE_HOURS * 3600)
                .build();
    }

    @Override
    public void logout(String userId) {
        if (StrUtil.isNotBlank(userId)) {
            // TODO: 将 Token 加入 Redis 黑名单
            log.info("[Auth] 用户登出 userId={}", userId);
        }
    }
}
