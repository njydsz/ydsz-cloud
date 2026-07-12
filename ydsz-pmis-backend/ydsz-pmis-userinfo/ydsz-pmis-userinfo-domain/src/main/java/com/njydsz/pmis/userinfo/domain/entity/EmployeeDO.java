package com.njydsz.pmis.userinfo.domain.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
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
 * 一对一关联 pmis_user_account(user_id)；多对一关联 pmis_department / pmis_position / pmis_rank。
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
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联登录账号 ID（pmis_user_account.id） */
    private String userId;

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

    /** 邮箱（脱敏：a***@example.com） */
    @Sensitive(SensitiveStrategy.EMAIL)
    private String email;

    /** 部门 ID（pmis_department.id） */
    private String departmentId;

    /** 岗位 ID（pmis_position.id，可空） */
    private String positionId;

    /** 职级编码（全职 L1-L18 / 兼职 P1-P18） */
    private String levelCode;

    /** 雇佣类型：FULL_TIME 全职 / PART_TIME 兼职 / OUTSOURCE 外包 */
    private String employeeType;

    /** 兼职费率 ID（仅 PART_TIME 类型填写，关联 pmis_part_time_rate.id） */
    private String partTimeRateId;

    /** 外包费率 ID（仅 OUTSOURCE 类型填写，关联 pmis_outsource_rate.id） */
    private String outsourceRateId;

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

    /** 紧急联系人电话（脱敏：138****8000） */
    @Sensitive(SensitiveStrategy.PHONE)
    private String emergencyPhone;

    /** 备注 / 描述 */
    private String description;

    /** 租户 ID */
    private String tenantId;
}
