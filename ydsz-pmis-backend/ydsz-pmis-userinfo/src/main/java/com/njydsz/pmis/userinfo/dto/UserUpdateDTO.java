package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户更新 DTO
 *
 * <p>仅包含前端可控的字段，隔离 {@link com.njydsz.pmis.userinfo.entity.UserAccountDO} 的
 * 密码/盐值、登录统计（lastLoginTime/loginFailCount/lockedUntil）、安全字段
 * （salt/mfaType/lastPwdChangeAt/pwdChangeCount）及审计字段，避免越权写入。
 *
 * <p>用户名与密码不可通过本接口修改（分别走注册与重置密码接口）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "用户更新表单")
public class UserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "{validation.user.msg_668e9add}")
    @Schema(description = "用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "员工 ID")
    private String employeeId;

    @Schema(description = "状态: ENABLED/DISABLED")
    private String status;

    @Schema(description = "数据权限范围: ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM")
    private String dataScope;

    @Schema(description = "自定义部门 ID 集（CUSTOM 模式，逗号分隔）")
    private String customDeptIds;

    @Schema(description = "是否启用 MFA")
    private Boolean mfaEnabled;

    @Schema(description = "部门 ID")
    private String deptId;

    @Schema(description = "直属上级 ID")
    private String leaderId;

    @Schema(description = "岗位编码")
    private String positionCode;
}
