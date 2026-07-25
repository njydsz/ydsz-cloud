package com.njydsz.common.batch.model;

import java.time.Duration;
import java.time.Instant;

import com.njydsz.common.batch.enums.BatchStatus;
import com.njydsz.common.batch.enums.ExitStatus;

import lombok.Builder;
import lombok.Data;

/**
 * 批处理执行上下文
 *
 * <p>维护单次 Job / Step 执行的实时状态、计数器、起止时间等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class BatchExecutionContext {

    /** Job / Step 名 */
    private String name;

    /** Job 实例 ID */
    private String jobInstanceId;

    /** 状态 */
    private BatchStatus status;

    /** 退出状态 */
    private ExitStatus exitStatus;

    /** 启动时间 */
    private Instant startTime;

    /** 结束时间 */
    private Instant endTime;

    /** 已读取数 */
    private long readCount;

    /** 已处理数 */
    private long processCount;

    /** 已写入数 */
    private long writeCount;

    /** 跳过数 */
    private long skipCount;

    /** 重试次数 */
    private int retryCount;

    /** 提交次数 */
    private int commitCount;

    /** 回滚次数 */
    private int rollbackCount;

    /** 错误信息 */
    private String errorMessage;

    /** 失败原因异常 */
    private Throwable exception;

    /**
     * 累计耗时
     */
    public Duration getDuration() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        Instant end = endTime == null ? Instant.now() : endTime;
        return Duration.between(startTime, end);
    }

    /**
     * 成功率
     */
    public double getSuccessRate() {
        if (readCount == 0) {
            return 0.0;
        }
        return (double) writeCount / readCount;
    }
}
