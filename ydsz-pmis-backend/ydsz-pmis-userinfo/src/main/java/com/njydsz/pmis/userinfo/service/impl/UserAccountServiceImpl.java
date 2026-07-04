package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.common.security.AccountLockInfo;
import com.njydsz.pmis.common.security.LoginAuditEvent;
import com.njydsz.pmis.common.security.LoginStatus;
import com.njydsz.pmis.common.security.PasswordPolicy;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.security.TotpUtil;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.userinfo.dto.LoginRequest;
import com.njydsz.pmis.userinfo.dto.LoginResult;
import com.njydsz.pmis.userinfo.dto.UserQueryDTO;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import com.njydsz.pmis.userinfo.entity.User2FADO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.entity.UserRoleDO;
import com.njydsz.pmis.userinfo.mapper.User2FAMapper;
import com.njydsz.pmis.userinfo.mapper.UserAccountMapper;
import com.njydsz.pmis.userinfo.mapper.UserRoleMapper;
import com.njydsz.pmis.userinfo.service.RoleService;
import com.njydsz.pmis.userinfo.service.SessionService;
import com.njydsz.pmis.userinfo.service.UserAccountService;
import com.njydsz.pmis.userinfo.vo.UserVO;
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
    private final RoleService roleService;
    private final SessionService sessionService;
    private final ApplicationEventPublisher publisher;
    private final AccountLockInfo lockPolicy = AccountLockInfo.defaultPolicy();

    @Override
    @Transactional(readOnly = true)
    public UserAccountDO findByUsername(String username) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getUsername, username));
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountDO findById(Long userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        return u;
    }

    @Override
    @Transactional(readOnly = true)
    public UserVO findVoById(Long userId) {
        return toVo(findById(userId));
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "id")
    @Transactional(readOnly = true)
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
        String ds = DataScopeHelper.buildSqlFragment("", "");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(UserAccountDO::getId);
        return userAccountMapper.selectPage(page, w);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserVO> pageVo(UserQueryDTO query) {
        Page<UserAccountDO> doPage = page(query);
        Page<UserVO> voPage = new Page<>(doPage.getCurrent(), doPage.getSize(), doPage.getTotal());
        voPage.setRecords(doPage.getRecords().stream().map(this::toVo).toList());
        return voPage;
    }

    /**
     * DO → VO 转换（H13.1 修复：对外接口统一返回 UserVO，剥离 password/salt）
     *
     * @param u 用户账号 DO
     * @return 用户视图对象（不含敏感字段）
     */
    private UserVO toVo(UserAccountDO u) {
        if (u == null) return null;
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setEmployeeId(u.getEmployeeId());
        vo.setStatus(u.getStatus());
        vo.setLastLoginTime(u.getLastLoginTime());
        vo.setLastLoginIp(u.getLastLoginIp());
        vo.setDataScope(u.getDataScope());
        vo.setDeptId(u.getDeptId());
        vo.setLeaderId(u.getLeaderId());
        vo.setPositionCode(u.getPositionCode());
        vo.setMfaEnabled(u.getMfaEnabled());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserAccountDO user, String rawPassword) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.user.msg_a633b7b9");
        }
        PasswordPolicy.PasswordCheckResult r = PasswordPolicy.check(rawPassword, user.getUsername());
        if (!r.pass()) {
            throw new BizException(BizErrorCode.PASSWORD_WEAK, r.firstError());
        }
        // BCrypt 哈希存储在 password 字段，salt 字段留空（BCrypt 自带盐）
        user.setPassword(CryptoUtil.hashPasswordBCrypt(rawPassword));
        user.setSalt("");
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_668e9add");
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
        // 禁止删除超级管理员：通过角色判断而非用户名，避免管理员改名后保护失效
        List<RoleDO> roles = roleService.listByUserId(userId);
        boolean isSuperAdmin = roles.stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getRoleCode()));
        if (isSuperAdmin) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_5b101e42");
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
        // BCrypt 哈希存储在 password 字段，salt 字段留空（BCrypt 自带盐）
        u.setPassword(CryptoUtil.hashPasswordBCrypt(newPassword));
        u.setSalt("");
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
    @Transactional(readOnly = true)
    public List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("deprecation")
    public LoginResult login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_0b62b5ce");
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
                    "error.user.msg_2e463b61" + remain + " 分钟后再试");
        }

        if (!"ENABLED".equals(u.getStatus())) {
            publishAudit(request, u.getId(), LoginStatus.FAIL_DISABLED, "账号已停用", false, null);
            throw new BizException(BizErrorCode.USER_DISABLED);
        }

        // 兼容 BCrypt 与历史 MD5：BCrypt 格式用 BCrypt 校验；MD5 校验通过后惰性升级为 BCrypt
        boolean oldHashWasBcrypt = CryptoUtil.isBCryptFormat(u.getPassword());
        boolean passwordOk = oldHashWasBcrypt
                ? CryptoUtil.verifyPasswordBCrypt(request.getPassword(), u.getPassword())
                : CryptoUtil.verifyPassword(request.getPassword(), u.getPassword(), u.getSalt());
        if (!passwordOk) {
            handleLoginFailure(u, "密码错误");
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT);
        }
        // 惰性升级：历史 MD5 密码登录成功后升级为 BCrypt（失败不影响登录流程）
        if (!oldHashWasBcrypt) {
            try {
                upgradePasswordHash(u.getId(), CryptoUtil.hashPasswordBCrypt(request.getPassword()));
            } catch (Exception ex) {
                log.warn("[User] 密码哈希惰性升级失败 userId={} reason={}", u.getId(), ex.getMessage());
            }
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
    @SuppressWarnings("deprecation")
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        // 兼容 BCrypt 与历史 MD5：BCrypt 格式用 BCrypt 校验，否则用 MD5 校验
        boolean oldHashWasBcrypt = CryptoUtil.isBCryptFormat(u.getPassword());
        boolean oldPasswordOk = oldHashWasBcrypt
                ? CryptoUtil.verifyPasswordBCrypt(oldPassword, u.getPassword())
                : CryptoUtil.verifyPassword(oldPassword, u.getPassword(), u.getSalt());
        if (!oldPasswordOk) {
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT, "error.user.msg_25562cd3");
        }
        PasswordPolicy.PasswordCheckResult r = PasswordPolicy.check(newPassword, u.getUsername());
        if (!r.pass()) {
            throw new BizException(BizErrorCode.PASSWORD_WEAK, r.firstError());
        }
        if (PasswordPolicy.isExpired(u.getLastPwdChangeAt(), PWD_EXPIRE_DAYS) && u.getLastPwdChangeAt() != null) {
            // 强制改密场景下直接放行
        }
        // 新密码统一使用 BCrypt 哈希（自带盐，salt 字段留空）
        u.setPassword(CryptoUtil.hashPasswordBCrypt(newPassword));
        u.setSalt("");
        u.setLastPwdChangeAt(LocalDateTime.now());
        u.setPwdChangeCount((u.getPwdChangeCount() == null ? 0 : u.getPwdChangeCount()) + 1);
        userAccountMapper.updateById(u);
        log.info("[User] 修改密码 userId={} oldHashBcrypt={} newHashBcrypt=true",
                userId, oldHashWasBcrypt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upgradePasswordHash(Long userId, String bcryptHash) {
        if (userId == null || !CryptoUtil.isBCryptFormat(bcryptHash)) {
            log.warn("[User] 跳过密码哈希升级：参数非法 userId={} format={}",
                    userId, bcryptHash == null ? "null" : "invalid");
            return;
        }
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            return;
        }
        // 仅当当前密码非 BCrypt 格式时才升级，避免重复覆盖
        if (CryptoUtil.isBCryptFormat(u.getPassword())) {
            return;
        }
        u.setPassword(bcryptHash);
        u.setSalt("");
        userAccountMapper.updateById(u);
        log.info("[User] 密码哈希惰性升级为 BCrypt userId={}", userId);
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
                    .tenantId(TenantContext.getTenantId())
                    .loginAt(System.currentTimeMillis())
                    .build();
            publisher.publishEvent(e);
        } catch (Exception ex) {
            log.warn("[Login] 发布审计事件失败: {}", ex.getMessage());
        }
    }
}