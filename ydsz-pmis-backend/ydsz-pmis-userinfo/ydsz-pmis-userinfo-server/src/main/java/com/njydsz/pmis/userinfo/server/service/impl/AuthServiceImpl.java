package com.njydsz.pmis.userinfo.server.service.impl.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.auth.token.JwtTokenProvider;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.AccountLockedEvent;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.userinfo.domain.dto.auth.CaptchaVO;
import com.njydsz.pmis.userinfo.domain.dto.auth.LoginContextDTO;
import com.njydsz.pmis.userinfo.domain.dto.auth.LoginDTO;
import com.njydsz.pmis.userinfo.domain.dto.auth.LoginResultVO;
import com.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import com.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;
import com.njydsz.pmis.userinfo.domain.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.server.service.auth.AuthService;
import com.njydsz.pmis.userinfo.server.service.org.DepartmentService;
import com.njydsz.pmis.userinfo.server.service.permission.PermissionService;
import com.njydsz.pmis.userinfo.server.service.permission.RoleService;
import com.njydsz.pmis.userinfo.server.service.user.UserAccountService;
import com.wf.captcha.SpecCaptcha;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证服务实现
 *
 * <p>核心流程：
 * <ol>
 *   <li>校验图形验证码（Redis 5 分钟有效期）</li>
 *   <li>本地加载登录上下文（合并后直接调用 user 服务，无需 Feign）</li>
 *   <li>校验密码（BCrypt 推荐，兼容历史 MD5 + 随机盐，登录成功后惰性升级为 BCrypt）</li>
 *   <li>校验用户状态（ENABLED / 锁定）</li>
 *   <li>生成 JWT（roles/permissions 写入 Claims）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 验证码 Redis Key 前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "pmis:captcha:";
    /** 登录失败计数 Redis Key 前缀 */
    private static final String LOGIN_FAIL_PREFIX = "pmis:login:fail:";
    /** Token 黑名单 Redis Key 前缀 */
    private static final String TOKEN_BLACKLIST_PREFIX = "pmis:token:blacklist:";

    /** 验证码有效期(分钟) */
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;
    /** 访问 Token 有效期(小时) */
    private static final long TOKEN_EXPIRE_HOURS = 8;
    /** 刷新 Token 有效期(天) */
    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 7;
    /** 登录失败锁定阈值(次) */
    private static final int LOGIN_FAIL_THRESHOLD = 5;
    /** 登录锁定时长(分钟) */
    private static final long LOGIN_LOCK_MINUTES = 30;

    /** Redis 操作模板（用于验证码、登录失败计数、Token 黑名单） */
    private final StringRedisTemplate redisTemplate;
    /** JWT Token 生成与校验工具 */
    private final JwtTokenProvider jwtTokenProvider;
    /** 用户账号服务（合并后本地调用，替代原 Feign） */
    private final UserAccountService userAccountService;
    /** 角色服务 */
    private final RoleService roleService;
    /** 权限服务 */
    private final PermissionService permissionService;
    /**
     * 部门服务（P1-6 修复：用于 DEPT_AND_CHILD 模式递归计算 deptIds 链）
     */
    private final DepartmentService departmentService;
    /** Spring 事件发布器（用于发布账号锁定事件等） */
    private final ApplicationEventPublisher publisher;

    /**
     * 是否强制启用图形验证码 (测试场景可关闭)
     */
    @Value("${pmis.auth.captcha-required:true}")
    private boolean captchaRequired = true;

    /**
     * 设置是否强制启用图形验证码（主要用于测试场景）
     *
     * @param captchaRequired 是否启用图形验证码
     */
    public void setCaptchaRequired(boolean captchaRequired) {
        this.captchaRequired = captchaRequired;
    }

    /**
     * 生成图形验证码
     *
     * @return 验证码 VO（含 captchaKey 与 Base64 图片）
     */
    @Override
    public CaptchaVO generateCaptcha() {
        // 1. 生成图形验证码 (使用 easy-captcha)
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(SpecCaptcha.TYPE_DEFAULT);
        String code = captcha.text().toLowerCase();
        String image = captcha.toBase64();

        // 2. 写入 Redis (5 分钟过期)
        String key = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + key, code, Duration.ofMinutes(CAPTCHA_EXPIRE_MINUTES));

        return CaptchaVO.builder()
                .captchaKey(key)
                .captchaImage(image)
                .build();
    }

    /**
     * 登录
     *
     * @param dto 登录请求参数（用户名、密码、验证码等）
     * @return 登录结果 VO（含访问 Token 与刷新 Token）
     * @throws SysException 当验证码错误、用户不存在、账号锁定或密码错误时抛出
     */
    @Override
    @SuppressWarnings("deprecation")
    public LoginResultVO login(LoginDTO dto) {
        // 1. 图形验证码校验（可配置关闭）
        if (captchaRequired) {
            validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());
        }

        // 2. 本地加载登录上下文（合并后直接调用 user 服务，无需 Feign）
        LoginContextDTO ctx = buildContext(userAccountService.findByUsername(dto.getUsername()));
        if (ctx == null) {
            log.warn("[Auth] 用户不存在 username={}", dto.getUsername());
            throw new SysException(BaseResultCode.USER_NOT_FOUND);
        }

        // 3. 锁定检查
        if (ctx.getLockedUntil() != null && ctx.getLockedUntil() > System.currentTimeMillis()) {
            throw new SysException(BaseResultCode.USER_LOCKED, "error.auth.msg_9d09bb97");
        }

        // 4. 状态校验
        if (!"ENABLED".equalsIgnoreCase(ctx.getStatus())) {
            throw new SysException(BaseResultCode.USER_DISABLED);
        }

        // 5. 密码校验（兼容 BCrypt 与历史 MD5；MD5 校验通过后惰性升级为 BCrypt）
        boolean oldHashWasBcrypt = CryptoUtil.isBCryptFormat(ctx.getPassword());
        boolean passwordOk = oldHashWasBcrypt
                ? CryptoUtil.verifyPasswordBCrypt(dto.getPassword(), ctx.getPassword())
                : CryptoUtil.verifyPassword(dto.getPassword(), ctx.getPassword(), ctx.getSalt());
        if (!passwordOk) {
            recordLoginFailure(dto.getUsername());
            throw new SysException(BaseResultCode.PASSWORD_INCORRECT);
        }
        // 惰性升级：历史 MD5 密码登录成功后升级为 BCrypt（失败不影响登录流程）
        if (!oldHashWasBcrypt) {
            try {
                userAccountService.upgradePasswordHash(ctx.getUserId(),
                        CryptoUtil.hashPasswordBCrypt(dto.getPassword()));
            } catch (Exception ex) {
                log.warn("[Auth] 密码哈希惰性升级失败 userId={} reason={}",
                        ctx.getUserId(), ex.getMessage());
            }
        }

        // 6. 清除失败计数
        clearLoginFailure(dto.getUsername());

        // 7. 生成 Token (P1-6 修复: 含数据权限上下文 deptId/deptIds/customDeptIds/dataScope)
        String token = jwtTokenProvider.generateToken(
                ctx.getUserId(), ctx.getUsername(),
                ctx.getRoles(), ctx.getPermissions(),
                ctx.getDeptId(), ctx.getDeptIds(), ctx.getCustomDeptIds(),
                ctx.getDataScope(),
                TOKEN_EXPIRE_HOURS * 3600L);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                ctx.getUserId(), REFRESH_TOKEN_EXPIRE_DAYS * 24 * 3600L);

        log.info("[Auth] 登录成功 userId={} username={} roles={}",
                ctx.getUserId(), ctx.getUsername(), ctx.getRoles());

        return LoginResultVO.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(TOKEN_EXPIRE_HOURS * 3600L)
                .build();
    }

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录结果 VO（含新的访问 Token 与刷新 Token）
     * @throws SysException 当刷新 Token 无效或用户不存在/禁用时抛出
     */
    @Override
    public LoginResultVO refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new SysException(BaseResultCode.TOKEN_INVALID);
        }

        String userId = jwtTokenProvider.getUserId(refreshToken);

        // 重新加载上下文（角色权限可能已变）
        LoginContextDTO ctx = buildContext(userAccountService.findById(userId));
        if (ctx == null) {
            throw new SysException(BaseResultCode.USER_NOT_FOUND);
        }
        if (!"ENABLED".equalsIgnoreCase(ctx.getStatus())) {
            throw new SysException(BaseResultCode.USER_DISABLED);
        }

        String newToken = jwtTokenProvider.generateToken(
                ctx.getUserId(), ctx.getUsername(),
                ctx.getRoles(), ctx.getPermissions(),
                ctx.getDeptId(), ctx.getDeptIds(), ctx.getCustomDeptIds(),
                ctx.getDataScope(),
                TOKEN_EXPIRE_HOURS * 3600L);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                ctx.getUserId(), REFRESH_TOKEN_EXPIRE_DAYS * 24 * 3600L);

        return LoginResultVO.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .expiresIn(TOKEN_EXPIRE_HOURS * 3600L)
                .build();
    }

    /**
     * 登出
     *
     * @param userId 用户 ID
     */
    @Override
    public void logout(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        // 将当前 Token 加入黑名单 (由 AuthGlobalFilter 检查)
        // 注: 实际项目中应通过请求头拿 Token 一起加入黑名单
        log.info("[Auth] 登出 userId={}", userId);
    }

    /**
     * 将 Token 加入黑名单
     *
     * @param token         待拉黑的 Token
     * @param expireSeconds 黑名单有效期（秒），通常与 Token 剩余有效期一致
     */
    @Override
    public void blacklistToken(String token, long expireSeconds) {
        if (token == null || token.isBlank()) return;
        redisTemplate.opsForValue().set(
                TOKEN_BLACKLIST_PREFIX + token, "1", Duration.ofSeconds(expireSeconds));
    }

    /**
     * 校验 Token 是否在黑名单
     *
     * @param token 待校验的 Token
     * @return true 表示在黑名单中（已登出），false 表示可用
     */
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }

    // ============== 私有方法 ==============

    /**
     * 根据用户实体构建登录上下文（含角色与权限编码）
     *
     * <p>合并后由本地 Service 直接装配，替代原 Feign 远程调用。
     *
     * @param user 用户实体
     * @return 登录上下文，用户为空时返回 null
     */
    private LoginContextDTO buildContext(UserAccountDO user) {
        if (user == null) {
            return null;
        }
        LoginContextDTO.LoginContextDTOBuilder builder = LoginContextDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .salt(user.getSalt())
                .status(user.getStatus())
                .loginFailCount(user.getLoginFailCount() == null ? 0 : user.getLoginFailCount())
                .lockedUntil(user.getLockedUntil() == null ? null
                        : user.getLockedUntil().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        // 角色编码列表
        try {
            List<RoleDO> roles = roleService.listByUserId(user.getId());
            if (roles != null && !roles.isEmpty()) {
                builder.roles(roles.stream().map(RoleDO::getRoleCode).toList());
            } else {
                builder.roles(Collections.emptyList());
            }
        } catch (Exception ignore) {
            builder.roles(Collections.emptyList());
        }

        // 权限编码列表
        try {
            List<String> perms = permissionService.listPermCodesByUserId(user.getId());
            builder.permissions(perms == null ? Collections.emptyList() : perms);
        } catch (Exception ignore) {
            builder.permissions(Collections.emptyList());
        }

        // P1-6 修复: 数据权限上下文 (deptId/deptIds/customDeptIds/dataScope)
        // 这些字段将写入 JWT，下游服务通过 AuthInterceptor 解析还原 DataScopeContext
        builder.dataScope(user.getDataScope())
                .deptId(user.getDeptId())
                .customDeptIds(parseCustomDeptIds(user.getCustomDeptIds()))
                .deptIds(resolveDeptIds(user.getDeptId()));

        return builder.build();
    }

    /**
     * P1-6 修复: 解析 CUSTOM 模式自定义部门 ID 集
     *
     * <p>UserAccountDO.customDeptIds 为逗号分隔字符串（如 "1,3,5"），解析为 List&lt;String&gt;。
     * 解析失败时跳过非法值并打印告警，避免登录流程中断。
     *
     * @param customDeptIds 逗号分隔的部门 ID 字符串
     * @return String 列表，为空时返回 null（不写入 JWT）
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
     * 含当前部门及所有下级部门。利用 DepartmentService 缓存，避免每次请求查库。
     * 查询失败时退化为仅当前部门，保证登录流程不中断。
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
            log.warn("[Auth] 计算 deptIds 失败 deptId={} reason={}，退化为仅当前部门",
                    deptId, e.getMessage());
            return List.of(deptId);
        }
    }

    /**
     * 校验图形验证码
     *
     * @param key  验证码 Key
     * @param code 用户输入的验证码
     * @throws SysException 当验证码为空、已过期或错误时抛出
     */
    private void validateCaptcha(String key, String code) {
        if (key == null || key.isBlank() || code == null || code.isBlank()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.auth.msg_e7006630");
        }
        String stored = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + key);
        if (stored == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.auth.msg_ffa59696");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.auth.msg_08e91fbb");
        }
        // 一次性使用
        redisTemplate.delete(CAPTCHA_KEY_PREFIX + key);
    }

    /**
     * 记录登录失败次数，达到阈值时触发账号锁定
     *
     * @param username 用户名
     */
    private void recordLoginFailure(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(LOGIN_LOCK_MINUTES));
        if (count != null && count >= LOGIN_FAIL_THRESHOLD) {
            log.warn("[Auth] 账号 {} 登录失败 {} 次, 达到阈值触发锁定", username, count);
            // 落库锁定: 设置 locked_until = now + 30min
            UserAccountDO user = userAccountService.findByUsername(username);
            if (user == null) {
                log.warn("[Auth] 锁定失败: 用户不存在 username={}", username);
                return;
            }
            LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(LOGIN_LOCK_MINUTES);
            userAccountService.lockAccount(user.getId(), lockedUntil);
            log.warn("[Auth] 账号已锁定 userId={} username={} lockedUntil={} (到期后自动解锁)",
                    user.getId(), username, lockedUntil);
            // 发布账号锁定事件, 触发异步通知（邮件/短信/站内信）
            try {
                AccountLockedEvent event = AccountLockedEvent.builder()
                        .userId(user.getId())
                        .username(username)
                        .lockedUntil(lockedUntil)
                        .failCount(count.intValue())
                        .lockMinutes((int) LOGIN_LOCK_MINUTES)
                        .traceId(TraceIdUtil.get())
                        .tenantId(TenantContext.getTenantId())
                        .lockedAt(System.currentTimeMillis())
                        .build();
                publisher.publishEvent(event);
            } catch (Exception ex) {
                log.warn("[Auth] 发布账号锁定事件失败 username={} reason={}", username, ex.getMessage());
            }
        }
    }

    /**
     * 清除登录失败计数（登录成功后调用）
     *
     * @param username 用户名
     */
    private void clearLoginFailure(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
    }

}
