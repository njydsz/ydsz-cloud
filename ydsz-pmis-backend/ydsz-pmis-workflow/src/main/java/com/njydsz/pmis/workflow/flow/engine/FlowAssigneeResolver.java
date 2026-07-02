package com.njydsz.pmis.workflow.flow.engine;

import java.util.List;
import java.util.Map;

/**
 * 办理人解析 SPI
 *
 * <p>业务模块（如 user 模块）可提供实现，将 ROLE/DEPT/LEADER/POSITION
 * 展开为具体用户列表。引擎在创建任务时调用本接口展开多人会签/或签。
 *
 * <p>未提供实现时，引擎将 assigneeId 原样保留，待办查询按字符串匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface FlowAssigneeResolver {

    /**
     * 将权限标识展开为具体用户 ID 列表
     *
     * @param permissionFlag 权限标识，如 role:hr / dept:10 / leader:1001 / position:PM
     * @param variables      流程变量（可用于动态解析）
     * @return 用户 ID 列表（空列表表示无法展开，引擎将原样保留）
     */
    List<Long> expandUsers(String permissionFlag, Map<String, Object> variables);

    /**
     * 查询用户的角色编码列表（用于待办匹配）
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    default List<String> getRoleCodes(Long userId) {
        return List.of();
    }

    /**
     * 查询用户的部门 ID 列表（用于待办匹配）
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表
     */
    default List<String> getDeptIds(Long userId) {
        return List.of();
    }
}
