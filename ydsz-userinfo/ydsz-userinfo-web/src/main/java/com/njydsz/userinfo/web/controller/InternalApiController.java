package com.njydsz.userinfo.web.controller;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.CompanyService;
import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.server.service.PostService;
import com.njydsz.userinfo.server.service.RoleService;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.WorkflowApproverCacheService;
import com.njydsz.userinfo.web.annotation.RequireInternal;

/**
 * 内部 API Controller（供跨服务 Feign 调用）
 *
 * <p>所有端点挂载在 {@code /api/internal/**} 路径下，仅供其他后端服务通过 Feign 调用， 不对前端暴露。该 Controller 是
 * ydsz-workflow、ydsz-project 等服务 拉取用户/部门/角色/岗位基础数据的主入口，避免直接访问数据库。
 *
 * <p><b>接口路径：</b>{@code /api/internal}
 *
 * <p><b>核心能力分组：</b>
 *
 * <ul>
 *   <li><b>用户查询</b>：{@code /user/info}、{@code /user/leader}、{@code /user/Role-codes}、 {@code
 *       /user/dept-ids}、{@code /user/list-by-Role}、{@code /user/list-by-position}
 *   <li><b>部门查询</b>：{@code /dept/tree}、{@code /dept/list}、 {@code /dept/leader-by-id}、{@code
 *       /dept/leader-by-code}
 *   <li><b>NameAssembler 批量富化</b>：{@code /user/batch-names}、 {@code /dept/batch-names}、{@code
 *       /Role/batch-names}、{@code /Post/batch-names}、{@code /Company/batch-names}
 * </ul>
 *
 * <p><b>工作流联动说明：</b>
 *
 * <ul>
 *   <li>{@code Role:xxx} 节点 → 调用 {@code /user/list-by-Role}
 *   <li>{@code position:xxx} 节点 → 调用 {@code /user/list-by-position}
 *   <li>{@code leader:xxx} 节点 → 调用 {@code /user/leader}
 *   <li>{@code dept:数字} 节点 → 调用 {@code /dept/leader-by-id}
 *   <li>{@code dept:非数字} 节点 → 调用 {@code /dept/leader-by-code}
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>所有端点挂载在 {@code /api/internal/**}，由 Gateway 通过白名单控制访问
 *   <li>推荐通过 Feign + FallbackFactory 调用，避免级联失败
 *   <li>批量名称接口接受 ID 列表（&le; 500），超过限制会被 Service 层截断
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.api.client.OrgQueryClient Feign Client 接口
 */
@Slf4j
@Validated
@RestController
@RequireInternal
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "内部 API", description = "跨服务 Feign 调用接口")
public class InternalApiController {

  private final UserAccountService userAccountService;
  private final DepartmentService departmentService;
  private final RoleService roleService;
  private final PostService postService;
  private final CompanyService companyService;
  private final WorkflowApproverCacheService workflowCacheService;

