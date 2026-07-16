package com.njydsz.userinfo.domain.dto.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工更新 DTO（部分更新，仅非空字段生效）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "员工更新")
public class EmployeeUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联登录账号 ID */
    @Size(max = 20)
    private String userId;

    /** 员工编码（工号） */
    @Size(max = 64)
    private String empCode;

    /** 员工姓名 */
    @Size(max = 64)
    private String empName;

    /** 身份证号 */
    @Size(max = 32)
    private String idCard;

    /** 性别 */
    private String gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 手机号 */
    @Size(max = 32)
    private String phone;

    /** 邮箱 */
    @Size(max = 128)
    private String email;

    /** 部门 ID */
    @Size(max = 20)
    private String departmentId;

    /** 岗位 ID */
    @Size(max = 20)
    private String positionId;

    /** 职级编码 */
    @Size(max = 8)
    private String levelCode;

    /** 雇佣类型 */
    private String employeeType;

    /** 兼职费率 ID */
    @Size(max = 20)
    private String partTimeRateId;

    /** 外包费率 ID */
    @Size(max = 20)
    private String outsourceRateId;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 在职状态 */
    private String workStatus;

    /** Bench 状态 */
    private String benchStatus;

    /** Bench 起始日期 */
    private LocalDate benchStart;

    /** 头像 URL */
    @Size(max = 255)
    private String avatar;

    /** 通讯地址 */
    @Size(max = 255)
    private String address;

    /** 紧急联系人 */
    @Size(max = 64)
    private String emergencyContact;

    /** 紧急联系人电话 */
    @Size(max = 32)
    private String emergencyPhone;

    /** 备注 */
    private String description;
}
