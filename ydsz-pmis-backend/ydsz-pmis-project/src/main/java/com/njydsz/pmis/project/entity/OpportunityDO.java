package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机主表 DO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_opportunity")
public class OpportunityDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final String serialVersionUID = "1";

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 商机编号 */
    private String opportunityCode;
    /** 商机名称 */
    private String opportunityName;
    /** 客户 ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 业务部门 ID */
    private String businessDeptId;
    /** 责任人 ID */
    private String ownerId;
    /** 责任人名称 */
    private String ownerName;
    /** 商机分级（A/B/C） */
    private String level;
    /** 商机来源 */
    private String source;
    /** 行业 */
    private String industry;
    /** 预估金额 */
    private BigDecimal estimatedAmount;
    /** 赢单率（百分比） */
    private BigDecimal winRate;
    /** 预计签单日期 */
    private LocalDate expectedSignDate;
    /** 预计开始日期 */
    private LocalDate expectedStartDate;
    /** 预计结束日期 */
    private LocalDate expectedEndDate;
    /** 商机状态（OpportunityStatus.code） */
    private String status;
    /** 输单原因 */
    private String lostReason;
    /** 竞争对手 */
    private String competitor;
    /** 备注 */
    private String remark;
    /** 标签 */
    private String tags;
    /** 租户 ID */
    private String tenantId;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
