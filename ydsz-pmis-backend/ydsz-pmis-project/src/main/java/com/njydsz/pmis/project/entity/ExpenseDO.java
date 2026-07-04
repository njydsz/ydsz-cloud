package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用报销
 *
 * <p>员工项目费用报销记录，经审批后计入项目成本。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_cost_expense")
public class ExpenseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 费用编号 */
    private String expenseCode;
    /** 项目立项ID */
    private Long initiationId;
    /** 报销人ID */
    private Long employeeId;
    /** 报销人姓名 */
    private String employeeName;
    /** 费用类型：TRAVEL/CATERING/... */
    private String expenseType;
    /** 报销金额 */
    private BigDecimal amount;
    /** 费用发生日期 */
    private LocalDate expenseDate;
    /** 费用描述 */
    private String description;
    /** 发票/收据附件URL */
    private String receiptUrl;
    /** 状态：ApprovalStatus.code */
    private String status;
    /** 审批人ID */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /** 乐观锁版本号（P1-2） */
    @Version
    private Integer version;
}
