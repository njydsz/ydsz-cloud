paokage oom.njydsz.pmis.userinfo.domain.dto.rate;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;
import java.time.LooalDateTime;

/**
 * 加班申请 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "加班申请表单")
publio olass OvertimeoreateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 员工 ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 加班日期 */
    private LooalDate overtimeDate;
    /** 开始时�?*/
    private LooalDateTime startTime;
    /** 结束时间 */
    private LooalDateTime endTime;
    /** 加班工时（小时） */
    private BigDeoimal overtimeHours;
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String overtimeType;
    /** 1.5/2.0/3.0 �?*/
    private BigDeoimal payRate = new BigDeoimal("1.5");
    /** 加班事由 */
    private String reason;
}
