package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录上下文 (供 auth 服务使用)
 *
 * <p>包含密码校验、角色/权限加载所需的全部信息。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录上下文")
public class LoginContextDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "加密密码")
    private String password;

    @Schema(description = "盐")
    private String salt;

    @Schema(description = "状态: ENABLED/DISABLED")
    private String status;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "员工 ID")
    private String employeeId;

    @Schema(description = "部门 ID")
    private String departmentId;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "职级编码")
    private String levelCode;

    @Schema(description = "职级名称")
    private String levelName;

    @Schema(description = "数据权限范围")
    private String dataScope;

    /**
     * P1-6 修复: 所属部门 ID（与 UserAccountDO.deptId 对齐，写入 JWT）
     */
    @Schema(description = "所属部门 ID（写入 JWT, DEPT 模式使用）")
    private String deptId;

    /**
     * P1-6 修复: DEPT_AND_CHILD 模式部门 ID 链（含所有下级部门）
     *
     * <p>登录时基于 deptPath 递归计算，写入 JWT。下游服务解析后直接用于 IN (...) 查询，
     * 避免每次请求都查库计算子部门。
     */
    @Schema(description = "DEPT_AND_CHILD 模式部门 ID 链（含下级）")
    private List<String> deptIds;

    /**
     * P1-6 修复: CUSTOM 模式自定义部门 ID 集
     *
     * <p>由 UserAccountDO.customDeptIds（逗号分隔字符串）解析得到。
     */
    @Schema(description = "CUSTOM 模式自定义部门 ID 集")
    private List<String> customDeptIds;

    @Schema(description = "角色编码列表")
    private List<String> roles;

    @Schema(description = "权限编码列表")
    private List<String> permissions;

    @Schema(description = "登录失败次数")
    private Integer loginFailCount;

    @Schema(description = "锁定截止时间戳")
    private Long lockedUntil;
}
