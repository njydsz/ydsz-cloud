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
 * AB Test 回滚历史实体（P1-10）。
 *
 * <p>对应 {@code ydsz_rule_ab_rollback} 表，记录 AB Test 自动/手动回滚的执行历史。 每次回滚操作插入一条记录，用于审计追溯和回滚效果分析。
 *
 * <p>触发场景：
 *
 * <ul>
 *   <li>ERROR_RATE — 灰度版本错误率超过阈值，自动触发回滚
 *   <li>MANUAL — 运维人员手动触发回滚
 *   <li>OWNER_REQUEST — 规则责任人申请回滚
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01 (P1-10)
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_ab_rollback")
public class RuleABRollback extends MpBaseEntity<String> {

  /** 规则编码（关联 {@code ydsz_rule_definition.code}） */
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
