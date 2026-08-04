package com.remisoft.message.domain.dto.batch;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量发送结果。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchSendResult {

    /** 批次 ID（业务侧生成,用于关联进度查询） */
    private String batchId;

    /** 总数 */
    private int total;

    /** 成功数 */
    private int success;

    /** 失败数 */
    private int failed;

    /** 被限流/拦截数 */
    private int skipped;

    /**
     * 累加成功数（线程不安全，批量统计请在单线程或外部同步下调用）。
     */
    public void incSuccess() {
        this.success++;
    }

    /**
     * 累加失败数（线程不安全，批量统计请在单线程或外部同步下调用）。
     */
    public void incFailed() {
        this.failed++;
    }

    /**
     * 累加被限流/拦截数（线程不安全，批量统计请在单线程或外部同步下调用）。
     */
    public void incSkipped() {
        this.skipped++;
    }
}
