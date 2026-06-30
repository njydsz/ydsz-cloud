package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.enums.ReconcileLevel;
import com.njydsz.pmis.execution.enums.ReconcileType;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单条对账结果
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class ReconcileResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 校验类型 */
    private ReconcileType type;

    /** 严重等级 */
    private ReconcileLevel level;

    /** 关联项目 ID */
    private Long initiationId;

    /** 关联员工 ID(可能为空) */
    private Long employeeId;

    /** 关联来源单据 ID */
    private Long sourceId;

    /** 关联来源类型(TIME_ENTRY / COST / ...) */
    private String sourceType;

    /** 描述 */
    private String description;

    /** 当前观测值(可选) */
    private BigDecimal actualValue;

    /** 期望值(可选) */
    private BigDecimal expectedValue;

    /** 偏差值(expected - actual) */
    private BigDecimal drift;

    /** 建议处理动作 */
    private String suggestion;

    public static ReconcileResult info(ReconcileType type, String desc) {
        return ReconcileResult.builder()
                .type(type).level(ReconcileLevel.INFO).description(desc).build();
    }

    public static ReconcileResult warn(ReconcileType type, String desc) {
        return ReconcileResult.builder()
                .type(type).level(ReconcileLevel.WARN).description(desc).build();
    }

    public static ReconcileResult warn(ReconcileType type, String desc, String suggestion) {
        return ReconcileResult.builder()
                .type(type).level(ReconcileLevel.WARN).description(desc).suggestion(suggestion).build();
    }

    public static ReconcileResult error(ReconcileType type, String desc) {
        return ReconcileResult.builder()
                .type(type).level(ReconcileLevel.ERROR).description(desc).build();
    }

    public static ReconcileResult error(ReconcileType type, String desc, String suggestion) {
        return ReconcileResult.builder()
                .type(type).level(ReconcileLevel.ERROR).description(desc).suggestion(suggestion).build();
    }
}
