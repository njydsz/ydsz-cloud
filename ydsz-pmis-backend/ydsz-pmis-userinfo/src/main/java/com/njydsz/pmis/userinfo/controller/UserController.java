package com.njydsz.pmis.userinfo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.userinfo.dto.PasswordChangeDTO;
import com.njydsz.pmis.userinfo.dto.PasswordResetDTO;
import com.njydsz.pmis.userinfo.dto.UserCreateDTO;
import com.njydsz.pmis.userinfo.dto.UserQueryDTO;
import com.njydsz.pmis.userinfo.dto.UserUpdateDTO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.service.UserAccountService;
import com.njydsz.pmis.userinfo.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "用户管理", description = "用户管理相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    /** 用户账号服务 */
    private final UserAccountService userAccountService;

    /**
     * 用户分页查询
     *
     * @param query 查询条件
     * @return 统一响应结果，包含分页数据（H13.1 修复：返回 UserVO 已脱敏）
     */
    @Operation(summary = "用户分页")
    @PrePermission("auth:user:list")
    @RateLimit(key = "user:list", qps = 20, windowSeconds = 60)
    @GetMapping
    public Result<Page<UserVO>> page(UserQueryDTO query) {
        return Result.ok(userAccountService.pageVo(query));
    }

    /**
     * 查询用户详情
     *
     * @param id 用户 ID
     * @return 统一响应结果，包含用户信息（H13.1 修复：返回 UserVO 已脱敏）
     */
    @Operation(summary = "用户详情")
    @RateLimit(key = "user:list", qps = 20, windowSeconds = 60)
    @GetMapping("/{id}")
    public Result<UserVO> get(@Parameter(description = "用户ID") @PathVariable @Min(1) Long id) {
        return Result.ok(userAccountService.findVoById(id));
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 统一响应结果，包含当前用户信息（H13.1 修复：返回 UserVO 已脱敏）
     */
    @Operation(summary = "当前用户信息")
    @RateLimit(key = "user:list", qps = 20, windowSeconds = 60)
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userAccountService.findVoById(SecurityContext.getUserId()));
    }

    /**
     * 当前用户修改自己的密码
     *
     * @param dto 请求体，包含 oldPassword 与 newPassword
     * @return 统一响应结果
     * @throws BizException 当原密码或新密码为空时抛出
     */
    @Operation(summary = "修改自己的密码")
    @PostMapping("/me/password")
    public Result<Void> changeMyPassword(@Valid @RequestBody PasswordChangeDTO dto) {
        userAccountService.changePassword(SecurityContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return Result.ok();
    }

    /**
     * 创建用户
     *
     * @param body 请求体，包含 username、password、employeeId
     * @return 统一响应结果，包含新建用户 ID
     * @throws BizException 当用户名或密码为空时抛出
     */
    @Operation(summary = "创建用户")
    @PrePermission("auth:user:create")
    @OperationLog(module = "权限管理", action = "创建用户", bizType = "USER")
    @RateLimit(key = "register", qps = 3, windowSeconds = 60,
            message = "{validation.user.msg_7aa2293e}")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();
        Long employeeId = dto.getEmployeeId();
        // @NotBlank + @Size 已校验 username/password，移除手动校验
        UserAccountDO u = new UserAccountDO();
        u.setUsername(username);
        u.setEmployeeId(employeeId);
        u.setStatus("ENABLED");
        return Result.ok(userAccountService.create(u, password));
    }

    /**
     * 更新用户信息
     *
     * @param user 用户实体
     * @return 统一响应结果
     */
    @Operation(summary = "更新用户")
    @PrePermission("auth:user:update")
    @OperationLog(module = "权限管理", action = "更新用户", bizType = "USER")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UserUpdateDTO dto) {
        UserAccountDO user = new UserAccountDO();
        BeanUtils.copyProperties(dto, user);
        userAccountService.update(user);
        return Result.ok();
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除用户")
    @PrePermission("auth:user:delete")
    @RequireReAuth(code = "USER_DELETE", name = "删除用户")
    @OperationLog(module = "权限管理", action = "删除用户", bizType = "USER")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "用户ID") @PathVariable @Min(1) Long id) {
        userAccountService.delete(id);
        return Result.ok();
    }

    /**
     * 重置用户密码
     *
     * @param id  用户 ID
     * @param dto 请求体，包含新密码
     * @return 统一响应结果
     */
    @Operation(summary = "重置密码")
    @PrePermission("auth:user:reset-password")
    @RequireReAuth(code = "USER_RESET_PASSWORD", name = "重置用户密码")
    @OperationLog(module = "权限管理", action = "重置密码", bizType = "USER")
    @RateLimit(key = "register", qps = 3, windowSeconds = 60,
            message = "{validation.user.msg_538560c7}")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@Parameter(description = "用户ID") @PathVariable @Min(1) Long id,
                                      @Valid @RequestBody PasswordResetDTO dto) {
        userAccountService.resetPassword(id, dto.getNewPassword());
        return Result.ok();
    }

    /**
     * 启用/禁用用户
     *
     * @param id     用户 ID
     * @param status 目标状态（ENABLED/DISABLED）
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用用户")
    @PrePermission("auth:user:toggle")
    @OperationLog(module = "权限管理", action = "切换状态", bizType = "USER")
    @PostMapping("/{id}/status")
    public Result<Void> toggleStatus(@Parameter(description = "用户ID") @PathVariable @Min(1) Long id, @Parameter(description = "目标状态") @RequestParam @NotBlank String status) {
        userAccountService.toggleStatus(id, status);
        return Result.ok();
    }

    /**
     * 为用户分配角色
     *
     * @param id      用户 ID
     * @param roleIds 角色 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "为用户分配角色")
    @PrePermission("auth:user:assign")
    @OperationLog(module = "权限管理", action = "分配角色", bizType = "USER")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@Parameter(description = "用户ID") @PathVariable @Min(1) Long id, @Valid @RequestBody List<Long> roleIds) {
        userAccountService.assignRoles(id, roleIds);
        return Result.ok();
    }

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param id 用户 ID
     * @return 统一响应结果，包含角色 ID 列表
     */
    @Operation(summary = "查询用户角色 ID 列表")
    @RateLimit(key = "user:list", qps = 20, windowSeconds = 60)
    @GetMapping("/{id}/roles")
    public Result<List<Long>> listRoles(@Parameter(description = "用户ID") @PathVariable @Min(1) Long id) {
        return Result.ok(userAccountService.listRoleIds(id));
    }
}
