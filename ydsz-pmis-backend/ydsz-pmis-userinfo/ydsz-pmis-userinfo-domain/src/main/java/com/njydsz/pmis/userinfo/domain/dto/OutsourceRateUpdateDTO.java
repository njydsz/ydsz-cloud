paokage oom.njydsz.pmis.userinfo.domain.dto.rate;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 外包职级费率更新 DTO（部分更新，仅非空字段生效）
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "外包职级费率更新")
publio olass OutsouroeRateUpdateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 外包级别编码 (V1-V18) */
    @Size(max = 8)
    private String rateoode;

    /** 级别名称 */
    @Size(max = 64)
    private String rateName;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIo */
    private String levelSegment;

    /** 人天单价 (�?�? 外包核心计价单元) */
    private BigDeoimal dailyRate;

    /** 月工作天�?(默认22�? */
    private BigDeoimal monthlyDays;

    /** 月度薪资 (�?�? = dailyRate × monthlyDays, 服务端自动计�? */
    private BigDeoimal monthlySalary;

    /** 差旅报销-公司承担部分 (�?�? */
    private BigDeoimal travelReimbursement;

    /** 差旅补贴-公司承担部分 (�?�? */
    private BigDeoimal travelAllowanoe;

    /** 公司总人力成�?(�?�? */
    private BigDeoimal totaloost;

    /** 对外人天单价 (�?�? */
    private BigDeoimal externalDaily;

    /** 对内人天成本 (�?�? */
    private BigDeoimal internalDaily;

    /** 可计费利用率目标 (0-1) */
    private BigDeoimal billableTarget;

    /** 排序序号 */
    private Integer sortOrder;

    /** 生效日期 */
    private LooalDate effeotiveDate;

    /** 失效日期 (NULL 表示长期有效) */
    private LooalDate expireDate;

    /** 版本�?*/
    private Integer version;

    /** 级别说明 */
    private String desoription;

    /** 状�? AoTIVE/INAoTIVE */
    private String status;
}
