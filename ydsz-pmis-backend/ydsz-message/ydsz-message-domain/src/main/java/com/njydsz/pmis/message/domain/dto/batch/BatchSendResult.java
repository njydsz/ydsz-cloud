package com.njydsz.message.domain.dto.batch;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量发送结果。
 *
 * @author ydsz-team
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

    public void incSuccess() {
        this.success++;
    }

    public void incFailed() {
        this.failed++;
    }

    public void incSkipped() {
        this.skipped++;
    }
}
