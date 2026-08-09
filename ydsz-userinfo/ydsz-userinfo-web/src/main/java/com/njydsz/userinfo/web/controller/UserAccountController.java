package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.dto.AssignRolesDTO;
import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.dto.UserImportResultDTO;
import com.njydsz.userinfo.domain.entity.UserLoginHistory;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.service.LoginHistoryService;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.UserExcelService;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 用户账号 Controller
 *
 * <p>提供用户账号的完整管理能力（CRUD）、密码自助管理（修改/重置）、角色分配/撤销、用户启用/禁用等。
 * 是用户中心服务（ydsz-userinfo）最核心的 Controller，被各业务模块通过 Feign
 * （{@code UserAccountClient}）远程调用获取用户基础信息。
 *
 * <p><b>接口路径：</b>{@code /api/v1/user}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>用户分页查询（多条件过滤：用户名/手机号/邮箱/部门/状态）</li>
 *   <li>用户 CRUD（含密码 BCrypt 加密存储）</li>
 *   <li>密码管理（用户自助修改 / 管理员重置）</li>
 *   <li>角色分配（{@code /assign-roles}）</li>
 *   <li>用户启用/禁用（{@code /enable} / {@code /disable}）</li>
 *   <li>用户导入/导出（批量）</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交（Redis SET NX EX）</li>
 *   <li>写接口启用 {@link RateLimit} 接口级限流（30-50 QPS）</li>
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>读接口无防护，业务方可高频调用</li>
 *   <li>密码字段禁止出现在响应中（{@code @Sensitive(PASSWORD)} 脱敏）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.service.UserAccountService 用户业务逻辑
 * @see com.njydsz.userinfo.domain.entity.UserAccount 用户实体
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户账号 CRUD、密码管理、角色分配")
@ApiVersion("1")
public class UserAccountController {

    private final UserAccountService service;
    private final UserExcelService userExcelService;
    private final LoginHistoryService loginHistoryService;

