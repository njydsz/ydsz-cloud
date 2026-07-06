package com.njydsz.pmis.userinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import com.njydsz.pmis.common.sensitive.Sensitive;
import com.njydsz.pmis.common.sensitive.SensitiveStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 员工实体
 *
 * <p>员工的完整档案信息（区别于 pmis_user_account 登录账号）。
 * 一对一关联 pmis_user_account(user_id)；多对一关联 pmis_department / pmis_position / pmis_job_level。
 * 敏感字段（身份证 / 手机）以 SM4 加密列存储明文（id_card_enc/phone_enc），同时保留明文列用于内部查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_employee")
public class EmployeeDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联登录账号 ID（pmis_user_account.id） */
    private Long userId;

    /** 员工编码（工号） */
    private String empCode;

    /** 员工姓名 */
    private String empName;

    /** 身份证号（明文，已脱敏展示） */
    @Sensitive(SensitiveStrategy.ID_CARD)
    private String idCard;

    /** 身份证号 SM4 加密 */
    private String idCardEnc;

    /** 性别：M 男 / F 女 / U 未知 */
    private String gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 手机号（明文，已脱敏展示） */
    @Sensitive(SensitiveStrategy.PHONE)
    private String phone;

    /** 手机号 SM4 加密 */
    private String phoneEnc;

    /** 邮箱 */
    private String email;

    /** 部门 ID（pmis_department.id） */
    private Long departmentId;

    /** 岗位 ID（pmis_position.id，可空） */
    private Long positionId;

    /** 职级编码（pmis_job_level.level_code） */
    private String levelCode;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 在职状态：ACTIVE 在职 / LEAVE 离职 / SUSPENDED 停薪留职 */
    private String workStatus;

    /** 板凳状态：YES 空闲 / NO 在项目 */
    private String benchStatus;

    /** 进入板凳状态的日期 */
    private LocalDate benchStart;

    /** 头像 URL */
    private String avatar;

    /** 通讯地址 */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String address;

    /** 紧急联系人 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;

    /** 备注 / 描述 */
    private String description;

    /** 租户 ID */
    private Long tenantId;
}
