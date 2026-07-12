package com.njydsz.pmis.project.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * WBS 任务状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class WbsTaskStatusDTO {
    /** 任务ID */
    private String id;
    /** 目标状态：WbsTaskStatus.code */
    private String targetStatus;
    /** 进度百分比（0-100） */
    private BigDecimal progressPct;
    /** 实际工时（人天） */
    private BigDecimal actualEffort;
}
