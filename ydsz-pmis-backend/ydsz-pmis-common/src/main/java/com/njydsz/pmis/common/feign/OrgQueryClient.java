package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 组织架构查询 Feign 客户端（P1-5）
 *
 * <p>供 workflow 服务调用 userinfo 服务，将 BPMN 中的角色/部门审批人标识
 * 展开为具体用户 ID 列表。{@link OrgQueryClientFallbackFactory} 保证
 * userinfo 不可用时主流程不被阻塞，回退到空列表（交由 emptyStrategy 兜底）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@FeignClient(
        name = FeignClientConstants.USERINFO,
        contextId = "orgQueryClient",
        path = "/feign/org",
        fallbackFactory = OrgQueryClientFallbackFactory.class
)
public interface OrgQueryClient {

    /**
     * 根据角色编码查询启用状态的用户 ID 列表
     *
     * @param roleCode 角色编码
     * @return 用户 ID 列表（无匹配时返回空列表）
     */
    @GetMapping("/usersByRole")
    Result<List<Long>> listUserIdsByRoleCode(@RequestParam("roleCode") String roleCode);

    /**
     * 根据部门 ID 查询部门负责人用户 ID
     *
     * @param deptId 部门 ID
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    @GetMapping("/deptLeader")
    Result<String> getDeptLeaderByDeptId(@RequestParam("deptId") Long deptId);

    /**
     * 根据部门编码查询部门负责人用户 ID
     *
     * @param deptCode 部门编码
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    @GetMapping("/deptLeaderByCode")
    Result<String> getDeptLeaderByDeptCode(@RequestParam("deptCode") String deptCode);

    /**
     * 查询用户拥有的角色编码列表（用于待办反查）
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    @GetMapping("/userRoleCodes")
    Result<List<String>> listRoleCodesByUserId(@RequestParam("userId") String userId);

    /**
     * 根据用户 ID 查询其所属部门 ID 列表（用于待办反查）
     *
     * <p>当前用户表无 deptId 字段，返回空列表；待 P2-2 候选人/变量独立表落地后补全。
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表（字符串形式，便于 permissionFlag 字符串匹配）
     */
    @GetMapping("/userDeptIds")
    Result<List<String>> listDeptIdsByUserId(@RequestParam("userId") String userId);

    /**
     * P2-2: 根据部门 ID 查询启用状态的用户 ID 列表
     *
     * @param deptId 部门 ID
     * @return 用户 ID 列表
     */
    @GetMapping("/usersByDept")
    Result<List<Long>> listUserIdsByDeptId(@RequestParam("deptId") Long deptId);

    /**
     * P2-2: 根据岗位编码查询启用状态的用户 ID 列表
     *
     * @param positionCode 岗位编码
     * @return 用户 ID 列表
     */
    @GetMapping("/usersByPosition")
    Result<List<Long>> listUserIdsByPositionCode(@RequestParam("positionCode") String positionCode);

    /**
     * P2-2: 根据用户 ID 查询直属上级用户 ID
     *
     * @param userId 用户 ID
     * @return 直属上级用户 ID，未设置时返回 null
     */
    @GetMapping("/leaderByUser")
    Result<String> getLeaderByUserId(@RequestParam("userId") String userId);
}
