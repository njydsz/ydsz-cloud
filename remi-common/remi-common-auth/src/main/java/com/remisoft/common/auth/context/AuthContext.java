package com.remisoft.common.auth.context;

import java.util.Map;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.remisoft.common.auth.model.ColumnPermissionInfo;
import com.remisoft.common.core.context.RequestContext;
import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.common.security.LoginUser;

/**
 * 统一认证与权限上下文持有者
 *
 * <p>合并原 SecurityContext 和 ColumnPermissionContext，
 * 提供统一的线程级用户身份与权限上下文管理。
 * 使用 TransmittableThreadLocal 保证在线程池场景下的正确传递。
 *
 * <p><b>存储内容：</b>
 * <ul>
 *   <li>loginUser: 登录用户信息（userId/username/deptId/tenantId/roles/permissions 等）</li>
 *   <li>tenantId: 租户ID，用于多租户场景下的数据隔离</li>
 *   <li>columnPermission: 列权限信息，用于字段级权限控制</li>
 * </ul>
 *
 * <p><b>生命周期：</b>
 * <ul>
 *   <li>在 Filter/Interceptor 中初始化（解析 Token 后调用 {@link #setCurrent}）</li>
 *   <li>在业务逻辑中读取（如 {@link #getUserId()}, {@link #requirePermission(String)}）</li>
 *   <li>在请求结束时必须调用 {@link #clear()} 清理，防止内存泄漏</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated v1.9.0 起弃用，由 {@link com.remisoft.common.core.context.RequestContext} 统一替代
 */
@Deprecated
public final class AuthContext {

    private static final TransmittableThreadLocal<ContextData> CONTEXT = new TransmittableThreadLocal<>();

    private AuthContext() {
    }

    // ==================== LoginUser 管理 ====================

    /**
     * 设置当前登录用户
     *
     * <p>同步关键信息（userId/tenantId）到 {@link RequestContext}，便于跨模块统一访问。
     * remi-common-core 精简后 {@link RequestContext} 仅保留固定字段，
     * username/realName/deptId 等扩展信息可通过 {@link #getCurrentOrNull()} 直接获取。
     *
     * @param user 登录用户
     */
    public static void setCurrent(LoginUser user) {
        ContextData data = getOrCreate();
        data.loginUser = user;
        // 同步关键信息到 RequestContext（仅同步 RequestContext 支持的固定字段）
        if (user != null) {
            RequestContext.setLoginUser(user);
            RequestContext.setUserId(user.getUserId());
            if (user.getTenantId() != null) {
                RequestContext.setTenantId(user.getTenantId());
            }
        }
    }

    /**
     * 获取当前登录用户
     *
     * @return 当前登录用户
     * @throws SysException 未登录时抛出
     */
    public static LoginUser getCurrent() {
        LoginUser user = getCurrentOrNull();
        if (user == null) {
            throw SysException.builder()
                    .code(BaseResultCode.UNAUTHORIZED.getCode())
                    .key("error.common.msg_1923bd82")
                    .httpStatus(BaseResultCode.UNAUTHORIZED.getHttpStatusCode())
                    .build();
        }
        return user;
    }

    /**
     * 获取当前登录用户（允许为空）
     *
     * @return 当前登录用户；未登录时返回 null
     */
    public static LoginUser getCurrentOrNull() {
        ContextData data = CONTEXT.get();
        return data != null ? data.loginUser : null;
    }

    /**
     * 当前用户 ID（雪花算法字符串）
     *
     * @return 当前用户 ID
     * @throws SysException 未登录时抛出
     */
    public static String getUserId() {
        return getCurrent().getUserId();
    }

    /**
     * 当前用户名
     *
     * @return 当前用户名
     * @throws SysException 未登录时抛出
     */
    public static String getUsername() {
        return getCurrent().getUsername();
    }

    /**
     * 当前部门 ID（雪花算法字符串）
     *
     * @return 当前部门 ID
     * @throws SysException 未登录时抛出
     */
    public static String getDeptId() {
        return getCurrent().getDeptId();
    }

    /**
     * 当前租户 ID（多租户上下文）
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
     * @throws SysException 无权限时抛出
     */
    public static void requirePermission(String perm) {
        LoginUser user = getCurrent();
        if (!user.hasPermission(perm)) {
            throw SysException.builder()
                    .code(BaseResultCode.FORBIDDEN.getCode())
                    .key("error.common.msg_1e40057e")
                    .params(new Object[]{perm})
                    .httpStatus(BaseResultCode.FORBIDDEN.getHttpStatusCode())
                    .build();
        }
    }

