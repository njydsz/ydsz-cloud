package com.njydsz.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则 AB 测试策略保存 DTO
 *
 * <p>隔离 {@link com.njydsz.literule.domain.entity.RuleABPolicyDO} 的
 * id/ruleCode/lastEvaluatedAt/lastRollbackAt/createdBy/createdAt/updatedBy/updatedAt
 * 审计字段，避免越权写入。ruleCode 由 URL 路径变量注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "AB 测试策略表单")
public class RuleABPolicySaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否启用自动回滚")
    private Boolean autoRollbackEnabled;

    @Schema(description = "回滚动作")
    private String rollbackAction;

    @Schema(description = "错误率阈值")
    private BigDecimal errorRateThreshold;

    @Schema(description = "最小样本量")
    private Integer minSampleSize;

    @Schema(description = "检查窗口（分钟）")
    private Integer checkWindowMinutes;

    @Schema(description = "通知通道（逗号分隔）")
    private String notifyChannels;

    @Schema(description = "描述")
    private String description;
}
