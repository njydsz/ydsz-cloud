package com.njydsz.pmis.common.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.context.RequestContext;
import com.njydsz.pmis.common.exception.BizException;

/**
 * 登录用户上下文（TransmittableThreadLocal）
 *
 * <p>在网关 / 拦截器中解析 Token 后 setCurrent()，业务层通过 getCurrent() 获取。
 * 使用 {@link TransmittableThreadLocal} 替代普通 ThreadLocal，
 * 支持 {@code @Async}、线程池、CompletableFuture 等异步场景的上下文传递。
 *
 * <p>同时同步关键信息到 {@link RequestContext}，便于跨模块统一访问。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class SecurityContext {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    private SecurityContext() {
    }

    /**
     * 设置当前登录用户
     *
     * @param user 登录用户
     */
    public static void setCurrent(LoginUser user) {
        CONTEXT.set(user);
        // 同步关键信息到 RequestContext
        if (user != null) {
            RequestContext.ContextData ctx = RequestContext.getOrDefault();
            ctx.setUserId(user.getUserId());
            ctx.setUsername(user.getUsername());
            ctx.setRealName(user.getRealName());
            ctx.setDeptId(user.getDeptId());
            if (user.getTenantId() != null) {
                ctx.setTenantId(user.getTenantId());
            }
        }
    }

    /**
     * 获取当前登录用户
     *
     * @return 当前登录用户
     * @throws BizException 未登录时抛出
     */
    public static LoginUser getCurrent() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new BizException(BizErrorCode.UNAUTHORIZED, "error.common.msg_1923bd82");
        }
        return user;
    }

    /**
     * 获取当前登录用户（允许为空）
     *
     * @return 当前登录用户；未登录时返回 null
     */
    public static LoginUser getCurrentOrNull() {
        return CONTEXT.get();
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 当前用户 ID（雪花算法字符串）
     *
     * @return 当前用户 ID
     */
    public static String getUserId() {
        return getCurrent().getUserId();
    }

    /**
     * 当前用户名
     *
     * @return 当前用户名
     */
    public static String getUsername() {
        return getCurrent().getUsername();
    }

    /**
     * 当前部门 ID（雪花算法字符串）
     *
     * @return 当前部门 ID
     */
    public static String getDeptId() {
        return getCurrent().getDeptId();
    }

    /**
     * 当前租户 ID（P2-16：多租户上下文）
     *
     * <p>从登录用户上下文获取 tenantId。未登录或上下文为空时返回默认值 "1"。
     * 适用于后台任务、单元测试等无 HTTP 请求上下文的场景。
     *
     * @return 当前租户 ID；未登录时返回 "1"
     */
    public static String getTenantIdOrDefault() {
        return getTenantIdOrDefault("1");
    }

    /**
     * 当前租户 ID（带自定义默认值）
     *
     * @param defaultTenantId 默认租户 ID（未登录时使用）
     * @return 当前租户 ID；未登录时返回 defaultTenantId
     */
    public static String getTenantIdOrDefault(String defaultTenantId) {
        LoginUser user = getCurrentOrNull();
        if (user == null || user.getTenantId() == null || user.getTenantId().isEmpty()) {
            return defaultTenantId == null || defaultTenantId.isEmpty() ? "1" : defaultTenantId;
        }
        return user.getTenantId();
    }

    /**
     * 校验权限
     *
     * @param perm 权限编码
     * @throws BizException 无权限时抛出
     */
    public static void requirePermission(String perm) {
        LoginUser user = getCurrent();
        if (!user.hasPermission(perm)) {
            throw new BizException(BizErrorCode.FORBIDDEN, "error.common.msg_1e40057e", perm);
        }
    }

    /**
     * 校验任一权限
     *
     * @param perms 权限编码列表
     * @throws BizException 全部权限都不拥有时抛出
     */
    public static void requireAnyPermission(String... perms) {
        LoginUser user = getCurrent();
        for (String p : perms) {
            if (user.hasPermission(p)) {
                return;
            }
        }
        throw new BizException(BizErrorCode.FORBIDDEN, "error.common.msg_ad4fff48");
    }
}
