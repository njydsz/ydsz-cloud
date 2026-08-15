package com.njydsz.common.auth.hierarchy;

import java.util.Set;

/**
 * 权限继承层级管理器。
 *
 * <p>支持权限树继承：拥有父权限自动拥有子权限。
 * 例如：拥有 {@code sys:user} 自动拥有 {@code sys:user:list}、{@code sys:user:add} 等。
 *
 * <p>使用 {@link Map} 而非字符串拼接，实现 O(1) 查找。
 *
 * <p><b>迁移说明：</b>此类现在作为静态门面（Facade）委托给 Spring Bean
 * {@link PermissionHierarchyService}。新代码建议直接注入 {@link PermissionHierarchyService}
 * 以支持按租户隔离。静态方法仅向后兼容，使用默认租户 {@link PermissionHierarchyService#DEFAULT_TENANT_ID}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class PermissionHierarchy {

    /**
     * 服务引用，由 Spring 配置在启动时注入。
     * volatile 保证多线程可见性。
     */
    private static volatile PermissionHierarchyService service;

    private PermissionHierarchy() {
    }

    /**
     * 设置 {@link PermissionHierarchyService} 引用。
     *
     * <p>由 Spring 自动配置调用，将 {@link PermissionHierarchyService} Bean 注入静态门面。
     *
     * @param servicePermissionHierarchyService 实例，不可为 null
     */
    static void setService(PermissionHierarchyService service) {
        PermissionHierarchy.service = service;
    }

    /**
     * 获取 {@link PermissionHierarchyService} 实例。
     *
     * @return 服务实例
     * @throws IllegalStateException 如果服务未初始化
     */
    static PermissionHierarchyService getService() {
        PermissionHierarchyService s = service;
        if (s == null) {
            throw new IllegalStateException(
                    "PermissionHierarchyService 未初始化，请检查 Spring 自动配置是否正确加载");
        }
        return s;
    }

    /**
     * 注册权限继承关系。
     *
     * <p>委托给 {@link PermissionHierarchyService#registerPermission}，使用默认租户。
     *
     * @param parent   父权限码
     * @param children 子权限码列表
     */
    public static void register(String parent, String... children) {
        getService().registerPermission(PermissionHierarchyService.DEFAULT_TENANT_ID, parent, children);
    }

    /**
     * 判断用户是否拥有指定权限（考虑权限继承）。
     *
     * <p>如果用户直接拥有该权限，返回 true。
     * 如果用户拥有该权限的某个父级权限，也返回 true（递归检查）。
     *
     * <p>委托给 {@link PermissionHierarchyService#hasPermission}，使用默认租户。
     *
     * @param granted         用户已授权的权限集合
     * @param required        需要校验的权限码
     * @param wildcardEnabled 是否启用通配符匹配
     * @return 拥有权限返回 true
     */
    public static boolean hasPermission(Set<String> granted, String required, boolean wildcardEnabled) {
        return getService().hasPermission(PermissionHierarchyService.DEFAULT_TENANT_ID, granted, required, wildcardEnabled);
    }

    /**
     * 获取指定权限的所有子权限（递归）。
     *
     * <p>委托给 {@link PermissionHierarchyService#getImpliedPermissions}，使用默认租户。
     *
     * @param parent 父权限码
     * @return 所有子权限集合
     */
    public static Set<String> getAllChildren(String parent) {
        return getService().getImpliedPermissions(PermissionHierarchyService.DEFAULT_TENANT_ID, parent);
    }

    /**
     * 清空所有权限继承关系。
     *
     * <p>委托给 {@link PermissionHierarchyService#clear()}。
     */
    public static void clear() {
        getService().clear();
    }

    /**
     * 获取已注册的父权限数量。
     *
     * <p>委托给 {@link PermissionHierarchyService#getRegisteredParentCount}，使用默认租户。
     *
     * @return 父权限数量
     */
    public static int getRegisteredParentCount() {
        return getService().getRegisteredParentCount(PermissionHierarchyService.DEFAULT_TENANT_ID);
    }
}
