package com.njydsz.pmis.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 自建工作流引擎 - 任务操作 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class FlowTaskOperateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（必填） */
    @NotNull(message = "{validation.workflow.msg_5a190a79}")
    private Long taskId;

    /** 操作人 ID */
    @NotNull(message = "{validation.workflow.msg_f65f41e7}")
    private Long userId;

    /** 操作人姓名 */
    private String userName;

    /** 操作：PASS/REJECT/CLAIM/DELEGATE/TRANSFER/CC */
    private String action;

    /** 审批意见 */
    private String comment;

    /** P2-42: 审批意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE（可选） */
    private String commentType;

    /** 流程变量 */
    private Map<String, Object> variables;

    /** 退回目标节点编码（仅 REJECT 时使用） */
    private String targetNodeCode;

    /** 转办/委派目标人 */
    private Long targetUserId;

    /** 转办/委派目标人姓名 */
    private String targetUserName;

    /** 租户 ID */
    private Long tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
