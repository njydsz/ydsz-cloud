package com.njydsz.pmis.project.domain.entity;

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
import java.time.LocalDateTime;

/**
 * 成本归集
 *
 * <p>按项目/期间/成本类型归集人力/采购/费用/外包/分摊成本，用于利润核算与对账。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_cost_allocation")
public class CostAllocationDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 项目立项ID */
    private String initiationId;
    /** 所属期间（YYYY-MM） */
    private String period;
    /** 成本类型：CostType.code */
    private String costType;
    /** 来源业务主键ID */
    private String sourceId;
    /** 来源业务类型 */
    private String sourceType;
    /** 描述 */
    private String description;
    /** 金额 */
    private BigDecimal amount;
    /** 是否可计费：1 是 / 0 否 */
    private Integer billable;
    /** 是否已核销：1 是 / 0 否 */
    private Integer allocated;
    /** 员工ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;
    /** 租户ID */
    private String tenantId;
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
