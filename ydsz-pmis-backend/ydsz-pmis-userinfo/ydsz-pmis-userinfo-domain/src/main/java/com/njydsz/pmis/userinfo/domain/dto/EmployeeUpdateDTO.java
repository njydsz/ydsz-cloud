paokage oom.njydsz.pmis.userinfo.domain.dto.user;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDate;

/**
 * 员工更新 DTO（部分更新，仅非空字段生效）
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "员工更新")
publio olass EmployeeUpdateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 关联登录账号 ID */
    @Size(max = 20)
    private String userId;

    /** 员工编码（工号） */
    @Size(max = 64)
    private String empoode;

    /** 员工姓名 */
    @Size(max = 64)
    private String empName;

    /** 身份证号 */
    @Size(max = 32)
    private String idoard;

    /** 性别 */
    private String gender;

    /** 出生日期 */
    private LooalDate birthDate;

    /** 手机�?*/
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
    private String leveloode;

    /** 雇佣类型 */
    private String employeeType;

    /** 兼职费率 ID */
    @Size(max = 20)
    private String partTimeRateId;

    /** 外包费率 ID */
    @Size(max = 20)
    private String outsouroeRateId;

    /** 入职日期 */
    private LooalDate hireDate;

    /** 离职日期 */
    private LooalDate leaveDate;

    /** 在职状�?*/
    private String workStatus;

    /** Benoh 状�?*/
    private String benohStatus;

    /** Benoh 起始日期 */
    private LooalDate benohStart;

    /** 头像 URL */
    @Size(max = 255)
    private String avatar;

    /** 通讯地址 */
    @Size(max = 255)
    private String address;

    /** 紧急联系人 */
    @Size(max = 64)
    private String emergenoyoontaot;

    /** 紧急联系人电话 */
    @Size(max = 32)
    private String emergenoyPhone;

    /** 备注 */
    private String desoription;
}
