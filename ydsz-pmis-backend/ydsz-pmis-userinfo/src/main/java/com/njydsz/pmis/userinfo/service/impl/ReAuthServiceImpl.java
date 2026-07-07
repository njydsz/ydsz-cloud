package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.aspect.RequireReAuthAspect;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.userinfo.dto.ReAuthRequest;
import com.njydsz.pmis.userinfo.dto.ReAuthResult;
import com.njydsz.pmis.userinfo.entity.User2FADO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.mapper.User2FAMapper;
import com.njydsz.pmis.userinfo.mapper.UserAccountMapper;
import com.njydsz.pmis.userinfo.service.ReAuthService;
import com.njydsz.pmis.userinfo.service.TwoFactorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 二次认证服务实现
 *
 * <p>颁发 token 流程：
 * <ol>
 *   <li>校验必填参数（operationCode + method）</li>
 *   <li>根据 method 选择凭据校验策略（PASSWORD / TOTP / BACKUP_CODE）</li>
 *   <li>调用 {@link RequireReAuthAspect#issueToken} 颁发 Redis token</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReAuthServiceImpl implements ReAuthService {

    /** 默认 TTL（秒） */
    private static final int DEFAULT_TTL = 300;
    /** 最大 TTL（秒），防滥用 */
    private static final int MAX_TTL = 1800;
    /** 最小 TTL（秒） */
    private static final int MIN_TTL = 30;

    private final RequireReAuthAspect requireReAuthAspect;
    private final UserAccountMapper userAccountMapper;
    private final User2FAMapper user2FAMapper;
    private final TwoFactorService twoFactorService;

    @Override
    public ReAuthResult issueToken(String userId, ReAuthRequest request) {
        if (userId == null) {
            throw new BizException(BizErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_d9712a58");
        }
        String opCode = trimToNull(request.getOperationCode());
        String method = trimToNull(request.getMethod());
        if (opCode == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_34522254");
        }
        if (method == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_92565530");
        }
        String methodUpper = method.toUpperCase(Locale.ROOT);
        int ttl = normalizeTtl(request.getTtlSeconds());

        boolean ok;
        switch (methodUpper) {
            case "PASSWORD" -> ok = verifyPassword(userId, request.getPassword());
            case "TOTP" -> ok = verifyTotp(userId, request.getOtp());
            case "BACKUP_CODE" -> ok = verifyBackupCode(userId, request.getBackupCode());
            default -> throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.user.msg_0fecfe87", method);
        }
        if (!ok) {
            throw new BizException(BizErrorCode.FORBIDDEN, "error.user.msg_89bb6348");
        }

        String token = requireReAuthAspect.issueToken(opCode, userId, ttl);
        log.info("[ReAuth] 颁发二次认证 token userId={} operation={} method={} ttl={}s",
                userId, opCode, methodUpper, ttl);
        return ReAuthResult.builder()
                .token(token)
                .ttlSeconds(ttl)
                .method(methodUpper)
                .operationCode(opCode)
                .build();
    }

    // ----------------- 私有 -----------------

    @SuppressWarnings("deprecation")
    private boolean verifyPassword(String userId, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_1a011aca");
        }
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if (u.getSalt() == null || u.getSalt().isBlank()) {
            log.warn("[ReAuth] 用户 {} 缺少 salt 配置，拒绝密码校验", userId);
            return false;
        }
        return CryptoUtil.verifyPassword(rawPassword, u.getPassword(), u.getSalt());
    }

    private boolean verifyTotp(String userId, String otp) {
        if (otp == null || otp.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_d6ef6b97");
        }
        User2FADO e = user2FAMapper.selectByUserId(userId);
        if (e == null || !Boolean.TRUE.equals(e.getEnabled())) {
            throw new BizException(BizErrorCode.FORBIDDEN, "error.user.msg_2a4023be");
        }
        return twoFactorService.verify(userId, otp);
    }

    private boolean verifyBackupCode(String userId, String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_140fe16d");
        }
        User2FADO e = user2FAMapper.selectByUserId(userId);
        if (e == null || e.getBackupCodes() == null || e.getBackupCodes().isBlank()) {
            throw new BizException(BizErrorCode.FORBIDDEN, "error.user.msg_bd347be6");
        }
        return twoFactorService.verifyBackup(userId, code);
    }

    private int normalizeTtl(Integer ttl) {
        if (ttl == null) return DEFAULT_TTL;
        if (ttl < MIN_TTL) return MIN_TTL;
        return Math.min(ttl, MAX_TTL);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}