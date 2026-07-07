package com.njydsz.pmis.userinfo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.njydsz.pmis.common.sensitive.Sensitive;
import com.njydsz.pmis.common.sensitive.SensitiveStrategy;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象
 *
 * <p>H13.1/H13.2 修复：作为对外接口统一返回对象，剥离 password/salt 等敏感字段，
 * 并对手机号、邮箱做脱敏处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 用户 ID */
    private String id;
    /** 用户名 */
    private String username;
    /** 关联员工 ID */
    private String employeeId;
    /** 真实姓名 */
    private String realName;
    /** 邮箱（脱敏：a***@example.com） */
    @Sensitive(SensitiveStrategy.EMAIL)
    private String email;
    /** 手机号（脱敏：138****8000） */
    @Sensitive(SensitiveStrategy.PHONE)
    private String phone;
    /** 头像地址 */
    private String avatar;
    /** 性别 */
    private String gender;
    /** 部门 ID */
    private String departmentId;
    /** 部门名称 */
    private String departmentName;
    /** 岗位 ID */
    private String positionId;
    /** 岗位名称 */
    private String positionName;
    /** 职级编码 */
    private String levelCode;
    /** 职级名称 */
    private String levelName;
    /** 状态：ENABLED/DISABLED/LOCKED */
    private String status;
    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;
    /** 最近登录 IP（脱敏：保留前 3 段） */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String lastLoginIp;
    /** 数据权限范围: ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM/PROJECT */
    private String dataScope;
    /** 所属部门 ID */
    private String deptId;
    /** 直属上级用户 ID */
    private String leaderId;
    /** 岗位编码 */
    private String positionCode;
    /** 是否启用双因素认证 */
    private Boolean mfaEnabled;
    /** 角色编码列表 */
    private List<String> roles;
    /** 权限编码列表 */
    private List<String> permissions;
}

