package com.njydsz.pmis.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 数据权限上下文
 *
 * <p>基于 {@link LoginUser} 解析得到，供 DataScopeAspect / DataScopeHelper 使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据权限范围 */
    private DataScope scope;

    /**
     * 当前用户 ID（雪花算法字符串 VARCHAR(20)）。
     *
     * <p>P3-1：随主键雪花化统一为 String。
     */
    private String userId;

    /**
     * 当前部门 ID（雪花算法字符串 VARCHAR(20)）。
     */
    private String deptId;

    /**
     * 当前部门 ID 链（含所有下级，DEPT_AND_CHILD 模式）。
     */
    private List<String> deptIds;

    /**
     * 自定义部门 ID 集（CUSTOM 模式）。
     */
    private List<String> customDeptIds;

    /** 是否超管（绕过数据权限） */
    private boolean superAdmin;

    /**
     * 是否全量
     *
     * @return true 表示超管或数据范围为 ALL
     */
    public boolean isAll() {
        return superAdmin || scope == DataScope.ALL;
    }

    /**
     * 仅本人
     *
     * @return true 表示数据范围为 SELF
     */
    public boolean isSelfOnly() {
        return scope == DataScope.SELF;
    }

    /**
     * 解析当前用户的数据权限上下文
     *
     * @param user 登录用户，为 null 时返回 SELF 兜底上下文
     * @return 数据权限上下文
     */
    public static DataScopeContext from(LoginUser user) {
        if (user == null) {
            return DataScopeContext.builder()
                    .scope(DataScope.SELF)
                    .superAdmin(false)
                    .build();
        }
        boolean superAdmin = user.isSuperAdmin();
        DataScope scope = superAdmin ? DataScope.ALL : DataScope.parse(user.getDataScope());
        return DataScopeContext.builder()
                .scope(scope)
                .userId(user.getUserId())
                .deptId(user.getDeptId())
                .deptIds(user.getDeptIds())
                .customDeptIds(user.getCustomDeptIds())
                .superAdmin(superAdmin)
                .build();
    }
}
