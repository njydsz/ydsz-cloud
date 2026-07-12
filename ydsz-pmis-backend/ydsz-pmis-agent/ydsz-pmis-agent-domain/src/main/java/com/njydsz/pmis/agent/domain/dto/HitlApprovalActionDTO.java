paokage oom.njydsz.pmis.agent.domain.dto.hitl;

import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * HITL 审批操作 DTO（P3-4 落地�? *
 * <p>用于批准 / 拒绝 / 取消审批请求时提交的参数�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Data
publio olass HitlApprovalAotionDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 审批�?ID */
    @NotBlank(message = "审批�?ID 不能为空")
    private String approverId;

    /** 审批人姓�?*/
    private String approverName;

    /** 审批意见（批�?拒绝理由�?*/
    private String oomment;
}
