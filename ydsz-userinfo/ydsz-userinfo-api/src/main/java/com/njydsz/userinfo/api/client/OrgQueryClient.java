package com.njydsz.userinfo.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 组织架构查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供组织架构关系的 7 类查询能力：
 *
 * <ul>
 *   <li>角色 → 用户 ID 列表（{@link #listUserIdsByRoleCode}）
 *   <li>用户 → 角色编码列表（{@link #listRoleCodesByUserId}）
 *   <li>用户 → 部门 ID 列表（{@link #listDeptIdsByUserId}）
 *   <li>用户 → 直属上级（{@link #getLeaderByUserId}）
 *   <li>岗位 → 用户 ID 列表（{@link #listUserIdsByPositionCode}）
 *   <li>部门 ID → 部门负责人（{@link #getDeptLeaderByDeptId}）
 *   <li>部门编码 → 部门负责人（{@link #getDeptLeaderByDeptCode}）
 * </ul>
 *
 * <p>另外提供 5 个 batch-names 批量名称富化接口，供 {@code UserInfoNameAssembler} 在跨模块 VO 富化场景下一次 Feign 往返解析 ID →
 * 名称映射，避免 N+1 调用。
 *
 * <p>所有 ID 均为 String 类型（雪花算法字符串），与项目 ID 约定一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = FeignClientConstants.USERINFO,
    contextId = "orgQueryClient",
    fallback = OrgQueryClientFallback.class)
public interface OrgQueryClient {

  /**
   * 根据 ID 查询用户信息（跨服务远程调用）。
   *
   * @param userId 用户 ID
   * @return 用户账号 VO（已脱敏，不含密码等敏感字段）
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_INFO)
  BaseResponse<UserAccountVO> queryUserById(@RequestParam String userId);

  /**
   * 获取部门树形结构（全量递归树）。
   *
   * @return 部门树形 VO 列表（根节点列表，各节点含 children 子树）
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_DEPT_TREE)
  BaseResponse<List<DepartmentTreeVO>> getDeptTree();

  /**
   * 获取部门扁平列表（不分层）。
   *
   * @return 部门 VO 列表
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_DEPT_LIST)
  BaseResponse<List<DepartmentVO>> getDeptList();

  /**
   * 按角色编码查询用户 ID 列表（支持工作流 {@code role:xxx} 审批人展开）。
   *
   * @param roleCode 角色编码（如 HR/PM/SUPER_ADMIN）
   * @return 用户 ID 列表（String 形式，雪花算法字符串）
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_LIST_BY_ROLE)
  BaseResponse<List<String>> listUserIdsByRoleCode(@RequestParam String roleCode);

  /**
   * 查询用户拥有的角色编码列表（工作流待办反查）。
   *
   * @param userId 用户 ID
   * @return 角色编码列表
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_ROLE_CODES)
  BaseResponse<List<String>> listRoleCodesByUserId(@RequestParam String userId);

  /**
   * 查询用户所属部门 ID 列表（工作流待办反查，支持多部门）。
   *
   * @param userId 用户 ID
   * @return 部门 ID 列表（String 形式）
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_DEPT_IDS)
  BaseResponse<List<String>> listDeptIdsByUserId(@RequestParam String userId);

  /**
   * 查询用户的直属上级 ID（支持工作流 {@code leader:xxx} 审批人展开）。
   *
   * @param userId 用户 ID
   * @return 直属上级用户 ID，未设置时返回 null
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_LEADER)
  BaseResponse<String> getLeaderByUserId(@RequestParam String userId);

  /**
   * 按岗位编码查询用户 ID 列表（支持工作流 {@code position:xxx} 审批人展开）。
   *
   * @param positionCode 岗位编码（如 PM/DEV/QA）
   * @return 用户 ID 列表
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_LIST_BY_POSITION)
  BaseResponse<List<String>> listUserIdsByPositionCode(@RequestParam String positionCode);

  /**
   * 按部门 ID 查询部门负责人（支持工作流 {@code dept:数字} 审批人展开）。
   *
   * @param deptId 部门 ID
   * @return 部门负责人用户 ID，未设置时返回 null
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_DEPT_LEADER_BY_ID)
  BaseResponse<String> getDeptLeaderByDeptId(@RequestParam String deptId);

  /**
   * 按部门编码查询部门负责人（支持工作流 {@code dept:非数字} 审批人展开）。
   *
   * @param deptCode 部门编码（如 TECH/HR）
   * @return 部门负责人用户 ID，未设置时返回 null
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_DEPT_LEADER_BY_CODE)
  BaseResponse<String> getDeptLeaderByDeptCode(@RequestParam String deptCode);

  // ==================== NameAssembler 批量名称富化接口 ====================

  /**
   * 批量查询用户 ID → 用户真实姓名映射（供 NameAssembler 富化 userName/createdByName 等字段）。
   *
   * <p>实现走单条 SQL {@code SELECT id, real_name FROM ydsz_user_account WHERE id IN (...)}， 一次 Feign
   * 往返拿到全部结果，避免 N+1 调用。
   *
   * @param userIds 用户 ID 列表
   * @return userId → realName 映射；未命中的 userId 不出现在 Map 中
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_USER_BATCH_NAMES)
  BaseResponse<Map<String, String>> batchUserNames(@RequestBody List<String> userIds);

  /**
   * 批量查询部门 ID → 部门名映射。
   *
   * @param deptIds 部门 ID 列表
   * @return deptId → deptName 映射
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_DEPT_BATCH_NAMES)
  BaseResponse<Map<String, String>> batchDeptNames(@RequestBody List<String> deptIds);

  /**
   * 批量查询角色 ID → 角色名映射。
   *
   * @param roleIds 角色 ID 列表
   * @return roleId → roleName 映射
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_ROLE_BATCH_NAMES)
  BaseResponse<Map<String, String>> batchRoleNames(@RequestBody List<String> roleIds);

  /**
   * 批量查询岗位 ID → 岗位名映射。
   *
   * @param postIds 岗位 ID 列表
   * @return postId → postName 映射
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_POST_BATCH_NAMES)
  BaseResponse<Map<String, String>> batchPostNames(@RequestBody List<String> postIds);

  /**
   * 批量查询公司 ID → 公司名映射。
   *
   * @param companyIds 公司 ID 列表
   * @return companyId → companyName 映射
   */
  @PostMapping(FeignClientConstants.USERINFO_PATH_COMPANY_BATCH_NAMES)
  BaseResponse<Map<String, String>> batchCompanyNames(@RequestBody List<String> companyIds);
}
