package com.njydsz.pmis.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
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

    /** 退回目标节点编码（仅 REJECT 时使用，单节点退回，向后兼容） */
    private String targetNodeCode;

    /**
     * GAP-P0-2: 退回多节点同退目标节点编码列表（仅 REJECT 时使用）
     *
     * <p>对标飞书"退回多节点同退"：勾选多个前序节点均重新审批。
     * 非空时优先于 {@link #targetNodeCode}；为空时降级到单节点退回（向后兼容）。
     */
    private List<String> targetNodeCodes;

    /** 转办/委派目标人 */
    private Long targetUserId;

    /** 转办/委派目标人姓名 */
    private String targetUserName;

    /** 租户 ID */
    private Long tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
