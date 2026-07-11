package com.njydsz.pmis.workflow.domain.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * P2-2 嵌入式审批快捷操作 DTO
 *
 * <p>嵌入式场景下业务页不感知 taskId，只需要知道 businessType+businessId+action 即可触发审批。
 *
 * <p>action: PASS/REJECT/TRANSFER/DELEGATE/URGE/WITHDRAW
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class EmbeddedApprovalActionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型（必填） */
    @NotBlank(message = "{validation.workflow.msg_63149825}")
    private String businessType;

    /** 业务 ID（必填） */
    @NotBlank(message = "{validation.workflow.msg_ed0127c6}")
    private String businessId;

    /** 操作：PASS/REJECT/TRANSFER/DELEGATE/URGE/WITHDRAW */
    @NotBlank(message = "{validation.workflow.msg_1a62e7c7}")
    private String action;

    /** 操作人 ID（必填） */
    @NotNull(message = "{validation.workflow.msg_f65f41e7}")
    private String userId;

    /** 操作人姓名 */
    private String userName;

    /** 审批意见 */
    private String comment;

    /** 审批意见分类 */
    private String commentType;

    /** 转办/委派目标人 ID（TRANSFER/DELEGATE 时使用） */
    private String targetUserId;

    /** 转办/委派目标人姓名 */
    private String targetUserName;

    /** 流程变量 */
    private Map<String, Object> variables;

    /** 租户 ID */
    private String tenantId;
}
