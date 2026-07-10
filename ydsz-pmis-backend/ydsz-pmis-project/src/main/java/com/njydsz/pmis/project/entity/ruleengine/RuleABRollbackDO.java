package com.njydsz.pmis.project.entity.ruleengine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AB Test 回滚历史实体（P1-10）。
 *
 * <p>对应 {@code pmis_rule_ab_rollback} 表，记录 AB Test 自动/手动回滚的执行历史。
 * 每次回滚操作插入一条记录，用于审计追溯和回滚效果分析。
 *
 * <p>触发场景：
 * <ul>
 *   <li>ERROR_RATE — 灰度版本错误率超过阈值，自动触发回滚</li>
 *   <li>MANUAL — 运维人员手动触发回滚</li>
 *   <li>OWNER_REQUEST — 规则责任人申请回滚</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-10)
 */
@Data
@TableName("pmis_rule_ab_rollback")
public class RuleABRollbackDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法字符串） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 规则编码（关联 {@code pmis_rule_definition.code}） */
    private String ruleCode;

    /** 触发原因：ERROR_RATE / MANUAL / OWNER_REQUEST */
    private String triggerReason;

    /** 回滚时的错误率（triggerReason=ERROR_RATE 时记录） */
    private BigDecimal errorRate;

    /** 回滚时的样本量（参与 AB Test 的事件总数） */
    private Long sampleSize;

    /** true=已从 canary 切换回主版本 / false=仅通知未回滚 */
    private Boolean fromCanary;

    /** 操作人 ID（自动回滚时为 SYSTEM） */
    private String operator;

    /** 通知状态：PENDING / SENT / FAILED（回滚后通知规则责任人） */
    private String notifyStatus;

    /** 回滚时间 */
    private LocalDateTime createdAt;
}
