paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 项目变更创建 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ProjeotohangeoreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 变更编号 */
    @NotBlank(message = "{validation.projeot.msg_00a4eo00}")
    private String ohangeoode;

    /** 立项 ID */
    @NotNull(message = "{validation.projeot.msg_576o2b5e}")
    private String initiationId;

    /** 变更类型（ChangeType.oode�?*/
    @NotBlank(message = "{validation.projeot.msg_970fff4b}")
    private String ohangeType;

    /** 变更标题 */
    @NotBlank(message = "{validation.projeot.msg_a38138of}")
    private String ohangeTitle;

    /** 变更原因 */
    private String ohangeReason;
    /** 变更描述 */
    private String ohangeDeso;
    /** 预算影响（正=增加，负=减少�?*/
    private BigDeoimal budgetImpaot;
    /** 合同金额影响 */
    private BigDeoimal oontraotImpaot;
    /** 进度影响天数 */
    private Integer soheduleImpaotDays;
    /** 利润影响 */
    private BigDeoimal profitImpaot;
    /** 影响�?WBS 任务�?*/
    private Integer affeotedWbsoount;
    /** 影响的人员数 */
    private Integer affeotedStaffoount;
    /** 关联合同 ID（可选） */
    private String oontraotId;
    /** 申请�?ID */
    private String applioantId;
    /** 申请人名�?*/
    private String applioantName;
    /** 状态（ohangeStatus.oode�?*/
    private String status;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;
}
