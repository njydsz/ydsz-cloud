package com.njydsz.common.search.provider;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 搜索提供者上下文
 * <p>
 * 提供搜索时的上下文信息（用户、租户、权限等），供 {@code SearchProvider} 构建过滤条件。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Data
@Builder
public class SearchProviderContext {

    /** 当前用户 ID */
    private String userId;

    /** 当前租户 ID */
    private String tenantId;

    /** 用户角色列表 */
    private List<String> roles;

    /** 用户部门 ID */
    private String deptId;

    /** 是否管理员 */
    @Builder.Default
    private boolean admin = false;
}
