package com.njydsz.pmis.auth.controller;

import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.common.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthController 限流注解测试。
 *
 * <p>P1-11: 验证登录接口已加 {@link RateLimit}（5 次 / 60 秒）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AuthController @RateLimit 注解测试")
class AuthControllerRateLimitTest {

    @Test
    @DisplayName("login 应配置 5 次/60 秒限流")
    void login_shouldBeRateLimited() throws NoSuchMethodException {
        Method login = AuthController.class.getMethod("login",
                LoginDTO.class);
        RateLimit rateLimit = login.getAnnotation(RateLimit.class);

        assertThat(rateLimit).as("login 必须标注 @RateLimit").isNotNull();
        assertThat(rateLimit.key()).isEqualTo("login");
        assertThat(rateLimit.qps()).isEqualTo(5);
        assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    }
}
