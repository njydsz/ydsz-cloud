package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.LoginAuditDO;
import com.njydsz.pmis.audit.mapper.LoginAuditMapper;
import com.njydsz.pmis.common.security.LoginAuditEvent;
import com.njydsz.pmis.common.security.LoginStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * LoginAuditListener 落库监听器测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LoginAuditListener 登录审计测试")
class LoginAuditListenerTest {

    private LoginAuditMapper mapper;
    private LoginAuditListener listener;

    @BeforeEach
    void setUp() {
        mapper = mock(LoginAuditMapper.class);
        listener = new LoginAuditListener(mapper);
    }

    @Test
    @DisplayName("登录成功事件应完整落库")
    void onLogin_success() {
        LoginAuditEvent e = LoginAuditEvent.builder()
                .username("alice")
                .userId(7L)
                .loginIp("127.0.0.1")
                .userAgent("Chrome/120")
                .status(LoginStatus.SUCCESS)
                .traceId("trace-1")
                .tenantId(1L)
                .loginAt(System.currentTimeMillis())
                .mfaUsed(false)
                .mfaSuccess(null)
                .build();
        listener.onLoginAudit(e);

        ArgumentCaptor<LoginAuditDO> cap = ArgumentCaptor.forClass(LoginAuditDO.class);
        verify(mapper).insertLogin(cap.capture());
        LoginAuditDO l = cap.getValue();
        assertThat(l.getUsername()).isEqualTo("alice");
        assertThat(l.getUserId()).isEqualTo(7L);
        assertThat(l.getLoginIp()).isEqualTo("127.0.0.1");
        assertThat(l.getUserAgent()).isEqualTo("Chrome/120");
        assertThat(l.getStatus()).isEqualTo("SUCCESS");
        assertThat(l.getTraceId()).isEqualTo("trace-1");
        assertThat(l.getTenantId()).isEqualTo(1L);
        assertThat(l.getMfaUsed()).isFalse();
        assertThat(l.getCreatedAt()).isNotNull();
        assertThat(l.getLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("登录失败事件 - 携带 failReason 与 MFA 标记")
    void onLogin_failure() {
        LoginAuditEvent e = LoginAuditEvent.builder()
                .username("bob")
                .userId(8L)
                .status(LoginStatus.FAIL_PASSWORD)
                .failReason("密码错误")
                .mfaUsed(true)
                .mfaSuccess(false)
                .loginAt(System.currentTimeMillis())
                .build();
        listener.onLoginAudit(e);

        ArgumentCaptor<LoginAuditDO> cap = ArgumentCaptor.forClass(LoginAuditDO.class);
        verify(mapper).insertLogin(cap.capture());
        LoginAuditDO l = cap.getValue();
        assertThat(l.getStatus()).isEqualTo("FAIL_PASSWORD");
        assertThat(l.getFailReason()).isEqualTo("密码错误");
        assertThat(l.getMfaUsed()).isTrue();
        assertThat(l.getMfaSuccess()).isFalse();
    }

    @Test
    @DisplayName("status 为空时默认 FAIL_OTHER")
    void statusNull_default() {
        LoginAuditEvent e = LoginAuditEvent.builder().username("x").status(null).build();
        listener.onLoginAudit(e);

        ArgumentCaptor<LoginAuditDO> cap = ArgumentCaptor.forClass(LoginAuditDO.class);
        verify(mapper).insertLogin(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(LoginStatus.FAIL_OTHER.name());
    }

    @Test
    @DisplayName("mfaUsed 为 null 时落库为 false")
    void mfaUsedNull() {
        LoginAuditEvent e = LoginAuditEvent.builder().status(LoginStatus.SUCCESS).mfaUsed(null).build();
        listener.onLoginAudit(e);

        ArgumentCaptor<LoginAuditDO> cap = ArgumentCaptor.forClass(LoginAuditDO.class);
        verify(mapper).insertLogin(cap.capture());
        assertThat(cap.getValue().getMfaUsed()).isFalse();
    }

    @Test
    @DisplayName("落库异常应被吞掉，不向外抛出")
    void swallowException() {
        doThrow(new RuntimeException("db down")).when(mapper).insertLogin(any());
        LoginAuditEvent e = LoginAuditEvent.builder().status(LoginStatus.SUCCESS).build();
        listener.onLoginAudit(e); // 不应抛
    }

    @Test
    @DisplayName("loginAt 为 null 时使用当前时间")
    void loginAtNull_now() {
        LoginAuditEvent e = LoginAuditEvent.builder().status(LoginStatus.SUCCESS).loginAt(null).build();
        listener.onLoginAudit(e);

        ArgumentCaptor<LoginAuditDO> cap = ArgumentCaptor.forClass(LoginAuditDO.class);
        verify(mapper).insertLogin(cap.capture());
        assertThat(cap.getValue().getLoginAt()).isNotNull();
    }
}
