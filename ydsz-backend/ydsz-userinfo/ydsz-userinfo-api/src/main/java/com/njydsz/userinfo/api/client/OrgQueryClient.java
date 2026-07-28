package com.njydsz.userinfo.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 组织架构查询 Feign 客户端（供跨服务调用）。
 *
 * <p>覆盖工作流引擎 {@code FeignFlowAssigneeResolver} 所需的 7 类组织关系查询：
 * <ul>
 *   <li>角色 → 用户 ID 列表（{@link #listUserIdsByRoleCode}）</li>
 *   <li>用户 → 角色编码列表（{@link #listRoleCodesByUserId}）</li>
 *   <li>用户 → 部门 ID 列表（{@link #listDeptIdsByUserId}）</li>
 *   <li>用户 → 直属上级（{@link #getLeaderByUserId}）</li>
 *   <li>岗位 → 用户 ID 列表（{@link #listUserIdsByPositionCode}）</li>
 *   <li>部门 ID → 部门负责人（{@link #getDeptLeaderByDeptId}）</li>
 *   <li>部门编码 → 部门负责人（{@link #getDeptLeaderByDeptCode}）</li>
 * </ul>
 *
 * <p>所有 ID 均为 String 类型（雪花算法字符串），与项目 ID 约定一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.USERINFO, contextId = "orgQueryClient",
        fallback = OrgQueryClientFallback.class)

/**
 * OrgQueryClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.userinfo.api.client}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface OrgQueryClient {

    @GetMapping("/api/internal/user/info")
    BaseResponse<UserAccountVO> queryUserById(@RequestParam String userId);

    @GetMapping("/api/internal/dept/tree")
    BaseResponse<List<DepartmentTreeVO>> getDeptTree();

    @GetMapping("/api/internal/dept/list")
    BaseResponse<List<DepartmentVO>> getDeptList();

    /**
     * 按角色编码查询用户 ID 列表（支持工作流 {@code role:xxx} 审批人展开）。
     *
     * @param roleCode 角色编码（如 HR/PM/SUPER_ADMIN）
     * @return 用户 ID 列表（String 形式，雪花算法字符串）
     */
    @GetMapping("/api/internal/user/list-by-role")
    BaseResponse<List<String>> listUserIdsByRoleCode(@RequestParam String roleCode);

    /**
     * 查询用户拥有的角色编码列表（工作流待办反查）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    @GetMapping("/api/internal/user/role-codes")
    BaseResponse<List<String>> listRoleCodesByUserId(@RequestParam String userId);

    /**
     * 查询用户所属部门 ID 列表（工作流待办反查，支持多部门）。
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表（String 形式）
     */
    @GetMapping("/api/internal/user/dept-ids")
    BaseResponse<List<String>> listDeptIdsByUserId(@RequestParam String userId);

    /**
     * 查询用户的直属上级 ID（支持工作流 {@code leader:xxx} 审批人展开）。
     *
     * @param userId 用户 ID
     * @return 直属上级用户 ID，未设置时返回 null
     */
    @GetMapping("/api/internal/user/leader")
    BaseResponse<String> getLeaderByUserId(@RequestParam String userId);

    /**
     * 按岗位编码查询用户 ID 列表（支持工作流 {@code position:xxx} 审批人展开）。
     *
     * @param positionCode 岗位编码（如 PM/DEV/QA）
     * @return 用户 ID 列表
     */
    @GetMapping("/api/internal/user/list-by-position")
    BaseResponse<List<String>> listUserIdsByPositionCode(@RequestParam String positionCode);

    /**
     * 按部门 ID 查询部门负责人（支持工作流 {@code dept:数字} 审批人展开）。
     *
     * @param deptId 部门 ID
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    @GetMapping("/api/internal/dept/leader-by-id")
    BaseResponse<String> getDeptLeaderByDeptId(@RequestParam String deptId);

    /**
     * 按部门编码查询部门负责人（支持工作流 {@code dept:非数字} 审批人展开）。
     *
     * @param deptCode 部门编码（如 TECH/HR）
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    @GetMapping("/api/internal/dept/leader-by-code")
    BaseResponse<String> getDeptLeaderByDeptCode(@RequestParam String deptCode);

    // ==================== NameAssembler 批量名称富化接口 ====================

    /**
     * 批量查询用户 ID → 用户真实姓名映射（供 NameAssembler 富化 userName/createdByName 等字段）。
     *
     * <p>实现走单条 SQL {@code SELECT id, real_name FROM ydsz_user_account WHERE id IN (...)}，
     * 一次 Feign 往返拿到全部结果，避免 N+1 调用。
     *
     * @param userIds 用户 ID 列表
     * @return userId → realName 映射；未命中的 userId 不出现在 Map 中
     */
    @PostMapping("/api/internal/user/batch-names")
    BaseResponse<Map<String, String>> batchUserNames(@RequestBody List<String> userIds);

    /**
     * 批量查询部门 ID → 部门名映射。
     *
     * @param deptIds 部门 ID 列表
     * @return deptId → deptName 映射
     */
    @PostMapping("/api/internal/dept/batch-names")
    BaseResponse<Map<String, String>> batchDeptNames(@RequestBody List<String> deptIds);

    /**
     * 批量查询角色 ID → 角色名映射。
     *
     * @param roleIds 角色 ID 列表
     * @return roleId → roleName 映射
     */
    @PostMapping("/api/internal/role/batch-names")
    BaseResponse<Map<String, String>> batchRoleNames(@RequestBody List<String> roleIds);

    /**
     * 批量查询岗位 ID → 岗位名映射。
     *
     * @param postIds 岗位 ID 列表
     * @return postId → postName 映射
     */
    @PostMapping("/api/internal/post/batch-names")
    BaseResponse<Map<String, String>> batchPostNames(@RequestBody List<String> postIds);

    /**
     * 批量查询公司 ID → 公司名映射。
     *
     * @param companyIds 公司 ID 列表
     * @return companyId → companyName 映射
     */
    @PostMapping("/api/internal/company/batch-names")
    BaseResponse<Map<String, String>> batchCompanyNames(@RequestBody List<String> companyIds);
}
