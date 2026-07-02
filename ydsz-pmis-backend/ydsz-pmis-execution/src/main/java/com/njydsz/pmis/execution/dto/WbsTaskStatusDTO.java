package com.njydsz.pmis.execution.dto;

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
    private Long id;
    private String targetStatus;
    private BigDecimal progressPct;
    private BigDecimal actualEffort;
}
