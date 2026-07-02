package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.aspect.RequireReAuthAspect;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.user.dto.ReAuthRequest;
import com.njydsz.pmis.user.dto.ReAuthResult;
import com.njydsz.pmis.user.entity.User2FADO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.mapper.User2FAMapper;
import com.njydsz.pmis.user.mapper.UserAccountMapper;
import com.njydsz.pmis.user.service.TwoFactorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReAuthServiceImpl 二次认证 token 颁发测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReAuthServiceImpl 二次认证颁发测试")
class ReAuthServiceImplTest {

    private RequireReAuthAspect aspect;
    private UserAccountMapper userAccountMapper;
    private User2FAMapper user2FAMapper;
    private TwoFactorService twoFactorService;
    private ReAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        aspect = mock(RequireReAuthAspect.class);
        userAccountMapper = mock(UserAccountMapper.class);
        user2FAMapper = mock(User2FAMapper.class);
        twoFactorService = mock(TwoFactorService.class);
        service = new ReAuthServiceImpl(aspect, userAccountMapper, user2FAMapper, twoFactorService);
        when(aspect.issueToken(any(), anyLong(), anyInt())).thenReturn("token-xyz");
    }

    @Test
    @DisplayName("userId 为空抛 UNAUTHORIZED")
    void nullUserId() {
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("xxx");
        assertThatThrownBy(() -> service.issueToken(null, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("请求为空抛 BAD_REQUEST")
    void nullRequest() {
        assertThatThrownBy(() -> service.issueToken(1L, null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("operationCode 为空抛 BAD_REQUEST")
    void blankOperationCode() {
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("   ");
        r.setMethod("PASSWORD");
        r.setPassword("p");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("method 为空抛 BAD_REQUEST")
    void blankMethod() {
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod(null);
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("不支持的 method 抛 BAD_REQUEST")
    void unknownMethod() {
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("SMS");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("PASSWORD 凭据正确：颁发 token")
    void password_ok() {
        String salt = "testsalt";
        String enc = CryptoUtil.md5("rawPwd" + salt);
        UserAccountDO u = new UserAccountDO();
        u.setId(1L);
        u.setUsername("u");
        u.setPassword(enc);
        u.setSalt(salt);
        when(userAccountMapper.selectById(1L)).thenReturn(u);
        when(aspect.issueToken(eq("USER_DELETE"), eq(1L), anyInt())).thenReturn("tok-1");

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("USER_DELETE");
        r.setMethod("PASSWORD");
        r.setPassword("rawPwd");

        ReAuthResult out = service.issueToken(1L, r);
        assertThat(out.getToken()).isEqualTo("tok-1");
        assertThat(out.getMethod()).isEqualTo("PASSWORD");
        assertThat(out.getOperationCode()).isEqualTo("USER_DELETE");
        assertThat(out.getTtlSeconds()).isEqualTo(300);
        verify(aspect, times(1)).issueToken("USER_DELETE", 1L, 300);
    }

    @Test
    @DisplayName("PASSWORD 凭据错误：抛 FORBIDDEN")
    void password_wrong() {
        String salt = "testsalt";
        String enc = CryptoUtil.md5("right" + salt);
        UserAccountDO u = new UserAccountDO();
        u.setId(1L);
        u.setUsername("u");
        u.setPassword(enc);
        u.setSalt(salt);
        when(userAccountMapper.selectById(1L)).thenReturn(u);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("wrong");

        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
        verify(aspect, never()).issueToken(any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("PASSWORD 空值抛 BAD_REQUEST")
    void password_blank() {
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("  ");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("用户不存在抛 USER_NOT_FOUND")
    void userNotFound() {
        when(userAccountMapper.selectById(1L)).thenReturn(null);
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("p");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.USER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("salt 缺失时返回 false -> FORBIDDEN")
    void saltMissing() {
        UserAccountDO u = new UserAccountDO();
        u.setId(1L);
        u.setPassword("enc");
        u.setSalt(null);
        when(userAccountMapper.selectById(1L)).thenReturn(u);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("p");

        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("TOTP 凭据正确：颁发 token")
    void totp_ok() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setEnabled(true);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        when(twoFactorService.verify(1L, "123456")).thenReturn(true);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("totp"); // 小写也应工作
        r.setOtp("123456");

        ReAuthResult out = service.issueToken(1L, r);
        assertThat(out.getMethod()).isEqualTo("TOTP");
        assertThat(out.getToken()).isEqualTo("token-xyz");
    }

    @Test
    @DisplayName("TOTP 未绑定抛 FORBIDDEN")
    void totp_notBound() {
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("TOTP");
        r.setOtp("123456");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("TOTP 已禁用抛 FORBIDDEN")
    void totp_disabled() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setEnabled(false);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("TOTP");
        r.setOtp("123456");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("TOTP 错误码：抛 FORBIDDEN")
    void totp_wrong() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setEnabled(true);
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        when(twoFactorService.verify(1L, "000000")).thenReturn(false);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("TOTP");
        r.setOtp("000000");

        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("BACKUP_CODE 正确：颁发 token")
    void backup_ok() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setBackupCodes("aabbccdd,eeffgghh");
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        when(twoFactorService.verifyBackup(1L, "eeffgghh")).thenReturn(true);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("BACKUP_CODE");
        r.setBackupCode("eeffgghh");
        ReAuthResult out = service.issueToken(1L, r);
        assertThat(out.getMethod()).isEqualTo("BACKUP_CODE");
    }

    @Test
    @DisplayName("BACKUP_CODE 备份码为空抛 FORBIDDEN")
    void backup_emptyCodes() {
        when(user2FAMapper.selectByUserId(1L)).thenReturn(null);
        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("BACKUP_CODE");
        r.setBackupCode("eeffgghh");
        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("BACKUP_CODE 凭据错误抛 FORBIDDEN")
    void backup_wrong() {
        User2FADO e = new User2FADO();
        e.setUserId(1L);
        e.setBackupCodes("aabbccdd");
        when(user2FAMapper.selectByUserId(1L)).thenReturn(e);
        when(twoFactorService.verifyBackup(1L, "wrong")).thenReturn(false);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("BACKUP_CODE");
        r.setBackupCode("wrong");

        assertThatThrownBy(() -> service.issueToken(1L, r))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("TTL < 30 被钳制为 30")
    void ttl_tooSmall() {
        String salt = "testsalt";
        String enc = CryptoUtil.md5("p" + salt);
        UserAccountDO u = new UserAccountDO();
        u.setId(1L);
        u.setPassword(enc);
        u.setSalt(salt);
        when(userAccountMapper.selectById(1L)).thenReturn(u);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("p");
        r.setTtlSeconds(10);

        ReAuthResult out = service.issueToken(1L, r);
        assertThat(out.getTtlSeconds()).isEqualTo(30);
        verify(aspect).issueToken("OP-1", 1L, 30);
    }

    @Test
    @DisplayName("TTL > 1800 被钳制为 1800")
    void ttl_tooLarge() {
        String salt = "testsalt";
        String enc = CryptoUtil.md5("p" + salt);
        UserAccountDO u = new UserAccountDO();
        u.setId(1L);
        u.setPassword(enc);
        u.setSalt(salt);
        when(userAccountMapper.selectById(1L)).thenReturn(u);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("p");
        r.setTtlSeconds(99999);

        ReAuthResult out = service.issueToken(1L, r);
        assertThat(out.getTtlSeconds()).isEqualTo(1800);
        verify(aspect).issueToken("OP-1", 1L, 1800);
    }

    @Test
    @DisplayName("TTL 为 null 默认 300")
    void ttl_default() {
        String salt = "testsalt";
        String enc = CryptoUtil.md5("p" + salt);
        UserAccountDO u = new UserAccountDO();
        u.setId(1L);
        u.setPassword(enc);
        u.setSalt(salt);
        when(userAccountMapper.selectById(1L)).thenReturn(u);

        ReAuthRequest r = new ReAuthRequest();
        r.setOperationCode("OP-1");
        r.setMethod("PASSWORD");
        r.setPassword("p");
        // ttl null
        ReAuthResult out = service.issueToken(1L, r);
        assertThat(out.getTtlSeconds()).isEqualTo(300);
    }
}
