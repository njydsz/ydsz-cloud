package com.njydsz.cronjob.domain.entity.job;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

    /** 任务数上限（NULL=unlimited；超过此值拒绝创建新任务） */
    private Integer maxJobs;

    /** 并发执行上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现） */
    private Integer maxConcurrent;

    /** 日执行量上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现） */
    private Integer maxDailyExecutions;

    /** 是否启用配额检查: 0 禁用 / 1 启用 */
    private Integer enabled;
}
