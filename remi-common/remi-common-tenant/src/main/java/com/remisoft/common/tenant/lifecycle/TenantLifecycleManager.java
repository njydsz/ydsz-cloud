package com.remisoft.common.tenant.lifecycle;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.remisoft.common.jdbc.exception.TenantIsolationException;
import com.remisoft.common.tenant.TenantContextHolder;

/**
 * 租户生命周期管理器。
 *
 * <p>管理租户的上下线状态：
 * <ul>
 *   <li>{@link TenantStatus#ACTIVE} — 正常接受请求</li>
 *   <li>{@link TenantStatus#SUSPENDED} — 暂停，拒绝该租户所有请求（欠费/违规）</li>
 *   <li>{@link TenantStatus#OFFLINE} — 下线，数据保留但不接受请求</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 暂停租户
 * TenantLifecycleManager.suspend("tenant_001", "欠费暂停");
 *
 * // 恢复租户
 * TenantLifecycleManager.activate("tenant_001");
 *
 * // 检查状态（在 WebFilter 或拦截器中调用）
 * if (!TenantLifecycleManager.isActive("tenant_001")) {
 *     throw new TenantSuspendedException("租户已暂停");
 * }
 * }</pre>
 *
 * <p><b>实现说明：</b>当前版本使用内存 Map 存储状态，
 * 生产环境应对接 {@code remi_tenant} 表 + Redis 缓存。
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class TenantLifecycleManager {

    private static final Map<String, TenantStatus> STATUS_MAP = new ConcurrentHashMap<>();
    private static final Set<String> SUSPENDED_SET = ConcurrentHashMap.newKeySet();

    private TenantLifecycleManager() {
    }

    /**
     * 检查租户是否活跃。
     *
     * @param tenantId 租户 ID
     * @return true=活跃
     */
    public static boolean isActive(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        TenantStatus status = STATUS_MAP.get(tenantId);
        return status == null || status == TenantStatus.ACTIVE;
    }

    /**
     * 检查当前请求的租户是否活跃。
     *
     * @return true=活跃
     * @throws TenantIsolationException 租户已暂停/下线时抛出
     */
    public static boolean checkCurrentTenantActive() {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return true; // 无租户上下文，由 SQL 拦截器 fail-closed 处理
        }
        if (SUSPENDED_SET.contains(tenantId)) {
            throw new TenantIsolationException(
                "租户 [" + tenantId + "] 已被暂停，拒绝执行请求。");
        }
        return true;
    }

    /**
     * 激活租户。
     *
     * @param tenantId 租户 ID
     */
    public static void activate(String tenantId) {
        STATUS_MAP.put(tenantId, TenantStatus.ACTIVE);
        SUSPENDED_SET.remove(tenantId);
    }

    /**
     * 暂停租户。
     *
     * @param tenantId 租户 ID
     * @param reason  暂停原因
     */
    public static void suspend(String tenantId, String reason) {
        STATUS_MAP.put(tenantId, TenantStatus.SUSPENDED);
        SUSPENDED_SET.add(tenantId);
    }

    /**
     * 下线租户。
     *
     * @param tenantId 租户 ID
     */
    public static void offline(String tenantId) {
        STATUS_MAP.put(tenantId, TenantStatus.OFFLINE);
        SUSPENDED_SET.add(tenantId);
    }

    /**
     * 获取租户状态。
     *
     * @param tenantId 租户 ID
     * @return 状态，未注册返回 null
     */
    public static TenantStatus getStatus(String tenantId) {
        return STATUS_MAP.get(tenantId);
    }

    /**
     * 注册租户状态（批量初始化用）。
     *
     * @param tenantId 租户 ID
     * @param status  状态
     */
    public static void register(String tenantId, TenantStatus status) {
        STATUS_MAP.put(tenantId, status);
        if (status == TenantStatus.SUSPENDED || status == TenantStatus.OFFLINE) {
            SUSPENDED_SET.add(tenantId);
        } else {
            SUSPENDED_SET.remove(tenantId);
        }
    }
}
