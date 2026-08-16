package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 规则灰度分桶统计 DO
 *
 * <p>对应 ydsz_rule_canary_bucket 表，按日聚合每条规则在 PRIMARY/CANARY 桶中的执行次数。 用于 AB Test 自动回滚判断（比较两桶错误率/触发率）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_canary_bucket")
public class RuleCanaryBucket extends MpBaseEntity<String> {

  /** 规则编码 */
  private String ruleCode;

  /** 桶类型：PRIMARY / CANARY */
  private String bucketType;

  /** 桶命中次数 */
  private Long bucketCount;

  /** 统计日期 */
  private LocalDate statDate;
}
