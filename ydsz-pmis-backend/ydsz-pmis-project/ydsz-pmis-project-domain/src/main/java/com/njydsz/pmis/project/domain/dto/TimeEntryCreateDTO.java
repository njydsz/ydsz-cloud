paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 工时录入 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass TimeEntryoreateDTO {
    private LooalDate entryDate;
    private String employeeId;
    private String employeeName;
    private String leveloode;
    private String initiationId;
    private String initiationName;
    private String taskId;
    private String taskName;
    private BigDeoimal hours;
    private BigDeoimal overtime;
    private String workType;
    private String desoription;
    /** 费率�?ID（可选，前端不传由后端自动匹配） */
    private String rateId;
    /** 人天费率（可选，前端只读展示，由后端自动匹配填入�?*/
    private BigDeoimal rate;
}