    /**
     * 校验任一权限
     *
     * @param perms 权限编码列表
     * @throws SysException 全部权限都不拥有时抛出
     */
    public static void requireAnyPermission(String... perms) {
        LoginUser user = getCurrent();
        for (String p : perms) {
            if (user.hasPermission(p)) {
                return;
            }
        }
        throw SysException.builder()
                .code(BaseResultCode.FORBIDDEN.getCode())
                .key("error.common.msg_ad4fff48")
                .httpStatus(BaseResultCode.FORBIDDEN.getHttpStatusCode())
                .build();
    }

    // ==================== 列权限管理（原有功能） ====================

    /**
     * 获取请求级缓存的用户信息 Map。
     *
     * <p>由 RbacPermissionEvaluator.loadCurrentUserInfo() 首次加载后写入，
     * 后续同一请求内直接从 ThreadLocal 读取，避免反复 Redis 调用。
     *
     * @return 缓存的用户信息 Map，未设置时返回 null
     */
    public static Map<String, Object> getCachedUserInfoMap() {
        ContextData data = CONTEXT.get();
        return data != null ? data.cachedUserInfoMap : null;
    }

    /**
     * 设置请求级缓存的用户信息 Map。
     *
     * @param userInfoMap 用户信息 Map
     */
    public static void setCachedUserInfoMap(Map<String, Object> userInfoMap) {
        ContextData data = getOrCreate();
        data.cachedUserInfoMap = userInfoMap;
    }

    /**
     * 获取当前线程的上下文数据
     *
     * @return 上下文数据，未设置时返回 null
     */
    public static ContextData get() {
        return CONTEXT.get();
    }

    /**
     * 设置当前线程的上下文数据
     *
     * @param data 上下文数据
     */
    public static void set(ContextData data) {
        CONTEXT.set(data);
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID，未设置时返回 null
     */
    public static String getTenantId() {
        ContextData data = CONTEXT.get();
        return data != null ? data.getTenantId() : null;
    }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        ContextData data = getOrCreate();
        data.setTenantId(tenantId);
    }

    /**
     * 获取列权限信息
     *
     * @return 列权限信息，未设置时返回 null
     */
    public static ColumnPermissionInfo getColumnPermission() {
        ContextData data = CONTEXT.get();
        return data != null ? data.getColumnPermission() : null;
    }

    /**
     * 设置列权限信息
     *
     * @param columnPermission 列权限信息
     */
    public static void setColumnPermission(ColumnPermissionInfo columnPermission) {
        ContextData data = getOrCreate();
        data.setColumnPermission(columnPermission);
    }

    /**
     * 判断是否有列权限
     *
     * @return true 表示有列权限且权限信息非空
     */
    public static boolean hasColumnPermission() {
        ColumnPermissionInfo info = getColumnPermission();
        return info != null && !info.isEmpty();
    }

    /**
     * 清理当前线程的上下文数据
     *
     * <p>必须在请求结束时调用（通常由 Filter 或 Interceptor 负责），
     * 防止 ThreadLocal 内存泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
        // 同步清理 RequestContext 中的登录用户信息，避免跨模块数据泄漏
        RequestContext.remove(RequestContext.KEY_LOGIN_USER);
        RequestContext.remove(RequestContext.KEY_USER_ID);
        RequestContext.remove(RequestContext.KEY_TENANT_ID);
    }

    // ==================== 内部方法 ====================

    private static ContextData getOrCreate() {
        ContextData data = CONTEXT.get();
        if (data == null) {
            data = new ContextData();
            CONTEXT.set(data);
        }
        return data;
    }

    /**
     * 上下文数据载体
     */
    public static class ContextData {
        private LoginUser loginUser;
        private String tenantId;
        private ColumnPermissionInfo columnPermission;

        /**
         * 请求级缓存的用户信息 Map，避免同一请求内多次 Redis 调用。
         * 由 RbacPermissionEvaluator.loadCurrentUserInfo() 首次加载后写入，后续直接读取。
         */
        private Map<String, Object> cachedUserInfoMap;

        public LoginUser getLoginUser() {
            return loginUser;
        }

        public void setLoginUser(LoginUser loginUser) {
            this.loginUser = loginUser;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public ColumnPermissionInfo getColumnPermission() {
            return columnPermission;
        }

        public void setColumnPermission(ColumnPermissionInfo columnPermission) {
            this.columnPermission = columnPermission;
        }

        public Map<String, Object> getCachedUserInfoMap() {
            return cachedUserInfoMap;
        }

        public void setCachedUserInfoMap(Map<String, Object> cachedUserInfoMap) {
            this.cachedUserInfoMap = cachedUserInfoMap;
        }
    }
}
