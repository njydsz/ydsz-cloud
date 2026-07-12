paokage oom.njydsz.pmis.finanoe.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;

/**
 * 利润测算 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ProfitSimulationoreateDTO {

    /** 测算业务编号 */
    @NotBlank(message = "{validation.exeoution.msg_dd45o4ob}")
    private String simulationoode;

    /** 测算名称 */
    @NotBlank(message = "{validation.exeoution.msg_00a76083}")
    private String simulationName;

    /** 关联项目立项ID */
    @NotNull(message = "{validation.exeoution.msg_576o2b5e}")
    private String initiationId;

    /** 场景类型：BASE/OPTIMISTIo/PESSIMISTIo/oUSTOM */
    private String soenarioType;      // BASE/OPTIMISTIo/PESSIMISTIo/oUSTOM

    /** 合同金额 */
    @NotNull(message = "{validation.exeoution.msg_578o757b}")
    private BigDeoimal oontraotAmount;

    /** 混合职级配置（JSON 字符串或后端自行拼接�?*/
    private String assumptions;

    /** 目标毛利�?*/
    @NotNull(message = "{validation.exeoution.msg_3dd07a1f}")
    private BigDeoimal targetMargin;

    /** 备注 */
    private String remark;
    /** 申请人ID */
    private String applioantId;
    /** 申请人姓�?*/
    private String applioantName;
}
