package com.njydsz.pmis.message.domain.dto.batch;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批次发送进度 VO。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
public class BatchProgressVO {

    /** 批次 ID */
    private String batchId;

    /** 批次名称 */
    private String batchName;

    /** 发送通道 */
    private String channel;

    /** 模板编码 */
    private String templateCode;

    /** 总数 */
    private int total;

    /** 成功数 */
    private int success;

    /** 失败数 */
    private int failed;

    /** 跳过数 */
    private int skipped;

    /** 已处理数（success + failed + skipped） */
    private int processed;

    /** 进度百分比（0-100） */
    private double progressPercent;

    /** 批次状态: PENDING / PROCESSING / COMPLETED / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 开始处理时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
