package com.njydsz.pmis.userinfo.web.controller.user;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.userinfo.domain.dto.auth.PasswordChangeDTO;
import com.njydsz.pmis.userinfo.domain.dto.auth.PasswordResetDTO;
import com.njydsz.pmis.userinfo.domain.dto.user.UserCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.user.UserQueryDTO;
import com.njydsz.pmis.userinfo.domain.dto.user.UserUpdateDTO;
import com.njydsz.pmis.userinfo.domain.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.server.service.user.UserAccountService;
import com.njydsz.pmis.userinfo.domain.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @AuthApiPermission(apiCodes = "auth:user:list")
    @RateLimit(key = "user:list", qps = 20, windowSeconds = 60)
    @GetMapping
    public BaseResponse<Page<UserVO>> page(@Valid UserQueryDTO query) {
        return BaseResponse.ok(userAccountService.pageVo(query));
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
    public BaseResponse<UserVO> get(@Parameter(description = "用户ID") @PathVariable String id) {
        return BaseResponse.ok(userAccountService.findVoById(id));
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 统一响应结果，包含当前用户信息（H13.1 修复：返回 UserVO 已脱敏）
     */
    @Operation(summary = "当前用户信息")
    @RateLimit(key = "user:list", qps = 20, windowSeconds = 60)
    @GetMapping("/me")
    public BaseResponse<UserVO> me() {
        return BaseResponse.ok(userAccountService.findVoById(AuthContext.getUserId()));
    }

    /**
     * 当前用户修改自己的密码
     *
     * @param dto 请求体，包含 oldPassword 与 newPassword
     * @return 统一响应结果
     * @throws SysException 当原密码或新密码为空时抛出
     */
    @Operation(summary = "修改自己的密码")
    @Idempotent(key = "user:changeMyPassword", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/me/password")
    public BaseResponse<Void> changeMyPassword(@Valid @RequestBody PasswordChangeDTO dto) {
        userAccountService.changePassword(AuthContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return BaseResponse.ok();
    }

    /**
     * 创建用户
     *
     * @param body 请求体，包含 username、password、employeeId
     * @return 统一响应结果，包含新建用户 ID
     * @throws SysException 当用户名或密码为空时抛出
     */
    @Operation(summary = "创建用户")
    @AuthApiPermission(apiCodes = "auth:user:create")
    @OperationLog(module = "权限管理", action = "创建用户", bizType = "USER")
    @RateLimit(key = "register", qps = 3, windowSeconds = 60,
            message = "{validation.user.msg_7aa2293e}")
    @Idempotent(key = "user:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody UserCreateDTO dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();
        String employeeId = dto.getEmployeeId();
        // @NotBlank + @Size 已校验 username/password，移除手动校验
        UserAccountDO u = new UserAccountDO();
        u.setUsername(username);
        u.setEmployeeId(employeeId);
        u.setStatus("ENABLED");
        return BaseResponse.ok(userAccountService.create(u, password));
    }

    /**
     * 更新用户信息
     *
     * @param user 用户实体
     * @return 统一响应结果
     */
    @Operation(summary = "更新用户")
    @AuthApiPermission(apiCodes = "auth:user:update")
    @OperationLog(module = "权限管理", action = "更新用户", bizType = "USER")
    @Idempotent(key = "user:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@Parameter(description = "用户ID") @PathVariable String id,
                               @Valid @RequestBody UserUpdateDTO dto) {
        dto.setId(id);
        UserAccountDO user = new UserAccountDO();
        BeanUtils.copyProperties(dto, user);
        userAccountService.update(user);
        return BaseResponse.ok();
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除用户")
    @AuthApiPermission(apiCodes = "auth:user:delete")
    @RequireReAuth(code = "USER_DELETE", name = "删除用户")
    @OperationLog(module = "权限管理", action = "删除用户", bizType = "USER")
    @Idempotent(key = "user:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "用户ID") @PathVariable String id) {
        userAccountService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 重置用户密码
     *
     * @param id  用户 ID
     * @param dto 请求体，包含新密码
     * @return 统一响应结果
     */
    @Operation(summary = "重置密码")
    @AuthApiPermission(apiCodes = "auth:user:resetPassword")
    @RequireReAuth(code = "USER_RESET_PASSWORD", name = "重置用户密码")
    @OperationLog(module = "权限管理", action = "重置密码", bizType = "USER")
    @RateLimit(key = "register", qps = 3, windowSeconds = 60,
            message = "{validation.user.msg_538560c7}")
    @Idempotent(key = "user:resetPassword", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/resetPassword")
    public BaseResponse<Void> resetPassword(@Parameter(description = "用户ID") @PathVariable String id,
                                      @Valid @RequestBody PasswordResetDTO dto) {
        userAccountService.resetPassword(id, dto.getNewPassword());
        return BaseResponse.ok();
    }

    /**
     * 启用/禁用用户
     *
     * @param id     用户 ID
     * @param status 目标状态（ENABLED/DISABLED）
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用用户")
    @AuthApiPermission(apiCodes = "auth:user:toggle")
    @OperationLog(module = "权限管理", action = "切换状态", bizType = "USER")
    @Idempotent(key = "user:toggleStatus", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/status")
    public BaseResponse<Void> toggleStatus(@Parameter(description = "用户ID") @PathVariable String id, @Parameter(description = "目标状态") @RequestParam @NotBlank String status) {
        userAccountService.toggleStatus(id, status);
        return BaseResponse.ok();
    }

    /**
     * 为用户分配角色
     *
     * @param id      用户 ID
     * @param roleIds 角色 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "为用户分配角色")
    @AuthApiPermission(apiCodes = "auth:user:assign")
    @OperationLog(module = "权限管理", action = "分配角色", bizType = "USER")
    @Idempotent(key = "user:assignRoles", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/roles")
    public BaseResponse<Void> assignRoles(@Parameter(description = "用户ID") @PathVariable String id, @Valid @RequestBody List<String> roleIds) {
        userAccountService.assignRoles(id, roleIds);
        return BaseResponse.ok();
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
    public BaseResponse<List<String>> listRoles(@Parameter(description = "用户ID") @PathVariable String id) {
        return BaseResponse.ok(userAccountService.listRoleIds(id));
    }
}
