package com.njydsz.pmis.userinfo.service.impl.user;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.constant.CacheConstants;
import com.njydsz.pmis.common.datasource.DataSourceConstants;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.common.security.AccountLockInfo;
import com.njydsz.pmis.common.security.LoginAuditEvent;
import com.njydsz.pmis.common.security.LoginStatus;
import com.njydsz.pmis.common.security.PasswordPolicy;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.security.TotpUtil;
import com.njydsz.pmis.common.service.BloomFilterService;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.userinfo.dto.auth.LoginRequest;
import com.njydsz.pmis.userinfo.dto.auth.LoginResult;
import com.njydsz.pmis.userinfo.dto.user.UserQueryDTO;
import com.njydsz.pmis.userinfo.entity.org.DepartmentDO;
import com.njydsz.pmis.userinfo.entity.permission.RoleDO;
import com.njydsz.pmis.userinfo.entity.user.User2FADO;
import com.njydsz.pmis.userinfo.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.entity.user.UserRoleDO;
import com.njydsz.pmis.userinfo.mapper.user.User2FAMapper;
import com.njydsz.pmis.userinfo.mapper.user.UserAccountMapper;
import com.njydsz.pmis.userinfo.mapper.user.UserRoleMapper;
import com.njydsz.pmis.userinfo.service.impl.auth.JwtSimpleBuilder;
import com.njydsz.pmis.userinfo.service.org.DepartmentService;
import com.njydsz.pmis.userinfo.service.permission.RoleService;
import com.njydsz.pmis.userinfo.service.auth.SessionService;
import com.njydsz.pmis.userinfo.service.user.UserAccountService;
import com.njydsz.pmis.userinfo.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    /**
     * P1-6 修复: 用于 DEPT_AND_CHILD 模式递归计算 deptIds 链
     */
    private final DepartmentService departmentService;
    private final ApplicationEventPublisher publisher;
    private final AccountLockInfo lockPolicy = AccountLockInfo.defaultPolicy();
    /**
     * 布隆过滤器服务:用于防止缓存穿透(P1-10)
     */
    private final BloomFilterService bloomFilterService;

    /**
     * 启动时初始化布隆过滤器:从 DB 加载所有用户名
     *
     * <p>仅在过滤器为空时加载(首次启动或过滤器被清空后),
     * 避免每次重启都全量加载。过滤器数据持久化在 Redis 中,重启后仍然有效。
     * 加载失败不影响应用启动,仅打印告警日志。
     */
    @PostConstruct
    public void initBloomFilter() {
        try {
            if (bloomFilterService.count("user:username") > 0) {
                log.info("[User] 布隆过滤器已存在数据,跳过初始化");
                return;
            }
            // 仅查询 username 列,避免加载 password 等敏感字段
            List<UserAccountDO> users = userAccountMapper.selectList(
                    new LambdaQueryWrapper<UserAccountDO>().select(UserAccountDO::getUsername));
            List<String> usernames = users.stream()
                    .map(UserAccountDO::getUsername)
                    .filter(StringUtils::hasText)
                    .toList();
            bloomFilterService.addAll("user:username", usernames);
            log.info("[User] 布隆过滤器初始化完成,加载用户名数量={}", usernames.size());
        } catch (Exception e) {
            log.warn("[User] 布隆过滤器初始化失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.USER_BY_USERNAME_CACHE, key = "#username",
            unless = "#result == null")
    public UserAccountDO findByUsername(String username) {
        // 布隆过滤器防穿透:判定不存在时直接返回,不查 DB
        if (!bloomFilterService.mightContain("user:username", username)) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getUsername, username));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.USER_BY_ID_CACHE, key = "#userId",
            unless = "#result == null")
    public UserAccountDO findById(String userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        return u;
    }

    @Override
    @Transactional(readOnly = true)
    public UserVO findVoById(String userId) {
        return toVo(findById(userId));
    }

    @Override
    @DS(DataSourceConstants.SLAVE)
    @DataScope(deptColumn = "dept_id", userColumn = "id")
    @Transactional(readOnly = true)
    public Page<UserAccountDO> page(UserQueryDTO query) {
        Page<UserAccountDO> page = new Page<>(query.getPage(), Math.min(query.getSize(), PageQuery.MAX_SIZE));
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
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "id");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(UserAccountDO::getId);
        return userAccountMapper.selectPage(page, w);
    }

    @Override
    @DS(DataSourceConstants.SLAVE)
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
    public String create(UserAccountDO user, String rawPassword) {
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
        // 添加到布隆过滤器,后续查询可命中防穿透校验
        bloomFilterService.add("user:username", user.getUsername());
        return user.getId();
    }

    @Override
    @CacheEvict(value = {CacheConstants.USER_BY_ID_CACHE, CacheConstants.USER_BY_USERNAME_CACHE},
            allEntries = true)
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
    @CacheEvict(value = {CacheConstants.USER_BY_ID_CACHE, CacheConstants.USER_BY_USERNAME_CACHE},
            allEntries = true)
    public void delete(String userId) {
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
    @CacheEvict(value = {CacheConstants.USER_BY_ID_CACHE, CacheConstants.USER_BY_USERNAME_CACHE},
            allEntries = true)
    public void resetPassword(String userId, String newPassword) {
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
    @CacheEvict(value = {CacheConstants.USER_BY_ID_CACHE, CacheConstants.USER_BY_USERNAME_CACHE},
            allEntries = true)
    public void toggleStatus(String userId, String status) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        u.setStatus(status);
        userAccountMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String userId, List<String> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (String rid : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(rid);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    @DS(DataSourceConstants.SLAVE)
    @Transactional(readOnly = true)
    public List<String> listRoleIds(String userId) {
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
                    "error.user.msg_2e463b61", remain);
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
    public void changePassword(String userId, String oldPassword, String newPassword) {
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
    public void upgradePasswordHash(String userId, String bcryptHash) {
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
    public void clearLoginFailCount(String userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) return;
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userAccountMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.USER_BY_ID_CACHE, CacheConstants.USER_BY_USERNAME_CACHE},
            allEntries = true)
    public void lockAccount(String userId, LocalDateTime lockedUntil) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            log.warn("[User] 锁定账号失败: 用户不存在 userId={}", userId);
            return;
        }
        u.setLockedUntil(lockedUntil);
        userAccountMapper.updateById(u);
        log.warn("[User] 账号已锁定 userId={} username={} lockedUntil={}",
                userId, u.getUsername(), lockedUntil);
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
        // P1-6 修复: 写入数据权限上下文 claims，下游服务通过 AuthInterceptor 还原 DataScopeContext
        if (u.getDeptId() != null) {
            claims.put("deptId", u.getDeptId());
        }
        List<String> deptIds = resolveDeptIds(u.getDeptId());
        if (deptIds != null && !deptIds.isEmpty()) {
            claims.put("deptIds", deptIds);
        }
        List<String> customDeptIds = parseCustomDeptIds(u.getCustomDeptIds());
        if (customDeptIds != null && !customDeptIds.isEmpty()) {
            claims.put("customDeptIds", customDeptIds);
        }
        return JwtSimpleBuilder.build(claims, ACCESS_TOKEN_EXPIRE_SECONDS);
    }

    private String issueRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        return JwtSimpleBuilder.build(claims, REFRESH_TOKEN_EXPIRE_SECONDS);
    }

    /**
     * P1-6 修复: 解析 CUSTOM 模式自定义部门 ID 集
     *
     * <p>UserAccountDO.customDeptIds 为逗号分隔字符串（如 "1,3,5"），解析为 List&lt;String&gt;。
     * 解析失败时跳过非法值并打印告警。
     *
     * @param customDeptIds 逗号分隔的部门 ID 字符串
     * @return String 列表，为空时返回 null
     */
    private List<String> parseCustomDeptIds(String customDeptIds) {
        if (customDeptIds == null || customDeptIds.isBlank()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String s : customDeptIds.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * P1-6 修复: 递归计算 DEPT_AND_CHILD 模式部门 ID 链
     *
     * <p>基于 {@link DepartmentDO#getDeptPath()} 前缀匹配（deptPath 形如 "/1/3/5"），
     * 含当前部门及所有下级部门。查询失败时退化为仅当前部门。
     *
     * @param deptId 当前用户所属部门 ID
     * @return 部门 ID 链（含当前部门），为 null 时返回 null
     */
    private List<String> resolveDeptIds(String deptId) {
        if (deptId == null) {
            return null;
        }
        try {
            List<DepartmentDO> all = departmentService.listAllEnabled();
            DepartmentDO current = all.stream()
                    .filter(d -> deptId.equals(d.getId()))
                    .findFirst().orElse(null);
            if (current == null || current.getDeptPath() == null) {
                return List.of(deptId);
            }
            String prefix = current.getDeptPath() + "/";
            List<String> ids = new ArrayList<>();
            ids.add(deptId);
            for (DepartmentDO d : all) {
                if (d.getDeptPath() != null && d.getDeptPath().startsWith(prefix)) {
                    ids.add(d.getId());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[User] 计算 deptIds 失败 deptId={} reason={}，退化为仅当前部门",
                    deptId, e.getMessage());
            return List.of(deptId);
        }
    }

    private void publishAudit(LoginRequest req, String userId, LoginStatus status,
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