  /**
   * 根据 userId 查询用户信息
   *
   * <p>典型场景：流程审批页反查审批人信息、消息通知接收人信息。
   *
   * <p>用户不存在或已删除时返回 null（Feign 调用方需做空值处理）。
   *
   * @param userId 用户 ID（雪花算法字符串）
   * @return 用户 VO；不存在时为 null
   */
  @RateLimit(resource = "userinfo.internalapi.getUserInfo", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:get-user-info:' + #userId", ttlSeconds = 5)
  @GetMapping("/user/info")
  @Operation(summary = "根据 userId 查询用户信息（内部调用）")
  public YdszResponse<UserAccountVO> getUserInfo(@RequestParam String userId) {
    return YdszResponse.success(userAccountService.getById(userId));
  }

  /**
   * 查询部门树形结构
   *
   * <p>由 Service 层在内存中构建树（递归遍历全量列表）。
   *
   * <p>典型场景：组织架构选择器嵌入、流程表单部门选择器。
   *
   * @return 部门树形结构列表（每个节点含 children）
   */
  @RateLimit(resource = "userinfo.internalapi.getDeptTree", threshold = 50)
  @Idempotent(key = "'ydsz:userinfo:internal-api:get-dept-tree'", ttlSeconds = 5)
  @GetMapping("/dept/tree")
  @Operation(summary = "查询部门树形结构（内部调用）")
  public YdszResponse<List<DepartmentTreeVO>> getDeptTree() {
    return YdszResponse.success(departmentService.tree());
  }

  /**
   * 查询部门列表（扁平结构）
   *
   * <p>返回全量部门 VO（不构建树形结构），按 {@code sort_order} 升序排列。
   *
   * @return 部门列表
   */
  @RateLimit(resource = "userinfo.internalapi.getDeptList", threshold = 50)
  @Idempotent(key = "'ydsz:userinfo:internal-api:get-dept-list'", ttlSeconds = 5)
  @GetMapping("/dept/list")
  @Operation(summary = "查询部门列表（内部调用）")
  public YdszResponse<List<DepartmentVO>> getDeptList() {
    return YdszResponse.success(departmentService.list());
  }

  /**
   * 按角色编码查询用户 ID 列表
   *
   * <p>对应工作流节点表达式 {@code Role:xxx} 的展开逻辑。
   *
   * <p>工作流引擎在计算审批人时调用该接口，将角色编码解析为具体用户 ID 列表。
   *
   * @param roleCode 角色编码（如 {@code PM} / {@code FINANCE}）
   * @return 该角色下的用户 ID 列表；角色不存在时返回空列表
   */
  @RateLimit(resource = "userinfo.internalapi.listUserIdsByRole", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:list-user-ids-by-role:' + #roleCode", ttlSeconds = 5)
  @GetMapping("/user/list-by-role")
  @Operation(summary = "按角色编码查询用户 ID 列表（工作流 role:xxx 展开，带缓存）")
  public YdszResponse<List<String>> listUserIdsByRole(@RequestParam String roleCode) {
    return YdszResponse.success(workflowCacheService.listUserIdsByRoleCode(roleCode));
  }

  /**
   * 查询用户拥有的角色编码列表
   *
   * <p>对应工作流待办反查：在「我审批的」列表中按角色过滤。
   *
   * @param userId 用户 ID
   * @return 用户的角色编码列表；用户无角色时返回空列表
   */
  @RateLimit(resource = "userinfo.internalapi.listRoleCodesByUser", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:list-role-codes-by-user:' + #userId", ttlSeconds = 5)
  @GetMapping("/user/role-codes")
  @Operation(summary = "查询用户拥有的角色编码列表（工作流待办反查）")
  public YdszResponse<List<String>> listRoleCodesByUserId(@RequestParam String userId) {
    return YdszResponse.success(userAccountService.listRoleCodesByUserId(userId));
  }

  /**
   * 查询用户所属部门 ID 列表
   *
   * <p>用户可能同时属于多个部门（主部门 + 兼任部门）。
   *
   * <p>对应工作流「部门级」权限过滤。
   *
   * @param userId 用户 ID
   * @return 部门 ID 列表
   */
  @RateLimit(resource = "userinfo.internalapi.listDeptIdsByUser", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:list-dept-ids-by-user:' + #userId", ttlSeconds = 5)
  @GetMapping("/user/dept-ids")
  @Operation(summary = "查询用户所属部门 ID 列表（工作流待办反查）")
  public YdszResponse<List<String>> listDeptIdsByUserId(@RequestParam String userId) {
    return YdszResponse.success(userAccountService.listDeptIdsByUserId(userId));
  }

  /**
   * 查询用户直属上级 ID
   *
   * <p>对应工作流节点表达式 {@code leader:xxx} 的展开逻辑。
   *
   * <p>典型场景：员工请假 → 部门负责人审批。
   *
   * @param userId 用户 ID
   * @return 直属上级用户 ID；无上级时返回 null
   */
  @RateLimit(resource = "userinfo.internalapi.getLeaderByUser", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:get-leader-by-user:' + #userId", ttlSeconds = 5)
  @GetMapping("/user/leader")
  @Operation(summary = "查询用户直属上级 ID（工作流 leader:xxx 展开，带缓存）")
  public YdszResponse<String> getLeaderByUserId(@RequestParam String userId) {
    return YdszResponse.success(workflowCacheService.getLeaderByUserId(userId));
  }

  /**
   * 按岗位编码查询用户 ID 列表
   *
   * <p>对应工作流节点表达式 {@code position:xxx} 的展开逻辑。
   *
   * <p>典型场景：所有 PM 评审需求单、所有 QA 验收测试报告。
   *
   * @param positionCode 岗位编码（如 {@code PM} / {@code QA}）
   * @return 该岗位下的用户 ID 列表；岗位不存在时返回空列表
   */
  @RateLimit(resource = "userinfo.internalapi.listUserIdsByPosition", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:list-user-ids-by-position:' + #positionCode", ttlSeconds = 5)
  @GetMapping("/user/list-by-position")
  @Operation(summary = "按岗位编码查询用户 ID 列表（工作流 position:xxx 展开，带缓存）")
  public YdszResponse<List<String>> listUserIdsByPosition(@RequestParam String positionCode) {
    return YdszResponse.success(workflowCacheService.listUserIdsByPositionCode(positionCode));
  }

  /**
   * 按部门 ID 查询部门负责人
   *
   * <p>对应工作流节点表达式 {@code dept:数字} 的展开逻辑。
   *
   * <p>典型场景：某部门员工提交报销 → 该部门负责人审批。
   *
   * @param deptId 部门 ID（雪花算法字符串）
   * @return 部门负责人用户 ID；部门不存在或无负责人时返回 null
   */
  @RateLimit(resource = "userinfo.internalapi.getDeptLeaderById", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:get-dept-leader-by-id:' + #deptId", ttlSeconds = 5)
  @GetMapping("/dept/leader-by-id")
  @Operation(summary = "按部门 ID 查询部门负责人（工作流 dept:数字 展开，带缓存）")
  public YdszResponse<String> getDeptLeaderByDeptId(@RequestParam String deptId) {
    return YdszResponse.success(workflowCacheService.getDeptLeaderByDeptId(deptId));
  }

  /**
   * 按部门编码查询部门负责人
   *
   * <p>对应工作流节点表达式 {@code dept:非数字} 的展开逻辑。
   *
   * <p>与 {@link #getDeptLeaderByDeptId} 互为补充，支持两种 ID 形式。
   *
   * @param deptCode 部门编码（如 {@code TECH} / {@code FINANCE}）
   * @return 部门负责人用户 ID；部门不存在或无负责人时返回 null
   */
  @RateLimit(resource = "userinfo.internalapi.getDeptLeaderByCode", threshold = 100)
  @Idempotent(key = "'ydsz:userinfo:internal-api:get-dept-leader-by-code:' + #deptCode", ttlSeconds = 5)
  @GetMapping("/dept/leader-by-code")
  @Operation(summary = "按部门编码查询部门负责人（工作流 dept:非数字 展开，带缓存）")
  public YdszResponse<String> getDeptLeaderByDeptCode(@RequestParam String deptCode) {
    return YdszResponse.success(workflowCacheService.getDeptLeaderByDeptCode(deptCode));
  }

  // ==================== NameAssembler 批量名称富化接口 ====================

  /**
   * 批量查询用户 ID → 真实姓名映射
   *
   * <p>由 {@code NameAssembler} 远程调用，幂等且带 Redis 缓存。
   *
   * <p>典型场景：审批列表中一次拉取 50 条审批单的「申请人姓名」「审批人姓名」。
   *
   * @param userIds 用户 ID 列表（建议 ≤ 500）
   * @return userId → 用户真实姓名 映射；不存在的 ID 会被过滤
   */
  @PostMapping("/user/batch-names")
  @Operation(summary = "批量查询用户 ID → 真实姓名映射（NameAssembler 富化用）")
  @RateLimit(resource = "userinfo.internal.batchUserNames", threshold = 200)
  public YdszResponse<Map<String, String>> batchUserNames(
      @RequestBody @Valid @Size(max = 500) List<String> userIds) {
    return YdszResponse.success(userAccountService.batchUserNames(userIds));
  }

  /**
   * 批量查询部门 ID → 部门名映射
   *
   * <p>由 {@code NameAssembler} 远程调用，幂等且带 Redis 缓存。
   *
   * @param deptIds 部门 ID 列表（建议 ≤ 500）
   * @return deptId → 部门名 映射
   */
  @PostMapping("/dept/batch-names")
  @Operation(summary = "批量查询部门 ID → 部门名映射（NameAssembler 富化用）")
  @RateLimit(resource = "userinfo.internal.batchDeptNames", threshold = 200)
  public YdszResponse<Map<String, String>> batchDeptNames(
      @RequestBody @Valid @Size(max = 500) List<String> deptIds) {
    return YdszResponse.success(departmentService.batchNamesByIds(deptIds));
  }

  /**
   * 批量查询角色 ID → 角色名映射
   *
   * @param roleIds 角色 ID 列表（建议 ≤ 500）
   * @return roleId → 角色名 映射
   */
  @PostMapping("/role/batch-names")
  @Operation(summary = "批量查询角色 ID → 角色名映射（NameAssembler 富化用）")
  @RateLimit(resource = "userinfo.internal.batchRoleNames", threshold = 200)
  public YdszResponse<Map<String, String>> batchRoleNames(
      @RequestBody @Valid @Size(max = 500) List<String> roleIds) {
    return YdszResponse.success(roleService.batchNamesByIds(roleIds));
  }

  /**
   * 批量查询岗位 ID → 岗位名映射
   *
   * @param postIds 岗位 ID 列表（建议 ≤ 500）
   * @return postId → 岗位名 映射
   */
  @PostMapping("/post/batch-names")
  @Operation(summary = "批量查询岗位 ID → 岗位名映射（NameAssembler 富化用）")
  @RateLimit(resource = "userinfo.internal.batchPostNames", threshold = 200)
  public YdszResponse<Map<String, String>> batchPostNames(
      @RequestBody @Valid @Size(max = 500) List<String> postIds) {
    return YdszResponse.success(postService.batchNamesByIds(postIds));
  }

  /**
   * 批量查询公司 ID → 公司名映射
   *
   * @param companyIds 公司 ID 列表（建议 ≤ 500）
   * @return companyId → 公司名 映射
   */
  @PostMapping("/company/batch-names")
  @Operation(summary = "批量查询公司 ID → 公司名映射（NameAssembler 富化用）")
  @RateLimit(resource = "userinfo.internal.batchCompanyNames", threshold = 200)
  public YdszResponse<Map<String, String>> batchCompanyNames(
      @RequestBody @Valid @Size(max = 500) List<String> companyIds) {
    return YdszResponse.success(companyService.batchNamesByIds(companyIds));
  }
}
