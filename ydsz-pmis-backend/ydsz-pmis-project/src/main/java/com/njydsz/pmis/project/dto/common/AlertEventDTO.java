package com.njydsz.pmis.project.dto.common;

import com.njydsz.pmis.project.enums.common.AlertSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 驾驶舱预警事件 DTO
 *
 * <p>由预警规则引擎触发，输出到前端预警面板。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件 ID（UUID） */
    private String eventId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名 */
    private String ruleName;

    /** 类别：EVM / COST / BENCH / CREDIT / RISK / UTILIZATION */
    private String category;

    /** 严重度 */
    private AlertSeverity severity;

    /** 标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 当前值 */
    private String currentValue;

    /** 阈值（参考） */
    private String threshold;

    /** 影响范围：项目 ID / 部门 / 客户 等 */
    private String scope;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 是否可点击查看（true 表示有 drill-down 链接） */
    private Boolean drilldownAvailable;
}
