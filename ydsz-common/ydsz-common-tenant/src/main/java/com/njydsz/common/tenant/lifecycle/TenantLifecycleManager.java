package com.njydsz.common.tenant.lifecycle;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import com.njydsz.common.jdbc.exception.TenantIsolationException;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 租户生命周期管理器接口。
 *
 * <p>定义了租户状态管理的两个核心方法：
 * <ul>
 *   <li>{@link #isActive(String)} — 检查租户是否活跃</li>
 *   <li>{@link #checkCurrentTenantActive()} — 校验当前请求租户是否活跃，不活跃则抛出异常</li>
 * </ul>
 *
 * <p>提供两种实现：
 * <ul>
 *   <li>{@link InMemoryTenantLifecycleManager} — 本地内存存储，适用于单实例开发环境</li>
 *   <li>{@link RedisTenantLifecycleManager} — Redis 分布式存储，适用于多实例生产环境</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在 WebFilter / Interceptor 中检查租户状态
 * if (!tenantLifecycleManager.isActive("tenant_001")) {
 *     throw new TenantIsolationException("租户已暂停或下线");
 * }
 *
 * // 或在拦截器中直接校验当前租户
 * tenantLifecycleManager.checkCurrentTenantActive();
 * }</pre>
 *
 * <h3>注册租户状态</h3>
 * <pre>{@code
 * // 新租户注册
 * TenantLifecycleManager.register("tenant_001", TenantStatus.ACTIVE);
 *
 * // 欠费暂停
 * TenantLifecycleManager.suspend("tenant_001", "欠费暂停");
 *
 * // 到期下线
 * TenantLifecycleManager.offline("tenant_001");
 *
 * // 恢复服务
 * TenantLifecycleManager.activate("tenant_001");
 * }</pre>
 *
 * <p><b>配置说明：</b>通过 {@code ydsz.tenant.lifecycle.storage} 选择存储后端：
 * <ul>
 *   <li>{@code memory} — 本地内存（默认，适合单实例开发）</li>
 *   <li>{@code redis} — Redis 共享存储（生产推荐，自动启用当 common-redis 在 classpath 时）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see TenantStatus
 * @see InMemoryTenantLifecycleManager
 * @see RedisTenantLifecycleManager
 */
public interface TenantLifecycleManager {

    // ========== 实例访问（静态委托方法，保持向后兼容） ==========

    /**
     * 获取当前 Spring 容器中的管理器实例。
     *
     * <p>通过 {@link ApplicationContext} 静态持有者延迟获取 Bean，
     * 避免循环依赖。
     *
     * @return 管理器实例
     */
    static TenantLifecycleManager getInstance() {
        return LifecycleManagerHolder.getInstance();
    }

    // ========== 向后兼容的静态方法 ==========

    static boolean isActive(String tenantId) {
        return getInstance().checkIsActive(tenantId);
    }

    static boolean checkCurrentTenantActive() {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return true;
        }
        if (!getInstance().checkIsActive(tenantId)) {
            throw new TenantIsolationException(buildSuspendedMessage(tenantId));
        }
        return true;
    }

    static void activate(String tenantId) {
        getInstance().doActivate(tenantId);
    }

    static void suspend(String tenantId, String reason) {
        getInstance().doSuspend(tenantId, reason);
    }

    static void offline(String tenantId) {
        getInstance().doOffline(tenantId);
    }

    static TenantStatus getStatus(String tenantId) {
        return getInstance().doGetStatus(tenantId);
    }

    static void register(String tenantId, TenantStatus status) {
        getInstance().doRegister(tenantId, status);
    }

    // ========== 实例方法（实现类覆盖） ==========

    default boolean checkIsActive(String tenantId) {
        if (tenantId == null) return false;
        TenantStatus status = doGetStatus(tenantId);
        return status == null || status == TenantStatus.ACTIVE;
    }

    void doActivate(String tenantId);

    void doSuspend(String tenantId, String reason);

    void doOffline(String tenantId);

    TenantStatus doGetStatus(String tenantId);

    void doRegister(String tenantId, TenantStatus status);

    /**
     * 批量注册（应用启动时初始化所有租户状态）。
     *
     * @param entries 租户 ID → 状态映射
     */
    void doRegisterAll(Map<String, TenantStatus> entries);

    /**
     * 返回是否为分布式存储后端（Redis）。
     *
     * @return true=分布式（多实例共享），false=本地内存
     */
    default boolean isDistributed() {
        return false;
    }

    // ========== 内部工具 ==========

    private static String buildSuspendedMessage(String tenantId) {
        TenantStatus status = getInstance().doGetStatus(tenantId);
        String statusLabel = status != null ? status.name() : "UNKNOWN";
        return String.format("租户 [%s] 当前状态=%s，拒绝执行请求。"
                + "如需恢复请联系管理员执行 TenantLifecycleManager.activate(\"%s\")",
                tenantId, statusLabel, tenantId);
    }

    /**
     * Spring ApplicationContext 静态持有者（延迟获取）。
     */
    final class LifecycleManagerHolder {

        private static volatile ApplicationContext applicationContext;

        private LifecycleManagerHolder() {
        }

        static void init(ApplicationContext ctx) {
            applicationContext = ctx;
        }

        static TenantLifecycleManager getInstance() {
            if (applicationContext == null) {
                // 未初始化时回退到纯内存实现（开发期兼容）
                return InMemoryTenantLifecycleManager.INSTANCE;
            }
            try {
                return applicationContext.getBean(TenantLifecycleManager.class);
            } catch (Exception e) {
                return InMemoryTenantLifecycleManager.INSTANCE;
            }
        }
    }
}
