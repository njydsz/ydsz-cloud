package com.njydsz.pmis.userinfo.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 员工创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "员工创建")
public class EmployeeCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联登录账号 ID */
    @NotBlank
    @Size(max = 20)
    private String userId;

    /** 员工编码（工号） */
    @NotBlank
    @Size(max = 64)
    private String empCode;

    /** 员工姓名 */
    @NotBlank
    @Size(max = 64)
    private String empName;

    /** 身份证号 */
    @Size(max = 32)
    private String idCard;

    /** 性别：M/F/U */
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
    @NotBlank
    @Size(max = 20)
    private String departmentId;

    /** 岗位 ID */
    @Size(max = 20)
    private String positionId;

    /** 职级编码（全职 L1-L18 / 兼职 P1-P18） */
    @NotBlank
    @Size(max = 8)
    private String levelCode;

    /** 雇佣类型：FULL_TIME/PART_TIME/OUTSOURCE（为空时默认 FULL_TIME） */
    private String employeeType;

    /** 兼职费率 ID（仅 PART_TIME 类型必填） */
    @Size(max = 20)
    private String partTimeRateId;

    /** 外包费率 ID（仅 OUTSOURCE 类型必填） */
    @Size(max = 20)
    private String outsourceRateId;

    /** 入职日期 */
    @NotNull
    private LocalDate hireDate;

    /** 在职状态（为空时默认 ACTIVE） */
    private String workStatus;

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
