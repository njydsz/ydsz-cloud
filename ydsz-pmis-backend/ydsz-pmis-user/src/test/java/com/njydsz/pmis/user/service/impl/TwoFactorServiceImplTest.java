package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TotpUtil;
import com.njydsz.pmis.user.dto.TwoFactorBindResult;
import com.njydsz.pmis.user.entity.User2FADO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.mapper.User2FAMapper;
import com.njydsz.pmis.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TwoFactorServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TwoFactorServiceImpl 双因素认证测试")
class TwoFactorServiceImplTest {

    private User2FAMapper user2FAMapper;
    private UserAccountMapper userAccountMapper;
    private TwoFactorServiceImpl service;

    @BeforeEach
    void setUp() {
        user2FAMapper = mock(User2FAMapper.class);
        userAccountMapper = mock(UserAccountMapper.class);
        service = new TwoFactorServiceImpl(user2FAMapper, userAccountMapper);
    }

    @Test
    @DisplayName("bindTotp 用户不存在抛 USER_NOT_FOUND")
    void bindTotp_userNotFound() {
        when(userAccountMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.bindTotp(1L, "alice"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.USER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("bindTotp 已启用则拒绝重复绑定")
    void bindTotp_alreadyEnabled() {
        when(userAccountMapper.selectById(1L)).thenReturn(user(1L, "alice"));
        User2FADO existing = new User2FADO();
        existing.setEnabled(true);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(existing);
        assertThatThrownBy(() -> service.bindTotp(1L, "alice"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("bindTotp 新建并返回 8 个备份码")
    void bindTotp_newBinding() {
        when(userAccountMapper.selectById(1L)).thenReturn(user(1L, "alice"));
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        when(user2FAMapper.insert(any(User2FADO.class))).thenAnswer(inv -> {
            User2FADO e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        });

        TwoFactorBindResult r = service.bindTotp(1L, "alice");
        assertThat(r).isNotNull();
        assertThat(r.getSecret()).isNotBlank();
        assertThat(r.getSecret().length()).isEqualTo(32);
        assertThat(r.getBackupCodes()).hasSize(8);
        assertThat(r.getOtpAuthUri()).startsWith("otpauth://totp/PMIS:alice?secret=");

        ArgumentCaptor<User2FADO> cap = ArgumentCaptor.forClass(User2FADO.class);
        verify(user2FAMapper).insert(cap.capture());
        User2FADO saved = cap.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getMfaType()).isEqualTo("TOTP");
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getBindingAt()).isNotNull();
    }

    @Test
    @DisplayName("bindTotp 重新绑定未启用的记录：updateById")
    void bindTotp_rebindExisting() {
        when(userAccountMapper.selectById(1L)).thenReturn(user(1L, "alice"));
        User2FADO existing = new User2FADO();
        existing.setId(20L);
        existing.setEnabled(false);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(existing);

        service.bindTotp(1L, "alice");
        verify(user2FAMapper, never()).insert(any(User2FADO.class));
        verify(user2FAMapper, times(1)).updateById(any(User2FADO.class));
    }

    @Test
    @DisplayName("confirmBind 未发起绑定抛 BAD_REQUEST")
    void confirmBind_notInit() {
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.confirmBind(1L, "123456"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("confirmBind OTP 错误返回 false")
    void confirmBind_wrongOtp() {
        User2FADO e = enabled2FA(1L);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        assertThat(service.confirmBind(1L, "000000")).isFalse();
        verify(userAccountMapper, never()).updateById(any(UserAccountDO.class));
    }

    @Test
    @DisplayName("confirmBind OTP 正确启用并更新账户")
    void confirmBind_success() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setSecret(TotpUtil.generateSecret());
        e.setEnabled(false);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        when(userAccountMapper.selectById(1L)).thenReturn(user(1L, "alice"));
        String otp = TotpUtil.generate(e.getSecret());
        assertThat(service.confirmBind(1L, otp)).isTrue();
        ArgumentCaptor<User2FADO> cap = ArgumentCaptor.forClass(User2FADO.class);
        verify(user2FAMapper).updateById(cap.capture());
        assertThat(cap.getValue().getEnabled()).isTrue();
        verify(userAccountMapper, times(1)).updateById(any(UserAccountDO.class));
    }

    @Test
    @DisplayName("verify 未绑定直接返回 false")
    void verify_unbound() {
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        assertThat(service.verify(1L, "123456")).isFalse();
    }

    @Test
    @DisplayName("verify 未启用返回 false")
    void verify_disabled() {
        User2FADO e = new User2FADO();
        e.setEnabled(false);
        e.setSecret(TotpUtil.generateSecret());
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        assertThat(service.verify(1L, "123456")).isFalse();
        verify(user2FAMapper, never()).updateById(any(User2FADO.class));
    }

    @Test
    @DisplayName("verify OTP 错误返回 false")
    void verify_wrong() {
        User2FADO e = enabled2FA(1L);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        assertThat(service.verify(1L, "000000")).isFalse();
    }

    @Test
    @DisplayName("verify OTP 正确返回 true 并更新 lastUsedAt")
    void verify_success() {
        User2FADO e = enabled2FA(1L);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        String otp = TotpUtil.generate(e.getSecret());
        assertThat(service.verify(1L, otp)).isTrue();
        verify(user2FAMapper, times(1)).updateById(any(User2FADO.class));
    }

    @Test
    @DisplayName("verifyBackup 未绑定返回 false")
    void verifyBackup_null() {
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        assertThat(service.verifyBackup(1L, "abc12345")).isFalse();
    }

    @Test
    @DisplayName("verifyBackup 命中并标记已用")
    void verifyBackup_match() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setBackupCodes("abc12345,67890def");
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        assertThat(service.verifyBackup(1L, "ABC12345")).isTrue();
        ArgumentCaptor<User2FADO> cap = ArgumentCaptor.forClass(User2FADO.class);
        verify(user2FAMapper).updateById(cap.capture());
        assertThat(cap.getValue().getBackupCodes()).startsWith("_used_");
        assertThat(cap.getValue().getBackupCodes()).contains("67890def");
    }

    @Test
    @DisplayName("verifyBackup 错误码返回 false")
    void verifyBackup_miss() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setBackupCodes("abc12345,67890def");
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        assertThat(service.verifyBackup(1L, "zzz99999")).isFalse();
        verify(user2FAMapper, never()).updateById(any(User2FADO.class));
    }

    @Test
    @DisplayName("disable 关闭 2FA 并清账户标志")
    void disable() {
        when(userAccountMapper.selectById(1L)).thenReturn(user(1L, "alice"));
        service.disable(1L);
        verify(user2FAMapper, times(1)).disableByUserId(1L);
        verify(userAccountMapper, times(1)).updateById(any(UserAccountDO.class));
    }

    @Test
    @DisplayName("disable 用户不存在不报错")
    void disable_userMissing() {
        when(userAccountMapper.selectById(99L)).thenReturn(null);
        service.disable(99L);
        verify(user2FAMapper, times(1)).disableByUserId(99L);
    }

    @Test
    @DisplayName("find 委托 mapper")
    void find() {
        User2FADO e = enabled2FA(1L);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        assertThat(service.find(1L)).isSameAs(e);
    }

    @Test
    @DisplayName("listBackupCodesMasked 未绑定返回空列表")
    void listMasked_null() {
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        assertThat(service.listBackupCodesMasked(1L)).isEmpty();
    }

    @Test
    @DisplayName("listBackupCodesMasked 对每个码做前后 2 字符脱敏")
    void listMasked_normal() {
        User2FADO e = new User2FADO();
        e.setBackupCodes("abcdefgh,12345678");
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        List<String> masked = service.listBackupCodesMasked(1L);
        assertThat(masked).hasSize(2);
        assertThat(masked.get(0)).isEqualTo("ab****gh");
        assertThat(masked.get(1)).isEqualTo("12****78");
    }

    @Test
    @DisplayName("listBackupCodesMasked 短码统一 ****")
    void listMasked_short() {
        User2FADO e = new User2FADO();
        e.setBackupCodes("ab");
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        List<String> masked = service.listBackupCodesMasked(1L);
        assertThat(masked).containsExactly("****");
    }

    private UserAccountDO user(Long id, String name) {
        UserAccountDO u = new UserAccountDO();
        u.setId(id);
        u.setUsername(name);
        u.setStatus("ENABLED");
        return u;
    }

    private User2FADO enabled2FA(Long userId) {
        User2FADO e = new User2FADO();
        e.setUserId(userId);
        e.setSecret(TotpUtil.generateSecret());
        e.setEnabled(true);
        e.setBackupCodes("aabbccdd,eeffgghh");
        return e;
    }
}
