package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.AssignRolesDTO;
import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.UserAccountService;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 用户账号 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户账号 CRUD、密码管理、角色分配")
public class UserAccountController {

    private final UserAccountService service;

    @GetMapping("/page")
    @Operation(summary = "分页查询用户列表")
    public BaseResponse<PageResponse<List<UserAccountVO>>> page(@Valid UserAccountPageQueryDTO query) {
        Page<UserAccountVO> page = service.page(query);
        PageResponse<List<UserAccountVO>> response = PageResponse.success(
                page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
        return BaseResponse.success(response);
    }

    @GetMapping("/list")
    @Operation(summary = "查询全部用户列表")
    public BaseResponse<List<UserAccountVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询用户")
    public BaseResponse<UserAccountVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public BaseResponse<String> create(@Valid @RequestBody UserAccountCreateDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新用户信息")
    public BaseResponse<Boolean> update(@Valid @RequestBody UserAccountUpdateDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public BaseResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        return BaseResponse.success(service.changePassword(dto));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码（管理员）")
    public BaseResponse<Boolean> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        return BaseResponse.success(service.resetPassword(dto));
    }

    @PostMapping("/{userId}/roles")
    @Operation(summary = "分配用户角色")
    public BaseResponse<Boolean> assignRoles(
            @PathVariable String userId,
            @Valid @RequestBody AssignRolesDTO dto) {
        return BaseResponse.success(service.assignRoles(userId, dto.getRoleIds()));
    }

    @GetMapping("/{userId}/roles")
    @Operation(summary = "查询用户角色 ID 列表")
    public BaseResponse<List<String>> getUserRoles(@PathVariable String userId) {
        return BaseResponse.success(service.getUserRoleIds(userId));
    }
}
