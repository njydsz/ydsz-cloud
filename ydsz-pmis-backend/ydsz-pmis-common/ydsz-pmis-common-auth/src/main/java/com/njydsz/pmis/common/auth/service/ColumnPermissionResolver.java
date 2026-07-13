package com.njydsz.pmis.common.auth.service;

import com.njydsz.pmis.common.auth.model.ColumnScopeInfo;
import com.njydsz.pmis.common.auth.service.impl.RedisRoleColumnPermissionResolver;

/**
 * 列权限解析器接口。
 *
 * <p>负责根据当前调用链上下文解析"当前用户在当前请求中的列可见/可编辑规则"。
 * 默认实现基于 accessToken -> roleCode -> role-col-key 的 Redis 解析链路。
 *
 * <p><b>实现类注意事项：</b>
 * <ul>
 *   <li>解析结果应考虑多角色合并场景</li>
 *   <li>应对解析结果做本地 TTL 缓存，降低 Redis 访问频率</li>
 *   <li>解析失败时应返回空对象而非 null</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see ColumnScopeInfo
 * @see RedisRoleColumnPermissionResolver
 */
public interface ColumnPermissionResolver {

    /**
     * 解析当前用户的列权限作用域。
     *
     * @return 列权限作用域；无规则时返回 {@link ColumnScopeInfo#empty()}
     */
    ColumnScopeInfo resolve();
}
