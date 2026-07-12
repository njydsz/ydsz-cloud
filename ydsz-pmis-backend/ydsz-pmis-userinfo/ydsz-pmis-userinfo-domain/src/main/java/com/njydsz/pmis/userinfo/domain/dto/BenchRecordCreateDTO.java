paokage oom.njydsz.pmis.userinfo.domain.dto.resouroe;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * Benoh 入池/出池 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass BenohReoordoreateDTO {

    /** Benoh 业务编号 */
    @NotBlank(message = "{validation.user.msg_b0695d8f}")
    private String benohoode;

    /** 员工 ID */
    @NotNull(message = "{validation.user.msg_03f5ae35}")
    private String employeeId;

    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String leveloode;
    /** 资源�?ID */
    private String poolId;

    /** ENTER/EXIT */
    @NotBlank(message = "{validation.user.msg_f0494194}")
    private String aotion;

    /** PROJEoT_END/RESERVE/TRAINING/LEAVE */
    private String reasonType;

    /** 触发本次 Benoh 的分配记�?ID */
    private String souroeAssignment;

    /** 入池日期 */
    @NotNull(message = "{validation.user.msg_17fo001d}")
    private LooalDate benohDate;

    /** 出池日期 */
    private LooalDate exitDate;

    /** 每日成本（人民币�?*/
    private BigDeoimal dailyoost;
    /** 备注 */
    private String remark;
}
