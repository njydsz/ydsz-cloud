package com.njydsz.pmis.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 规则灰度分桶统计 DO
 *
 * <p>对应 pmis_rule_canary_bucket 表，按日聚合每条规则在 PRIMARY/CANARY 桶中的执行次数。
 * 用于 AB Test 自动回滚判断（比较两桶错误率/触发率）。
 */
@Data
@TableName("pmis_rule_canary_bucket")
public class RuleCanaryBucketDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String ruleCode;

    /** 桶类型：PRIMARY / CANARY */
    private String bucketType;

    private Long bucketCount;

    private LocalDate statDate;
    private LocalDateTime updatedAt;
}