    /**
     * 分页查询用户列表
     *
     * <p>支持按 username / realName / phone / email 模糊匹配 + status / userType / companyId 精确匹配，
     * 默认按 {@code created_at} 降序排列。
     * <p>结果集启用 {@code @DataScope} 自动追加部门过滤（创建人所在部门 + 子部门）。
     *
     * @param query 分页查询条件（pageNum / pageSize / username / realName / phone / email / status）
     * @return 分页结果（含总记录数、当前页、每页大小、数据列表）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询用户列表")
    public PageResult<List<UserAccountVO>> page(@Valid UserAccountPageQueryDTO query) {
        Page<UserAccountVO> page = service.page(query);
        return PageResult.success(
                page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询全部用户列表（不翻页）
     *
     * <p>适用于前端下拉框、单选按钮组等场景。
     * <p><b>注意：</b>数据量较大（&gt; 500）时建议业务方缓存，避免高频调用。
     *
     * @return 全部未删除用户列表（按 created_at 降序）
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部用户列表")
    public BaseResponse<List<UserAccountVO>> list() {
        return BaseResponse.success(service.list());
    }

    /**
     * 根据 ID 查询用户
     *
     * @param id 用户 ID（雪花算法字符串）
     * @return 用户详情；不存在或已删除时返回 null
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询用户")
    public BaseResponse<UserAccountVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    /**
     * 创建用户
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志（{@code password} 字段已排除）。
     * <p>业务流程：username 唯一性校验 → 密码策略校验 → BCrypt 加密 → 写入 DB → 触发 ES 索引同步。
     *
     * @param dto 用户创建 DTO（含 username / password / realName / phone / email / deptIds 等）
     * @return 新创建的用户 ID
     */
    @Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建用户: ' + #dto.username", excludeParams = {"password"})
    @Idempotent(key = "ydsz:userinfo:UserAccountController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.useraccount.create", threshold = 50)
    @PostMapping
    @Operation(summary = "创建用户")
    public BaseResponse<String> create(@Valid @RequestBody UserAccountCreateDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    /**
     * 更新用户信息
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段，<b>避免覆盖已有值</b>。
     * <p>更新不会改变 {@code password}，如需重置密码请调用 {@link #resetPassword}。
     *
     * @param dto 用户更新 DTO（必须包含 ID）
     * @return 是否成功
     */
    @Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新用户: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:UserAccountController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.useraccount.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新用户信息")
    public BaseResponse<Boolean> update(@Valid @RequestBody UserAccountUpdateDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    /**
     * 按 ID 删除用户
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>删除为<b>软删除</b>（{@code deleted=1}），保留历史数据便于审计追溯，
     * 同步触发 ES 索引删除。
     *
     * @param id 用户 ID
     * @return 是否成功
     */
    @Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除用户: ' + #id")
    @RateLimit(resource = "userinfo.useraccount.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:UserAccountController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }

    /**
     * 修改密码（用户自助）
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志（{@code oldPassword / newPassword} 已排除）。
     * <p>业务流程：旧密码校验 → 新旧密码不能相同 → 密码策略校验 → BCrypt 加密 → 写入 DB。
     *
     * @param dto 修改密码 DTO（userId / oldPassword / newPassword）
     * @return 是否成功
     */
    @Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'修改密码'", excludeParams = {"oldPassword", "newPassword"})
    @Idempotent(key = "ydsz:userinfo:UserAccountController:changePassword:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.useraccount.changePassword", threshold = 50)
    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public BaseResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        return BaseResponse.success(service.changePassword(dto));
    }

    /**
     * 重置密码（管理员）
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>业务流程：密码策略校验 → BCrypt 加密 → 写入 DB → 重置失败计数和锁定状态。
     * <p>本接口<b>无需旧密码</b>，仅供管理员使用；用户自助修改请用 {@link #changePassword}。
     *
     * @param dto 重置密码 DTO（userId / newPassword）
     * @return 是否成功
     */
    @RateLimit(resource = "userinfo.useraccount.resetPassword", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:UserAccountController:resetPassword:lock", ttlSeconds = 5)
    @PostMapping("/reset-password")
    @Operation(summary = "重置密码（管理员）")
    public BaseResponse<Boolean> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        return BaseResponse.success(service.resetPassword(dto));
    }

    /**
     * 分配用户角色
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p><b>覆盖式</b>分配：先清空旧的角色关联，再批量插入新关联（避免 N+1 循环）。
     * 业务方传入<b>完整</b>的角色 ID 列表，而非增量。
     *
     * @param userId 用户 ID
     * @param dto    分配角色 DTO（roleIds 列表）
     * @return 是否成功
     */
    @RateLimit(resource = "userinfo.useraccount.assignRoles", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:UserAccountController:assignRoles:lock", ttlSeconds = 5)
    @PostMapping("/{userId}/roles")
    @Operation(summary = "分配用户角色")
    public BaseResponse<Boolean> assignRoles(
            @PathVariable String userId,
            @Valid @RequestBody AssignRolesDTO dto) {
        return BaseResponse.success(service.assignRoles(userId, dto.getRoleIds()));
    }

    /**
     * 查询用户的角色 ID 列表
     *
     * <p>返回该用户拥有的全部角色 ID；常用于工作流审批人解析（{@code OrgQueryClient.listUserIdsByRoleCode} 的逆向查询）。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @GetMapping("/{userId}/roles")
    @Operation(summary = "查询用户角色 ID 列表")
    public BaseResponse<List<String>> getUserRoles(@PathVariable String userId) {
        return BaseResponse.success(service.getUserRoleIds(userId));
    }

    /**
     * 批量导入用户（Excel）
     *
     * <p>上传 .xlsx 文件批量导入用户，单次上限 1000 行。
     * <p>文件格式：第一行为表头（用户名/真实姓名/初始密码/手机号/邮箱/部门编码/岗位编码/上级用户名）。
     * <p>先调用 {@code /import-template} 下载模板，按模板填写后上传。
     *
     * @param file Excel 文件（.xlsx 格式）
     * @return 导入结果（总数/成功数/失败数/失败明细）
     */
    @Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'批量导入用户'")
    @RateLimit(resource = "userinfo.useraccount.import", threshold = 10)
    @PostMapping("/import")
    @Operation(summary = "批量导入用户（Excel）")
    public BaseResponse<UserImportResultDTO> importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return BaseResponse.error("请选择要导入的文件");
        }
        try {
            UserImportResultDTO result = userExcelService.importUsers(
                    file.getInputStream(), file.getOriginalFilename());
            return BaseResponse.success(result);
        } catch (Exception e) {
            return BaseResponse.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 下载用户导入模板
     *
     * <p>返回带表头和一行示例数据的 Excel 模板文件，供业务方批量导入时使用。
     *
     * @param response HTTP 响应
     */
    @GetMapping("/import-template")
    @Operation(summary = "下载用户导入模板")
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            byte[] templateBytes = userExcelService.getImportTemplate();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=用户导入模板.xlsx");
            response.getOutputStream().write(templateBytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 导出用户列表（Excel）
     *
     * <p>导出全部用户数据为 Excel 文件。
     * <p><b>注意：</b>数据量较大时建议异步导出，当前实现为同步导出。
     *
     * @param response HTTP 响应
     */
    @Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'导出用户列表'")
    @GetMapping("/export")
    @Operation(summary = "导出用户列表（Excel）")
    public void exportUsers(HttpServletResponse response) {
        try {
            byte[] excelBytes = userExcelService.exportUsers();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=用户列表.xlsx");
            response.getOutputStream().write(excelBytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * P1-3: 查询用户最近登录历史
     *
     * <p>返回用户最近 N 条登录记录（默认 20 条），包含 IP、时间、结果等信息。
     * <p>用于安全审计、异常登录排查。
     *
     * @param userId 用户 ID
     * @param limit 返回记录数（默认 20，最大 100）
     * @return 登录历史列表
     */
    @GetMapping("/{userId}/login-history")
    @Operation(summary = "查询用户最近登录历史（安全审计）")
    public BaseResponse<List<UserLoginHistory>> getLoginHistory(
            @PathVariable String userId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        return BaseResponse.success(loginHistoryService.getRecentLogins(userId, limit));
    }
}
