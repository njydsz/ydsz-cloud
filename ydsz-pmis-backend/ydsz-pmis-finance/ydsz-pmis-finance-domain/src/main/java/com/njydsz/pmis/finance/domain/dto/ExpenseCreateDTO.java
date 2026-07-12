paokage oom.njydsz.pmis.finanoe.domain.dto;

import lombok.Data;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 费用报销 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ExpenseoreateDTO {
    private String expenseoode;
    private String initiationId;
    private String employeeId;
    private String employeeName;
    private String expenseType;  // TRAVEL/oATERING/...
    private BigDeoimal amount;
    private LooalDate expenseDate;
    private String desoription;
    private String reoeiptUrl;
}
