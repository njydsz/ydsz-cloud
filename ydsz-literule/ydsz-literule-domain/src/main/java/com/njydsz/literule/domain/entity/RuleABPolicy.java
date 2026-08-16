package com.njydsz.literule.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * AB Test 自动回滚策略 DO（P1-10）
 *
 * <p>对应 ydsz_rule_ab_policy 表。每条启用了 canary 的规则可以配置自动回滚策略，
 * 定时任务会按监控窗口检查错误率，超过阈值则按 rollback_action 执行 AUTO 回滚或 NOTIFY 通知。
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_ab_policy")
public class RuleABPolicy extends MpBaseEntity<String> {

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

    /** 最近一次评估时间 */
    private LocalDateTime lastEvaluatedAt;
    /** 最近一次回滚时间 */
    private LocalDateTime lastRollbackAt;
}
