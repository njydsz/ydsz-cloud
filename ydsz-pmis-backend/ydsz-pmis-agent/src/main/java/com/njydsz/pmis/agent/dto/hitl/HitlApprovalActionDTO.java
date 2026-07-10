package com.njydsz.pmis.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * HITL 审批操作 DTO（P3-4 落地）
 *
 * <p>用于批准 / 拒绝 / 取消审批请求时提交的参数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Data
public class HitlApprovalActionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审批人 ID */
    @NotBlank(message = "审批人 ID 不能为空")
    private String approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 审批意见（批准/拒绝理由） */
    private String comment;
}
