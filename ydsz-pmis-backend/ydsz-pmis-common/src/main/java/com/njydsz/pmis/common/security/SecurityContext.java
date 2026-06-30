package com.njydsz.pmis.common.security;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;

/**
 * 登录用户上下文（ThreadLocal）
 *
 * <p>在网关 / 拦截器中解析 Token 后 setCurrent()，业务层通过 getCurrent() 获取。
 * 必须在 finally 中 clear()，避免线程复用导致内存泄漏。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class SecurityContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private SecurityContext() {
    }

    /**
     * 设置当前登录用户
     */
    public static void setCurrent(LoginUser user) {
        CONTEXT.set(user);
    }

    /**
     * 获取当前登录用户
     *
     * @throws BizException 未登录时抛出
     */
    public static LoginUser getCurrent() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new BizException(BizErrorCode.UNAUTHORIZED, "用户未登录");
        }
        return user;
    }

    /**
     * 获取当前登录用户（允许为空）
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
     * 当前用户 ID
     */
    public static Long getUserId() {
        return getCurrent().getUserId();
    }

    /**
     * 当前用户名
     */
    public static String getUsername() {
        return getCurrent().getUsername();
    }

    /**
     * 当前部门 ID
     */
    public static Long getDeptId() {
        return getCurrent().getDeptId();
    }

    /**
     * 校验权限
     */
    public static void requirePermission(String perm) {
        LoginUser user = getCurrent();
        if (!user.hasPermission(perm)) {
            throw new BizException(BizErrorCode.FORBIDDEN, "无权限: " + perm);
        }
    }

    /**
     * 校验任一权限
     */
    public static void requireAnyPermission(String... perms) {
        LoginUser user = getCurrent();
        for (String p : perms) {
            if (user.hasPermission(p)) {
                return;
            }
        }
        throw new BizException(BizErrorCode.FORBIDDEN, "无权限");
    }
}
