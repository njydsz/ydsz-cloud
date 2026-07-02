package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserController 限流注解测试。
 *
 * <p>P1-11: 验证创建用户（注册）与重置密码接口已加 {@link RateLimit}（3 次 / 60 秒）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("UserController @RateLimit 注解测试")
class UserControllerRateLimitTest {

    @Test
    @DisplayName("create（注册）应配置 3 次/60 秒限流")
    void create_shouldBeRateLimited() throws NoSuchMethodException {
        Method create = UserController.class.getMethod("create", Map.class);
        RateLimit rateLimit = create.getAnnotation(RateLimit.class);

        assertThat(rateLimit).as("create 必须标注 @RateLimit").isNotNull();
        assertThat(rateLimit.key()).isEqualTo("register");
        assertThat(rateLimit.qps()).isEqualTo(3);
        assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("resetPassword 应配置 3 次/60 秒限流")
    void resetPassword_shouldBeRateLimited() throws NoSuchMethodException {
        Method reset = UserController.class.getMethod("resetPassword", Long.class, String.class);
        RateLimit rateLimit = reset.getAnnotation(RateLimit.class);

        assertThat(rateLimit).as("resetPassword 必须标注 @RateLimit").isNotNull();
        assertThat(rateLimit.key()).isEqualTo("register");
        assertThat(rateLimit.qps()).isEqualTo(3);
        assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    }
}
