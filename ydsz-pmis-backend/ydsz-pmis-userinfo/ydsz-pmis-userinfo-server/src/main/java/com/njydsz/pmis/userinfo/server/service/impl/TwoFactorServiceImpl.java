package com.njydsz.pmis.userinfo.server.service.impl.auth;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.security.TotpUtil;
import com.njydsz.pmis.userinfo.domain.dto.auth.TwoFactorBindResult;
import com.njydsz.pmis.userinfo.domain.entity.user.User2FADO;
import com.njydsz.pmis.userinfo.domain.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.infra.mapper.user.User2FAMapper;
import com.njydsz.pmis.userinfo.infra.mapper.user.UserAccountMapper;
import com.njydsz.pmis.userinfo.server.service.auth.TwoFactorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 双因素认证服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorServiceImpl implements TwoFactorService {

    private static final int BACKUP_CODE_COUNT = 8;

    private final User2FAMapper user2FAMapper;
    private final UserAccountMapper userAccountMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TwoFactorBindResult bindTotp(String userId, String account) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new SysException(StandardResultCode.USER_NOT_FOUND);
        }
        User2FADO existing = user2FAMapper.selectByUserId(userId);
        String secret;
        if (existing != null && Boolean.TRUE.equals(existing.getEnabled())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_350ea646");
        }
        secret = TotpUtil.generateSecret();
        String[] codes = TotpUtil.generateBackupCodes(BACKUP_CODE_COUNT);
        User2FADO entity = existing != null ? existing : new User2FADO();
        entity.setUserId(userId);
        entity.setMfaType("TOTP");
        entity.setSecret(secret);
        entity.setBindingAt(LocalDateTime.now());
        entity.setBackupCodes(joinCodes(codes));
        entity.setEnabled(false);
        entity.setTenantId(TenantContext.getTenantId());
        if (existing == null) {
            user2FAMapper.insert(entity);
        } else {
            user2FAMapper.updateById(entity);
        }
        String issuer = "PMIS";
        String uri = TotpUtil.otpAuthUri(account, issuer, secret);
        return TwoFactorBindResult.builder()
                .secret(secret)
                .otpAuthUri(uri)
                .backupCodes(Arrays.asList(codes))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmBind(String userId, String otp) {
        User2FADO e = user2FAMapper.selectByUserId(userId);
        if (e == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_b9b014df");
        }
        if (!TotpUtil.verify(e.getSecret(), otp)) {
            return false;
        }
        e.setEnabled(true);
        e.setLastUsedAt(LocalDateTime.now());
        user2FAMapper.updateById(e);

        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u != null) {
            u.setMfaEnabled(true);
            u.setMfaType("TOTP");
            userAccountMapper.updateById(u);
        }
        return true;
    }

    @Override
    public boolean verify(String userId, String otp) {
        User2FADO e = user2FAMapper.selectByUserId(userId);
        if (e == null || !Boolean.TRUE.equals(e.getEnabled())) {
            return false;
        }
        if (!TotpUtil.verify(e.getSecret(), otp)) {
            return false;
        }
        e.setLastUsedAt(LocalDateTime.now());
        user2FAMapper.updateById(e);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean verifyBackup(String userId, String code) {
        User2FADO e = user2FAMapper.selectByUserId(userId);
        if (e == null || e.getBackupCodes() == null) {
            return false;
        }
        String[] codes = e.getBackupCodes().split(",");
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(code)) {
                codes[i] = "_used_" + System.currentTimeMillis();
                e.setBackupCodes(String.join(",", codes));
                user2FAMapper.updateById(e);
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String userId) {
        user2FAMapper.disableByUserId(userId);
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u != null) {
            u.setMfaEnabled(false);
            u.setMfaType("NONE");
            userAccountMapper.updateById(u);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User2FADO find(String userId) {
        return user2FAMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listBackupCodesMasked(String userId) {
        User2FADO e = user2FAMapper.selectByUserId(userId);
        if (e == null || e.getBackupCodes() == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(e.getBackupCodes().split(","))
                .map(c -> c.length() <= 4 ? "****" : c.substring(0, 2) + "****" + c.substring(c.length() - 2))
                .collect(Collectors.toList());
    }

    private String joinCodes(String[] codes) {
        return String.join(",", codes);
    }
}