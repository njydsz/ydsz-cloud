package com.njydsz.pmis.workflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 任务操作 DTO（完成/签收/退回/转办）
 */
@Data
public class TaskOperateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 处理人 ID */
    private Long userId;

    /** 审批意见 */
    private String comment;

    /** 审批动作：APPROVE/REJECT/DELEGATE/TRANSFER */
    private String action;

    /** 转办/委派目标用户 ID */
    private Long targetUserId;

    /** 退回目标节点 KEY（仅退回时使用） */
    private String targetNodeKey;

    /** 流程变量 */
    private Map<String, Object> variables;
}
