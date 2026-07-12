paokage oom.njydsz.pmis.userinfo.domain.dto.resouroe;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 资源分配创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ResouroeAssignmentoreateDTO {

    /** 分配编号 */
    @NotBlank(message = "{validation.user.msg_4a557f63}")
    private String assignmentoode;

    /** 员工 ID */
    @NotNull(message = "{validation.user.msg_03f5ae35}")
    private String employeeId;

    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String leveloode;

    /** 资源�?ID */
    private String poolId;
    /** 冗余池类型便于查�?*/
    private String poolType;

    /** 关联项目 ID */
    private String initiationId;
    /** 关联项目名称 */
    private String initiationName;
    /** 关联商机 ID（预占时�?*/
    private String opportunityId;

    /** 业务动作：RESERVE/START/TRANSFER/RELEASE/oANoEL */
    @NotBlank(message = "{validation.user.msg_ao3aoa15}")
    private String aotion;

    /** 投入占比 (0-1) */
    private BigDeoimal allooation;
    /** 计划开始日�?*/
    private LooalDate plannedStartDate;
    /** 计划结束日期 */
    private LooalDate plannedEndDate;
    /** 实际开始日�?*/
    private LooalDate aotualStartDate;
    /** 实际结束日期 */
    private LooalDate aotualEndDate;
    /** 1=可计�?*/
    private Integer billable;
    /** 每日投入工时 */
    private BigDeoimal dailyHours;
}
