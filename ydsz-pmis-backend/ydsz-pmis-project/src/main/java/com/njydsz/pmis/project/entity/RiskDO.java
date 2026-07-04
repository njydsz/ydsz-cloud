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
import java.time.LocalDateTime;

/**
 * 项目风险登记
 *
 * <p>记录项目范围/进度/成本/质量/资源/外部等维度的风险，跟踪应对与闭环。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_execution_risk")
public class RiskDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 风险编号 */
    private String riskCode;
    /** 项目立项ID */
    private Long initiationId;
    /** 风险标题 */
    private String riskTitle;
    /** 风险类型：SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER */
    private String riskType;
    /** 风险描述 */
    private String description;
    /** 发生概率：LOW/MEDIUM/HIGH */
    private String probability;
    /** 影响程度：LOW/MEDIUM/HIGH */
    private String impact;
    /** 计算后的风险等级 */
    private String riskLevel;
    /** 应对策略 */
    private String mitigation;
    /** 应急预案 */
    private String contingency;
    /** 责任人ID */
    private Long ownerId;
    /** 责任人姓名 */
    private String ownerName;
    /** 状态：RiskStatus.code */
    private String status;
    /** 风险发生时间 */
    private LocalDateTime occurredAt;
    /** 风险关闭时间 */
    private LocalDateTime closedAt;
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
