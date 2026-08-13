package com.njydsz.common.tenant.provisioning;

/**
 * 租户生命周期钩子接口。
 *
 * <p>定义租户注册/暂停/恢复/下线各阶段需要执行的初始化或清理操作。
 * 业务模块可实现此接口以注册自定义的租户生命周期回调。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li><b>onRegister</b> — 创建租户默认角色/权限、初始化 Schema、预热缓存</li>
 *   <li><b>onSuspend</b> — 暂停期间保留数据但禁止访问、释放临时资源</li>
 *   <li><b>onResume</b> — 恢复过期缓存、重新启用定时任务</li>
 *   <li><b>onDelete</b> — 清理租户所有数据、归档至冷存储</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Component
 * public class OrderModuleProvisioningHook implements TenantProvisioningHook {
 *
 *     \@Override
 *     public void onRegister(String tenantId) {
 *         orderConfigService.createDefaults(tenantId);
 *     }
 *
 *     \@Override
 *     public void onDelete(String tenantId) {
 *         orderArchiveService.archiveAll(tenantId);
 *     }
 *
 *     \@Override
 *     public void onSuspend(String tenantId) {
 *         cronJobService.pauseTenantJobs(tenantId);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public interface TenantProvisioningHook {

    /**
     * 租户注册时调用。
     *
     * @param tenantId 租户 ID
     */
    default void onRegister(String tenantId) {
    }

    /**
     * 租户暂停时调用。
     *
     * @param tenantId 租户 ID
     */
    default void onSuspend(String tenantId) {
    }

    /**
     * 租户恢复时调用。
     *
     * @param tenantId 租户 ID
     */
    default void onResume(String tenantId) {
    }

    /**
     * 租户下线/删除时调用。
     *
     * @param tenantId 租户 ID
     */
    default void onDelete(String tenantId) {
    }

    /**
     * 执行顺序（越小越先执行）。
     *
     * @return 排序值，默认 100
     */
    default int getOrder() {
        return 100;
    }
}
