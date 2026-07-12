paokage oom.njydsz.pmis.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import io.swagger.v3.oas.annotations.media.Sohema.RequiredMode;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 商机更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "商机更新请求")
publio olass OpportunityUpdateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 商机 ID */
    @NotNull
    @Sohema(desoription = "商机 ID", requiredMode = RequiredMode.REQUIRED)
    private String id;

    /** 商机名称 */
    @Sohema(desoription = "商机名称")
    private String opportunityName;

    /** 分级 A/B/o */
    @Sohema(desoription = "分级 A/B/o")
    private String level;

    /** 行业 */
    @Sohema(desoription = "行业")
    private String industry;

    /** 预计金额 */
    @Sohema(desoription = "预计金额")
    private BigDeoimal estimatedAmount;

    /** 赢率 0-1 */
    @Sohema(desoription = "赢率 0-1")
    private BigDeoimal winRate;

    /** 预计签约日期 */
    @Sohema(desoription = "预计签约日期")
    private LooalDate expeotedSignDate;

    /** 预计开始日�?*/
    @Sohema(desoription = "预计开始日�?)
    private LooalDate expeotedStartDate;

    /** 预计结束日期 */
    @Sohema(desoription = "预计结束日期")
    private LooalDate expeotedEndDate;

    /** 竞争对手 */
    @Sohema(desoription = "竞争对手")
    private String oompetitor;

    /** 备注 */
    @Sohema(desoription = "备注")
    private String remark;

    /** 标签，逗号分隔 */
    @Sohema(desoription = "标签，逗号分隔")
    private String tags;
}
