package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.server.service.UserAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内部 API Controller（供跨服务 Feign 调用）。
 *
 * <p>所有端点挂载在 {@code /api/internal/**} 路径下，仅供其他后端服务通过 Feign 调用，
 * 不对前端暴露。覆盖以下能力：
 * <ul>
 *   <li>用户信息查询（{@link #getUserInfo}）</li>
 *   <li>部门树形/列表查询（{@link #getDeptTree}、{@link #getDeptList}）</li>
 *   <li>角色 → 用户 ID 列表（{@link #listUserIdsByRole}，工作流 role:xxx 展开）</li>
 *   <li>用户 → 角色编码列表（{@link #listRoleCodesByUserId}，工作流待办反查）</li>
 *   <li>用户 → 部门 ID 列表（{@link #listDeptIdsByUserId}，工作流待办反查）</li>
 *   <li>用户 → 直属上级（{@link #getLeaderByUserId}，工作流 leader:xxx 展开）</li>
 *   <li>岗位 → 用户 ID 列表（{@link #listUserIdsByPosition}，工作流 position:xxx 展开）</li>
 *   <li>部门 ID → 部门负责人（{@link #getDeptLeaderByDeptId}，工作流 dept:数字 展开）</li>
 *   <li>部门编码 → 部门负责人（{@link #getDeptLeaderByDeptCode}，工作流 dept:非数字 展开）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "内部 API", description = "跨服务 Feign 调用接口")
public class InternalApiController {

    private final UserAccountService userAccountService;
    private final DepartmentService departmentService;

    @GetMapping("/user/info")
    @Operation(summary = "根据 userId 查询用户信息（内部调用）")
    public BaseResponse<UserAccountVO> getUserInfo(@RequestParam String userId) {
        return BaseResponse.success(userAccountService.getById(userId));
    }

    @GetMapping("/dept/tree")
    @Operation(summary = "查询部门树形结构（内部调用）")
    public BaseResponse<List<DepartmentTreeVO>> getDeptTree() {
        return BaseResponse.success(departmentService.tree());
    }

    @GetMapping("/dept/list")
    @Operation(summary = "查询部门列表（内部调用）")
    public BaseResponse<List<DepartmentVO>> getDeptList() {
        return BaseResponse.success(departmentService.list());
    }

    @GetMapping("/user/list-by-role")
    @Operation(summary = "按角色编码查询用户 ID 列表（工作流 role:xxx 展开）")
    public BaseResponse<List<String>> listUserIdsByRole(@RequestParam String roleCode) {
        return BaseResponse.success(userAccountService.listUserIdsByRoleCode(roleCode));
    }

    @GetMapping("/user/role-codes")
    @Operation(summary = "查询用户拥有的角色编码列表（工作流待办反查）")
    public BaseResponse<List<String>> listRoleCodesByUserId(@RequestParam String userId) {
        return BaseResponse.success(userAccountService.listRoleCodesByUserId(userId));
    }

    @GetMapping("/user/dept-ids")
    @Operation(summary = "查询用户所属部门 ID 列表（工作流待办反查）")
    public BaseResponse<List<String>> listDeptIdsByUserId(@RequestParam String userId) {
        return BaseResponse.success(userAccountService.listDeptIdsByUserId(userId));
    }

    @GetMapping("/user/leader")
    @Operation(summary = "查询用户直属上级 ID（工作流 leader:xxx 展开）")
    public BaseResponse<String> getLeaderByUserId(@RequestParam String userId) {
        return BaseResponse.success(userAccountService.getLeaderByUserId(userId));
    }

    @GetMapping("/user/list-by-position")
    @Operation(summary = "按岗位编码查询用户 ID 列表（工作流 position:xxx 展开）")
    public BaseResponse<List<String>> listUserIdsByPosition(@RequestParam String positionCode) {
        return BaseResponse.success(userAccountService.listUserIdsByPositionCode(positionCode));
    }

    @GetMapping("/dept/leader-by-id")
    @Operation(summary = "按部门 ID 查询部门负责人（工作流 dept:数字 展开）")
    public BaseResponse<String> getDeptLeaderByDeptId(@RequestParam String deptId) {
        return BaseResponse.success(departmentService.getDeptLeaderByDeptId(deptId));
    }

    @GetMapping("/dept/leader-by-code")
    @Operation(summary = "按部门编码查询部门负责人（工作流 dept:非数字 展开）")
    public BaseResponse<String> getDeptLeaderByDeptCode(@RequestParam String deptCode) {
        return BaseResponse.success(departmentService.getDeptLeaderByDeptCode(deptCode));
    }
}
