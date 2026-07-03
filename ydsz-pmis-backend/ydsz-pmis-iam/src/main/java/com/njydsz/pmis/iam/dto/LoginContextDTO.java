package com.njydsz.pmis.iam.dto;

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
    private Long userId;

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
    private Long employeeId;

    @Schema(description = "部门 ID")
    private Long departmentId;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "职级编码")
    private String levelCode;

    @Schema(description = "职级名称")
    private String levelName;

    @Schema(description = "数据权限范围")
    private String dataScope;

    @Schema(description = "角色编码列表")
    private List<String> roles;

    @Schema(description = "权限编码列表")
    private List<String> permissions;

    @Schema(description = "登录失败次数")
    private Integer loginFailCount;

    @Schema(description = "锁定截止时间戳")
    private Long lockedUntil;
}
