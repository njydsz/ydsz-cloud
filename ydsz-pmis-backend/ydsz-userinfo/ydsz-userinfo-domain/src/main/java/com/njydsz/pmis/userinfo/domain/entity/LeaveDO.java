package com.njydsz.userinfo.domain.entity.rate;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请假申请实体
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_leave")
public class LeaveDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 请假单号 */
    private String leaveCode;
    /** 员工 ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 请假类型（LeaveType.code） */
    private String leaveType;
    /** 开始日期 */
    private LocalDate startDate;
    /** 结束日期 */
    private LocalDate endDate;
    /** 请假天数 */
    private BigDecimal leaveDays;
    /** 请假事由 */
    private String reason;
    /** 附件地址 */
    private String attachmentUrl;
    /** 审批单 ID */
    private String approvalId;
    /** 审批状态（LeaveStatus.code） */
    private String approvalStatus;
    /** 审批人 ID */
    private String approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approvalTime;
    /** 审批意见 */
    private String approvalRemark;
    /** 租户 ID */
    private String tenantId;
    /** 外部提供方链路追踪 ID */
    private String providerTraceId;
}
