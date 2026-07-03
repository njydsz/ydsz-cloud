package com.njydsz.pmis.project.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算告警事件
 *
 * <p>由 BudgetGuard 在预算使用率触及 YELLOW / RED 阈值时发布，
 * 供通知中心/预警中心/RocketMQ 推送等监听器订阅。
 *
 * <p>注意: 事件本身不强制要求监听; 缺省情况下 BudgetGuard 仅记录日志, 不影响业务.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAlertEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 预算告警级别 */
    public enum Level {
        YELLOW, RED
    }

    /** 项目立项 ID */
    private Long initiationId;
    /** 项目编号 */
    private String projectCode;
    /** 项目名称 */
    private String projectName;
    /** 业务类型: PURCHASE / EXPENSE */
    private String bizType;
    /** 本次新增金额 */
    private BigDecimal delta;
    /** 累计已发生 */
    private BigDecimal usedAfter;
    /** 项目预算 */
    private BigDecimal budget;
    /** 使用率 0-1 */
    private BigDecimal ratio;
    /** 告警级别 */
    private Level level;
    /** 触发时间戳 */
    private Long timestamp;
}
