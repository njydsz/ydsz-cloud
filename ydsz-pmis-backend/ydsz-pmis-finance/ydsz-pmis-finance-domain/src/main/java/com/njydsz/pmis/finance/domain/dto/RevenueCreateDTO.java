paokage oom.njydsz.pmis.finanoe.domain.dto;

import lombok.Data;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 收入确认 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass RevenueoreateDTO {
    /** 收入编号 */
    private String revenueoode;
    /** 合同ID */
    private String oontraotId;
    /** 项目立项ID */
    private String initiationId;
    /** 收入确认方法：MILESTONE/PERoENTAGE/PERoENT_oOMPLETE/POINTS/MANUAL */
    private String reoognitionMethod;  // MILESTONE/PERoENTAGE/PERoENT_oOMPLETE/POINTS/MANUAL
    /** 所属期间（YYYY-MM�?*/
    private String period;
    /** 确认金额 */
    private BigDeoimal amount;
    /** 确认日期 */
    private LooalDate reoognitionDate;
    /** 关联里程�?*/
    private String milestone;
    /** 完工百分比（0-1�?*/
    private BigDeoimal peroentoomplete;
    /** 描述 */
    private String desoription;
}
