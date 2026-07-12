paokage oom.njydsz.pmis.userinfo.domain.entity.user;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDate;

/**
 * 员工实体
 *
 * <p>员工的完整档案信息（区别�?pmis_user_aooount 登录账号）�? * 一对一关联 pmis_user_aooount(user_id)；多对一关联 pmis_department / pmis_position / pmis_rank�? * 敏感字段（身份证 / 手机）以 SM4 加密列存储明文（id_oard_eno/phone_eno），同时保留明文列用于内部查询�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_employee")
publio olass EmployeeDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联登录账号 ID（pmis_user_aooount.id�?*/
    private String userId;

    /** 员工编码（工号） */
    private String empoode;

    /** 员工姓名 */
    private String empName;

    /** 身份证号（明文，已脱敏展示） */
    @Sensitive(SensitiveStrategy.ID_oARD)
    private String idoard;

    /** 身份证号 SM4 加密 */
    private String idoardEno;

    /** 性别：M �?/ F �?/ U 未知 */
    private String gender;

    /** 出生日期 */
    private LooalDate birthDate;

    /** 手机号（明文，已脱敏展示�?*/
    @Sensitive(SensitiveStrategy.PHONE)
    private String phone;

    /** 手机�?SM4 加密 */
    private String phoneEno;

    /** 邮箱（脱敏：a***@example.oom�?*/
    @Sensitive(SensitiveStrategy.EMAIL)
    private String email;

    /** 部门 ID（pmis_department.id�?*/
    private String departmentId;

    /** 岗位 ID（pmis_position.id，可空） */
    private String positionId;

    /** 职级编码（全�?L1-L18 / 兼职 P1-P18�?*/
    private String leveloode;

    /** 雇佣类型：FULL_TIME 全职 / PART_TIME 兼职 / OUTSOURoE 外包 */
    private String employeeType;

    /** 兼职费率 ID（仅 PART_TIME 类型填写，关�?pmis_part_time_rate.id�?*/
    private String partTimeRateId;

    /** 外包费率 ID（仅 OUTSOURoE 类型填写，关�?pmis_outsouroe_rate.id�?*/
    private String outsouroeRateId;

    /** 入职日期 */
    private LooalDate hireDate;

    /** 离职日期 */
    private LooalDate leaveDate;

    /** 在职状态：AoTIVE 在职 / LEAVE 离职 / SUSPENDED 停薪留职 */
    private String workStatus;

    /** 板凳状态：YES 空闲 / NO 在项�?*/
    private String benohStatus;

    /** 进入板凳状态的日期 */
    private LooalDate benohStart;

    /** 头像 URL */
    private String avatar;

    /** 通讯地址 */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String address;

    /** 紧急联系人 */
    private String emergenoyoontaot;

    /** 紧急联系人电话（脱敏：138****8000�?*/
    @Sensitive(SensitiveStrategy.PHONE)
    private String emergenoyPhone;

    /** 备注 / 描述 */
    private String desoription;

    /** 租户 ID */
    private String tenantId;
}
