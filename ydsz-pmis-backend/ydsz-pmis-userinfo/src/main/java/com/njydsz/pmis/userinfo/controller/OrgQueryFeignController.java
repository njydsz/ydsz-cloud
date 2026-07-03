package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.entity.DepartmentDO;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import com.njydsz.pmis.userinfo.mapper.DepartmentMapper;
import com.njydsz.pmis.userinfo.mapper.RoleMapper;
import com.njydsz.pmis.userinfo.mapper.UserRoleMapper;
import com.njydsz.pmis.userinfo.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 组织架构查询 Feign 端点（P1-5）
 *
 * <p>仅供 workflow 服务远程调用，将 BPMN 中的角色/部门审批人标识展开为具体用户 ID。
 * 不对外暴露文档，不参与权限校验（由网关层和 Feign 拦截器保障内部调用安全）。
 *
 * <p>当前能力：
 * <ul>
 *   <li>role:xxx → 通过 role_code → role_id → user_ids 展开</li>
 *   <li>dept:xxx → 通过 dept_id/dept_code → leader_id 展开部门负责人</li>
 *   <li>user_role_codes → 反查用户角色编码（用于待办匹配）</li>
 * </ul>
 *
 * <p>未实现的能力（待 P2-2 落地）：
 * <ul>
 *   <li>dept:xxx 展开部门下所有成员（用户表无 dept_id 字段）</li>
 *   <li>leader:xxx 直属上级展开（用户表无 leader_id 字段）</li>
 *   <li>position:xxx 岗位展开（无岗位表）</li>
 *   <li>multi_leader:N 多级上级（依赖 leader_id 字段）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "Feign-组织架构查询")
@RestController
@RequestMapping("/api/v1/feign/org")
@RequiredArgsConstructor
public class OrgQueryFeignController {

    /** 角色 Mapper（按 roleCode 查 roleId） */
    private final RoleMapper roleMapper;
    /** 用户-角色关联 Mapper（按 roleId 查 userIds） */
    private final UserRoleMapper userRoleMapper;
    /** 部门 Mapper（按 deptId/deptCode 查 leaderId） */
    private final DepartmentMapper departmentMapper;
    /** 角色服务（反查用户角色编码） */
    private final RoleService roleService;

    /**
     * 根据角色编码查询用户 ID 列表
     *
     * <p>查询链：role_code → pmis_role.id → pmis_user_role.user_id
     *
     * @param roleCode 角色编码
     * @return 用户 ID 列表（无匹配返回空列表）
     */
    @Operation(summary = "按角色编码查询用户 ID 列表")
    @GetMapping("/users-by-role")
    public Result<List<Long>> listUserIdsByRoleCode(@RequestParam("roleCode") String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Result.ok(Collections.emptyList());
        }
        try {
            RoleDO role = roleMapper.selectByCode(roleCode.trim());
            if (role == null || role.getId() == null) {
                log.debug("[OrgQuery] 角色编码未命中: roleCode={}", roleCode);
                return Result.ok(Collections.emptyList());
            }
            List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(role.getId());
            if (userIds == null) {
                return Result.ok(Collections.emptyList());
            }
            // 去重 + 过滤 null
            List<Long> cleaned = userIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            return Result.ok(cleaned);
        } catch (Exception e) {
            log.warn("[OrgQuery] 按角色查询用户失败: roleCode={} err={}", roleCode, e.getMessage());
            return Result.ok(Collections.emptyList());
        }
    }

    /**
     * 根据部门 ID 查询部门负责人用户 ID
     *
     * @param deptId 部门 ID
     * @return 部门负责人用户 ID，未设置或部门不存在时返回 null
     */
    @Operation(summary = "按部门 ID 查询部门负责人")
    @GetMapping("/dept-leader")
    public Result<Long> getDeptLeaderByDeptId(@RequestParam("deptId") Long deptId) {
        if (deptId == null) {
            return Result.ok(null);
        }
        try {
            DepartmentDO dept = departmentMapper.selectById(deptId);
            if (dept == null) {
                log.debug("[OrgQuery] 部门 ID 未命中: deptId={}", deptId);
                return Result.ok(null);
            }
            return Result.ok(dept.getLeaderId());
        } catch (Exception e) {
            log.warn("[OrgQuery] 按部门 ID 查负责人失败: deptId={} err={}", deptId, e.getMessage());
            return Result.ok(null);
        }
    }

    /**
     * 根据部门编码查询部门负责人用户 ID
     *
     * @param deptCode 部门编码
     * @return 部门负责人用户 ID，未设置或部门不存在时返回 null
     */
    @Operation(summary = "按部门编码查询部门负责人")
    @GetMapping("/dept-leader-by-code")
    public Result<Long> getDeptLeaderByDeptCode(@RequestParam("deptCode") String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return Result.ok(null);
        }
        try {
            DepartmentDO dept = departmentMapper.selectByCode(deptCode.trim());
            if (dept == null) {
                log.debug("[OrgQuery] 部门编码未命中: deptCode={}", deptCode);
                return Result.ok(null);
            }
            return Result.ok(dept.getLeaderId());
        } catch (Exception e) {
            log.warn("[OrgQuery] 按部门编码查负责人失败: deptCode={} err={}", deptCode, e.getMessage());
            return Result.ok(null);
        }
    }

    /**
     * 查询用户拥有的角色编码列表
     *
     * <p>用于 workflow 待办查询时反查"该用户能看到的角色审批任务"。
     *
     * @param userId 用户 ID
     * @return 角色编码列表（无匹配返回空列表）
     */
    @Operation(summary = "查询用户角色编码列表")
    @GetMapping("/user-role-codes")
    public Result<List<String>> listRoleCodesByUserId(@RequestParam("userId") Long userId) {
        if (userId == null) {
            return Result.ok(Collections.emptyList());
        }
        try {
            List<RoleDO> roles = roleService.listByUserId(userId);
            if (roles == null || roles.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            List<String> codes = roles.stream()
                    .map(RoleDO::getRoleCode)
                    .filter(Objects::nonNull)
                    .filter(c -> !c.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            return Result.ok(codes);
        } catch (Exception e) {
            log.warn("[OrgQuery] 查询用户角色失败: userId={} err={}", userId, e.getMessage());
            return Result.ok(Collections.emptyList());
        }
    }

    /**
     * 查询用户所属部门 ID 列表
     *
     * <p>当前用户表（pmis_user_account）无 dept_id 字段，返回空列表。
     * 待 P2-2 候选人/变量独立表落地（用户表增加 dept_id/leader_id 字段）后补全。
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表（字符串形式），当前始终返回空列表
     */
    @Operation(summary = "查询用户部门 ID 列表（待 P2-2 落地）")
    @GetMapping("/user-dept-ids")
    public Result<List<String>> listDeptIdsByUserId(@RequestParam("userId") Long userId) {
        // TODO P2-2: 用户表增加 dept_id 字段后实现
        return Result.ok(Collections.emptyList());
    }
}
