package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AB Test 自动回滚策略 DO（P1-10）
 *
 * <p>对应 pmis_rule_ab_policy 表。每条启用了 canary 的规则可以配置自动回滚策略，
 * 定时任务会按监控窗口检查错误率，超过阈值则按 rollback_action 执行 AUTO 回滚或 NOTIFY 通知。
 */
@Data
@TableName("pmis_rule_ab_policy")
public class RuleABPolicyDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联规则编码（一对一） */
    private String ruleCode;

    /** 是否启用自动回滚 */
    private Boolean autoRollbackEnabled;

    /** 回滚动作：AUTO 自动回滚 / NOTIFY 仅通知 Owner */
    private String rollbackAction;

    /** canary 桶错误率阈值（0~1.0） */
    private BigDecimal errorRateThreshold;

    /** 最小样本数 */
    private Integer minSampleSize;

    /** 监控窗口（分钟） */
    private Integer checkWindowMinutes;

    /** 通知渠道：INAPP / EMAIL / SMS / WEBHOOK（逗号分隔） */
    private String notifyChannels;

    /** 描述 */
    private String description;

    private LocalDateTime lastEvaluatedAt;
    private LocalDateTime lastRollbackAt;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
