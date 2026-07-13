package com.njydsz.pmis.common.auth.service;

import com.njydsz.pmis.common.auth.model.DataScopeInfo;
import com.njydsz.pmis.common.auth.service.impl.RedisRoleDataPermissionResolver;

/**
 * 数据权限解析器接口。
 *
 * <p>负责根据当前调用链上下文解析"当前用户可访问的数据范围"。
 * 默认实现基于 accessToken -> roleCode -> role-row-key 的 Redis 解析链路。
 *
 * <p><b>数据权限维度：</b>
 * <ul>
 *   <li>租户维度（TENANT）：按租户隔离数据</li>
 *   <li>集团维度（GROUP）：可访问集团下所有公司数据</li>
 *   <li>公司维度（COMPANY）：可访问公司及下属部门数据</li>
 *   <li>部门维度（DEPT）：可访问本部门及下级部门数据</li>
 *   <li>用户维度（USER）：仅可访问自己的数据</li>
 *   <li>项目维度（PROJECT）：可访问有权限的项目数据</li>
 *   <li>区域维度（REGION）：可访问有权限的区域数据</li>
 * </ul>
 *
 * <p><b>实现类注意事项：</b>
 * <ul>
 *   <li>解析结果应考虑多角色合并场景，取权限范围最大的</li>
 *   <li>应对解析结果做本地 TTL 缓存，降低 Redis 访问频率</li>
 *   <li>解析失败时应返回空对象而非 null</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see com.njydsz.pmis.common.auth.model.DataScopeInfo
 * @see RedisRoleDataPermissionResolver
 */
public interface DataPermissionResolver {

    /**
     * 解析当前用户的数据权限范围。
     *
     * @return 数据权限范围信息；无规则时返回 {@link DataScopeInfo#empty()}
     */
    DataScopeInfo resolve();
}
