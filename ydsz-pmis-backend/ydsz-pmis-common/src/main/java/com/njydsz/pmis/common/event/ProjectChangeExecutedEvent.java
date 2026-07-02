package com.njydsz.pmis.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目变更执行完成事件
 *
 * <p>由 ProjectChangeService 在状态变更为 EXECUTING / EXECUTED 时发布,
 * 供 EVM 基线重算 / 资源重调度 / 通知中心等监听器订阅.
 *
 * <p>事件字段使用 String 描述变更最终状态, 避免跨模块引用 project 枚举.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectChangeExecutedEvent implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 变更 ID */
    private Long changeId;
    /** 变更编号 */
    private String changeCode;
    /** 变更标题 */
    private String changeTitle;
    /** 关联项目立项 ID */
    private Long initiationId;
    /** 变更类型: SCOPE / COST / CONTRACT / STAFF / SCHEDULE */
    private String changeType;
    /** 是否重大变更 */
    private Boolean majorFlag;
    /** 触发时的状态 (ChangeStatus.code 字符串) */
    private String finalStatusCode;
    /** 毛利影响百分比 */
    private BigDecimal profitImpactPct;
    /** 进度影响天数 (正=延期, 负=提前) */
    private Integer scheduleImpactDays;
    /** 触发时间戳 */
    private Long timestamp;
}
