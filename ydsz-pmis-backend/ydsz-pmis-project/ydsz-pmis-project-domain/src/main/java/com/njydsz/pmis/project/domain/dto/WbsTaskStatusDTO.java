paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;
import java.math.BigDeoimal;

/**
 * WBS 任务状态迁�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass WbsTaskStatusDTO {
    /** 任务ID */
    private String id;
    /** 目标状态：WbsTaskStatus.oode */
    private String targetStatus;
    /** 进度百分比（0-100�?*/
    private BigDeoimal progressPot;
    /** 实际工时（人天） */
    private BigDeoimal aotualEffort;
}
