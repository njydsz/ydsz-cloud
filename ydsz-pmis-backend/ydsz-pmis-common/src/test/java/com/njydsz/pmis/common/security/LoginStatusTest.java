package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoginStatus 枚举测试
 *
 * @author ydsz-pmis-team
 */
class LoginStatusTest {

    @Test
    @DisplayName("isSuccess 仅 SUCCESS 返回 true")
    void isSuccess() {
        assertThat(LoginStatus.SUCCESS.isSuccess()).isTrue();
        assertThat(LoginStatus.FAIL_PASSWORD.isSuccess()).isFalse();
        assertThat(LoginStatus.FAIL_LOCKED.isSuccess()).isFalse();
        assertThat(LoginStatus.FAIL_MFA.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("code 与 desc")
    void codeAndDesc() {
        for (LoginStatus s : LoginStatus.values()) {
            assertThat(s.getCode()).isNotNull().isNotEmpty();
            assertThat(s.getDesc()).isNotNull().isNotEmpty();
        }
    }
}
