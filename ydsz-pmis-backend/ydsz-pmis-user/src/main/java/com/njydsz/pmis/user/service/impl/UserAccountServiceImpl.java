package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.AccountLockInfo;
import com.njydsz.pmis.common.security.LoginAuditEvent;
import com.njydsz.pmis.common.security.LoginStatus;
import com.njydsz.pmis.common.security.PasswordPolicy;
import com.njydsz.pmis.common.security.TotpUtil;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.user.dto.LoginRequest;
import com.njydsz.pmis.user.dto.LoginResult;
import com.njydsz.pmis.user.dto.UserQueryDTO;
import com.njydsz.pmis.user.entity.User2FADO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.entity.UserRoleDO;
import com.njydsz.pmis.user.mapper.User2FAMapper;
import com.njydsz.pmis.user.mapper.UserAccountMapper;
import com.njydsz.pmis.user.mapper.UserRoleMapper;
import com.njydsz.pmis.user.service.SessionService;
import com.njydsz.pmis.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户账号服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private static final int ACCESS_TOKEN_EXPIRE_SECONDS = 7200;
    private static final int REFRESH_TOKEN_EXPIRE_SECONDS = 7 * 24 * 3600;
    private static final int PWD_EXPIRE_DAYS = 90;

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final User2FAMapper user2FAMapper;
    private final SessionService sessionService;
    private final ApplicationEventPublisher publisher;
    private final AccountLockInfo lockPolicy = AccountLockInfo.defaultPolicy();

    @Override
    public UserAccountDO findByUsername(String username) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getUsername, username));
    }

    @Override
    public UserAccountDO findById(Long userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        return u;
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "id")
    public Page<UserAccountDO> page(UserQueryDTO query) {
        Page<UserAccountDO> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<UserAccountDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.like(UserAccountDO::getUsername, query.getKeyword());
        }
        if (StringUtils.hasText(query.getStatus())) {
            w.eq(UserAccountDO::getStatus, query.getStatus());
        }
        if (query.getEmployeeId() != null) {
            w.eq(UserAccountDO::getEmployeeId, query.getEmployeeId());
        }
        // 数据权限 SQL 注入（按员工 dept_id 与 user.id）
        String ds = com.njydsz.pmis.common.security.DataScopeHelper.buildSqlFragment("", "");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(UserAccountDO::getId);
        return userAccountMapper.selectPage(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserAccountDO user, String rawPassword) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "用户名已存在");
        }
        PasswordPolicy.PasswordCheckResult r = PasswordPolicy.check(rawPassword, user.getUsername());
        if (!r.pass()) {
            throw new BizException(BizErrorCode.PASSWORD_WEAK, r.firstError());
        }
        String[] pair = CryptoUtil.encryptPassword(rawPassword);
        user.setPassword(pair[0]);
        user.setSalt(pair[1]);
        if (user.getStatus() == null) user.setStatus("ENABLED");
        user.setLoginFailCount(0);
        user.setLastLoginTime(null);
        user.setMfaEnabled(false);
        user.setMfaType("NONE");
        user.setLastPwdChangeAt(LocalDateTime.now());
        user.setPwdChangeCount(0);
        if (user.getDataScope() == null) user.setDataScope("SELF");
        userAccountMapper.insert(user);
        return user.getId();
    }

    @Override
    public void update(UserAccountDO user) {
        if (user.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID 不能为空");
        }
        UserAccountDO exists = userAccountMapper.selectById(user.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        // 不可改用户名/密码
        user.setUsername(null);
        user.setPassword(null);
        user.setSalt(null);
        userAccountMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if ("admin".equals(u.getUsername())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "内置 admin 不可删除");
        }
        userAccountMapper.deleteById(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        PasswordPolicy.PasswordCheckResult r = PasswordPolicy.check(newPassword, u.getUsername());
        if (!r.pass()) {
            throw new BizException(BizErrorCode.PASSWORD_WEAK, r.firstError());
        }
        String[] pair = CryptoUtil.encryptPassword(newPassword);
        u.setPassword(pair[0]);
        u.setSalt(pair[1]);
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        u.setLastPwdChangeAt(LocalDateTime.now());
        u.setPwdChangeCount((u.getPwdChangeCount() == null ? 0 : u.getPwdChangeCount()) + 1);
        userAccountMapper.updateById(u);
        log.info("[User] 重置密码 userId={}", userId);
    }

    @Override
    public void toggleStatus(Long userId, String status) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        u.setStatus(status);
        userAccountMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long rid : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(rid);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResult login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户名不能为空");
        }
        UserAccountDO u = findByUsername(request.getUsername());
        if (u == null) {
            publishAudit(request, null, LoginStatus.FAIL_USER_NOT_FOUND, "用户不存在", false, null);
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }

        if (lockPolicy.isLocked(u.getLockedUntil())) {
            publishAudit(request, u.getId(), LoginStatus.FAIL_LOCKED,
                    "账号已锁定至 " + u.getLockedUntil(), false, null);
            long remain = lockPolicy.remainingMinutes(u.getLockedUntil());
            throw new BizException(BizErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请 " + remain + " 分钟后再试");
        }

        if (!"ENABLED".equals(u.getStatus())) {
            publishAudit(request, u.getId(), LoginStatus.FAIL_DISABLED, "账号已停用", false, null);
            throw new BizException(BizErrorCode.USER_DISABLED);
        }

        boolean passwordOk = CryptoUtil.verifyPassword(request.getPassword(), u.getPassword(), u.getSalt());
        if (!passwordOk) {
            handleLoginFailure(u, "密码错误");
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT);
        }

        // 2FA 校验
        User2FADO twofa = user2FAMapper.selectByUserId(u.getId());
        boolean mfaRequired = twofa != null && Boolean.TRUE.equals(twofa.getEnabled());
        boolean mfaPassed = false;
        if (mfaRequired && twofa != null) {
            if (StringUtils.hasText(request.getOtp())) {
                mfaPassed = TotpUtil.verify(twofa.getSecret(), request.getOtp());
            } else if (StringUtils.hasText(request.getBackupCode())) {
                mfaPassed = verifyBackup(twofa, request.getBackupCode());
            }
            if (!mfaPassed) {
                publishAudit(request, u.getId(), LoginStatus.FAIL_MFA, "2FA 验证失败", true, false);
                throw new BizException(BizErrorCode.MFA_INVALID);
            }
        }

        // 登录成功
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        u.setLastLoginTime(LocalDateTime.now());
        u.setLastLoginIp(request.getClientIp());
        userAccountMapper.updateById(u);

        // 创建会话
        var session = sessionService.create(u.getId(), request.getClientIp(), request.getUserAgent(),
                request.getDeviceType(), ACCESS_TOKEN_EXPIRE_SECONDS);

        // 同账号互踢（仅保留当前会话）
        sessionService.kickOthers(u.getId(), session.getSessionId());

        // 颁发 token
        String access = issueAccessToken(u, session.getSessionId());
        String refresh = issueRefreshToken(u.getId());

        publishAudit(request, u.getId(), LoginStatus.SUCCESS, null, mfaRequired, mfaPassed);

        return LoginResult.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expireAt(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE_SECONDS * 1000L)
                .sessionId(session.getSessionId())
                .userId(u.getId())
                .username(u.getUsername())
                .mfaRequired(mfaRequired)
                .mfaPassed(mfaPassed)
                .dataScope(u.getDataScope())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if (!CryptoUtil.verifyPassword(oldPassword, u.getPassword(), u.getSalt())) {
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT, "原密码错误");
        }
        PasswordPolicy.PasswordCheckResult r = PasswordPolicy.check(newPassword, u.getUsername());
        if (!r.pass()) {
            throw new BizException(BizErrorCode.PASSWORD_WEAK, r.firstError());
        }
        if (PasswordPolicy.isExpired(u.getLastPwdChangeAt(), PWD_EXPIRE_DAYS) && u.getLastPwdChangeAt() != null) {
            // 强制改密场景下直接放行
        }
        String[] pair = CryptoUtil.encryptPassword(newPassword);
        u.setPassword(pair[0]);
        u.setSalt(pair[1]);
        u.setLastPwdChangeAt(LocalDateTime.now());
        u.setPwdChangeCount((u.getPwdChangeCount() == null ? 0 : u.getPwdChangeCount()) + 1);
        userAccountMapper.updateById(u);
        log.info("[User] 修改密码 userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearLoginFailCount(Long userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) return;
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userAccountMapper.updateById(u);
    }

    /**
     * 登录失败处理：递增失败次数 + 必要时锁定
     */
    private void handleLoginFailure(UserAccountDO u, String reason) {
        int cnt = (u.getLoginFailCount() == null ? 0 : u.getLoginFailCount()) + 1;
        u.setLoginFailCount(cnt);
        if (lockPolicy.shouldLock(cnt)) {
            u.setLockedUntil(lockPolicy.calculateLockUntil(LocalDateTime.now()));
        }
        userAccountMapper.updateById(u);
        publishAudit(null, u.getId(), LoginStatus.FAIL_PASSWORD, reason, false, null);
    }

    private boolean verifyBackup(User2FADO e, String code) {
        if (e.getBackupCodes() == null) return false;
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

    private String issueAccessToken(UserAccountDO u, String sessionId) {
        // 简化：直接使用 JWT-like 字符串（实际项目接入 JwtTokenProvider）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", u.getId());
        claims.put("username", u.getUsername());
        claims.put("sessionId", sessionId);
        claims.put("mfa", u.getMfaEnabled() != null && u.getMfaEnabled());
        claims.put("dataScope", u.getDataScope());
        return JwtSimpleBuilder.build(claims, ACCESS_TOKEN_EXPIRE_SECONDS);
    }

    private String issueRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        return JwtSimpleBuilder.build(claims, REFRESH_TOKEN_EXPIRE_SECONDS);
    }

    private void publishAudit(LoginRequest req, Long userId, LoginStatus status,
                              String reason, boolean mfaUsed, Boolean mfaSuccess) {
        try {
            LoginAuditEvent e = LoginAuditEvent.builder()
                    .username(req != null ? req.getUsername() : null)
                    .userId(userId)
                    .loginIp(req != null ? req.getClientIp() : null)
                    .userAgent(req != null ? req.getUserAgent() : null)
                    .status(status)
                    .failReason(reason)
                    .mfaUsed(mfaUsed)
                    .mfaSuccess(mfaSuccess)
                    .traceId(TraceIdUtil.get())
                    .tenantId(1L)
                    .loginAt(System.currentTimeMillis())
                    .build();
            publisher.publishEvent(e);
        } catch (Exception ex) {
            log.warn("[Login] 发布审计事件失败: {}", ex.getMessage());
        }
    }
}
