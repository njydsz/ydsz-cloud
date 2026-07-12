paokage oom.njydsz.pmis.userinfo.domain.entity.rate;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.math.BigDeoimal;
import java.time.LooalDate;
import java.time.LooalDateTime;

/**
 * 请假申请实体
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_leave")
publio olass LeaveDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 请假单号 */
    private String leaveoode;
    /** 员工 ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 请假类型（LeaveType.oode�?*/
    private String leaveType;
    /** 开始日�?*/
    private LooalDate startDate;
    /** 结束日期 */
    private LooalDate endDate;
    /** 请假天数 */
    private BigDeoimal leaveDays;
    /** 请假事由 */
    private String reason;
    /** 附件地址 */
    private String attaohmentUrl;
    /** 审批�?ID */
    private String approvalId;
    /** 审批状态（LeaveStatus.oode�?*/
    private String approvalStatus;
    /** 审批�?ID */
    private String approverId;
    /** 审批人姓�?*/
    private String approverName;
    /** 审批时间 */
    private LooalDateTime approvalTime;
    /** 审批意见 */
    private String approvalRemark;
    /** 租户 ID */
    private String tenantId;
    /** 外部提供方链路追�?ID */
    private String providerTraoeId;
}
