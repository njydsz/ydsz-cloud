package com.njydsz.pmis.project.domain.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目变更执行事件。
 *
 * <p>当项目变更审批通过并执行完成后发布此事件，
 * 触发 EVM 基线重算、预警刷新等异步操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectChangeExecutedEvent implements Serializable {

    /** 变更 ID */
    private String changeId;
    /** 变更编号 */
    private String changeCode;
    /** 变更标题 */
    private String changeTitle;
    /** 立项 ID */
    private String initiationId;
    /** 变更类型 */
    private String changeType;
    /** 是否重大变更 */
    private Boolean majorFlag;
    /** 最终状态码 */
    private String finalStatusCode;
    /** 利润影响百分比 */
    private BigDecimal profitImpactPct;
    /** 进度影响天数 */
    private Integer scheduleImpactDays;
    /** 事件时间戳 */
    private Long timestamp;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;

    /**
     * 构造项目变更执行事件。
     *
     * @param initiationId   立项 ID
     * @param changeCode     变更编号
     * @param changeType     变更类型
     * @param finalStatusCode 最终状态码
     * @param majorFlag      是否重大变更
     */
    public ProjectChangeExecutedEvent(String initiationId, String changeCode, String changeType,
                                       String finalStatusCode, Boolean majorFlag) {
        this.initiationId = initiationId;
        this.changeCode = changeCode;
        this.changeType = changeType;
        this.finalStatusCode = finalStatusCode;
        this.majorFlag = majorFlag;
        this.occurredAt = LocalDateTime.now();
    }
}
