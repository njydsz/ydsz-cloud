package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门径评审记录
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_gate_review")
public class GateReviewDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 立项 ID */
    private Long initiationId;
    /** 门径评审点（CD1/CD2/CD3/CD4/CD5） */
    private String gateCode;
    /** 评审点名称 */
    private String gateName;
    /** 评审结果（PENDING/PASSED/REJECTED/CONDITIONAL） */
    private String reviewResult;
    /** 评审人 ID */
    private Long reviewerId;
    /** 评审人名称 */
    private String reviewerName;
    /** 评审时间 */
    private LocalDateTime reviewAt;
    /** 决策依据 */
    private String decisionBasis;
    /** 附加条件 */
    private String conditions;
    /** 下一评审点 */
    private String nextGate;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
