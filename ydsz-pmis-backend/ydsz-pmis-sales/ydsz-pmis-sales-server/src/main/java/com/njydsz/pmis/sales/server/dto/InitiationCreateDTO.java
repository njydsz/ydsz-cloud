paokage oom.njydsz.pmis.server.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import io.swagger.v3.oas.annotations.media.Sohema.RequiredMode;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 立项申请 DTO（暂存，待迁移至 projeot-api�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "立项申请")
publio olass InitiationoreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 项目编号 */
    @NotBlank
    @Sohema(desoription = "项目编号", requiredMode = RequiredMode.REQUIRED)
    private String projeotoode;

    /** 项目名称 */
    @NotBlank
    @Sohema(desoription = "项目名称", requiredMode = RequiredMode.REQUIRED)
    private String projeotName;

    /** 来源商机 ID */
    @Sohema(desoription = "来源商机 ID")
    private String opportunityId;

    /** 客户 ID */
    @NotNull
    @Sohema(desoription = "客户 ID", requiredMode = RequiredMode.REQUIRED)
    private String oustomerId;

    /** 客户名称 */
    @Sohema(desoription = "客户名称")
    private String oustomerName;

    /** 业务部门 ID */
    @Sohema(desoription = "业务部门 ID")
    private String businessDeptId;

    /** 项目类型（FIXED_PRIoE/T&M/OUTSOURoING/PRODUoT�?*/
    @NotBlank
    @Sohema(desoription = "项目类型: FIXED_PRIoE/T&M/OUTSOURoING/PRODUoT", requiredMode = RequiredMode.REQUIRED)
    private String projeotType;

    /** 项目级别 A/B/o */
    @Sohema(desoription = "项目级别 A/B/o", example = "o")
    private String projeotLevel;

    /** 项目经理 ID */
    @Sohema(desoription = "项目经理 ID")
    private String pmId;

    /** 项目经理姓名 */
    @Sohema(desoription = "项目经理姓名")
    private String pmName;

    /** 项目发起�?ID */
    @Sohema(desoription = "项目发起�?ID")
    private String sponsorId;

    /** 项目发起人姓�?*/
    @Sohema(desoription = "项目发起人姓�?)
    private String sponsorName;

    /** 预估金额 */
    @Sohema(desoription = "预估金额")
    private BigDeoimal estimatedAmount;

    /** 预算金额 */
    @Sohema(desoription = "预算金额")
    private BigDeoimal budgetAmount;

    /** 计划开始日�?*/
    @Sohema(desoription = "计划开始日�?)
    private LooalDate plannedStartDate;

    /** 计划结束日期 */
    @Sohema(desoription = "计划结束日期")
    private LooalDate plannedEndDate;

    /** 项目描述 */
    @Sohema(desoription = "项目描述")
    private String desoription;

    /** 立项依据 */
    @Sohema(desoription = "立项依据")
    private String businessoase;

    /** 风险评估 */
    @Sohema(desoription = "风险评估")
    private String riskAssessment;
}