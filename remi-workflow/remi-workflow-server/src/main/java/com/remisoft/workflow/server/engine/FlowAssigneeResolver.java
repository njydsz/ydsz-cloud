package com.remisoft.workflow.server.engine;

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
 * @since 1.0.0
 * @author remi-team
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
    default List<String> getRoleCodes(String userId) {
        return List.of();
    }

    /**
     * 查询用户的部门 ID 列表（用于待办匹配）
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表
     */
    default List<String> getDeptIds(String userId) {
        return List.of();
    }

    /**
     * P2-39: 展开多级上级（连续 N 级主管）
     *
     * <p>从指定用户开始逐级向上查找上级，返回各级上级的用户 ID 列表。
     * 例如 userId=1001, levels=3 → [直属上级, 上上级, 上上上级]
     *
     * @param userId    起始用户 ID（通常为发起人）
     * @param levels    向上级数（≥1）
     * @param variables 流程变量（可用于动态解析）
     * @return 多级上级用户 ID 列表（空列表表示无法展开，引擎将原样保留）
     */
    default List<Long> expandMultiLeader(String userId, int levels, Map<String, Object> variables) {
        return List.of();
    }

    /**
     * P1-5: 查询部门负责人
     *
     * <p>将部门 ID 解析为该部门的负责人用户 ID。
     * 业务方需提供实现：查询部门主表的 leader_user_id 字段。
     *
     * @param deptId    部门 ID
     * @param variables 流程变量（可用于动态解析）
     * @return 部门负责人用户 ID（null 表示无法解析，引擎将原样保留 deptId）
     */
    default Long expandDeptLeader(String deptId, Map<String, Object> variables) {
        return null;
    }
}
