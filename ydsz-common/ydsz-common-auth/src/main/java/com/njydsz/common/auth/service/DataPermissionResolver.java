package com.njydsz.common.auth.service;

import com.njydsz.common.auth.model.DataScopeInfo;
import com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver;

/**
 * 数据权限解析器接口。
 *
 * <p>负责根据当前调用链上下文解析"当前用户可访问的数据范围"。
 * 默认实现基于 accessToken -> roleCode -> role-row-key 的 Redis 解析链路。
 *
 * <p><b>废弃原因：</b>与 {@link RolePermissionLoader} 职责重叠，
 * 数据权限解析建议通过 {@link RolePermissionLoader} 或独立数据权限服务实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             迁移目标：{@link RolePermissionLoader} 或独立数据权限服务。
 *
 * @see DataScopeInfo
 * @see RedisRoleDataPermissionResolver
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public interface DataPermissionResolver {

    /**
     * 解析当前用户的数据权限范围。
     *
     * @return 数据权限范围信息；无规则时返回 {@link DataScopeInfo#empty()}
     */
    DataScopeInfo resolve();
}
