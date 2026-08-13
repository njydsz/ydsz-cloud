package com.njydsz.common.tenant.lifecycle;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 内存版租户生命周期管理器。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储租户状态，适合单实例开发环境。
 * 多实例部署时各节点状态独立，生产环境请使用 {@link RedisTenantLifecycleManager}。
 *
 * <p><b>自动装配逻辑：</b>当 classpath 中不存在 {@code StringRedisTemplate} 时，
 * 此实现作为 {@code @Primary} Bean 启用。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see TenantLifecycleManager
 */
@Component
@Primary
public class InMemoryTenantLifecycleManager implements TenantLifecycleManager {

    /** 未初始化时的回退单例（Spring 容器启动前使用） */
    static final InMemoryTenantLifecycleManager INSTANCE = new InMemoryTenantLifecycleManager();

    private final Map<String, TenantStatus> statusMap = new ConcurrentHashMap<>();
    private final Set<String> suspendedSet = ConcurrentHashMap.newKeySet();

    @Override
    public void doActivate(String tenantId) {
        statusMap.put(tenantId, TenantStatus.ACTIVE);
        suspendedSet.remove(tenantId);
    }

    @Override
    public void doSuspend(String tenantId, String reason) {
        statusMap.put(tenantId, TenantStatus.SUSPENDED);
        suspendedSet.add(tenantId);
    }

    @Override
    public void doOffline(String tenantId) {
        statusMap.put(tenantId, TenantStatus.OFFLINE);
        suspendedSet.add(tenantId);
    }

    @Override
    public TenantStatus doGetStatus(String tenantId) {
        return statusMap.get(tenantId);
    }

    @Override
    public void doRegister(String tenantId, TenantStatus status) {
        statusMap.put(tenantId, status);
        if (status == TenantStatus.SUSPENDED || status == TenantStatus.OFFLINE) {
            suspendedSet.add(tenantId);
        } else {
            suspendedSet.remove(tenantId);
        }
    }

    @Override
    public void doRegisterAll(Map<String, TenantStatus> entries) {
        if (entries == null) return;
        entries.forEach(this::doRegister);
    }

    @Override
    public boolean checkIsActive(String tenantId) {
        if (tenantId == null) return false;
        TenantStatus status = statusMap.get(tenantId);
        return status == null || status == TenantStatus.ACTIVE;
    }
}
