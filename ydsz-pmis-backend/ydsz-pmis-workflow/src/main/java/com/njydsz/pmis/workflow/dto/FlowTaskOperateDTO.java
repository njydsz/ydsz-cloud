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
    private static final String serialVersionUID = "1";

    /** 任务 ID（必填） */
    @NotNull(message = "{validation.workflow.msg_5a190a79}")
    private String taskId;

    /** 操作人 ID */
    @NotNull(message = "{validation.workflow.msg_f65f41e7}")
    private String userId;

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

    /**
     * 目标节点编码
     *
     * <p>多场景复用：
     * <ul>
     *   <li>REJECT：单节点退回（向后兼容）</li>
     *   <li>GAP-P2-9 自由流（JUMP）：运行时动态指定下一节点编码，目标节点必须存在于当前流程定义中，
     *       且目标节点的 {@code ext.freeJump=true}（节点级白名单）才允许跳转</li>
     * </ul>
     */
    private String targetNodeCode;

    /**
     * GAP-P0-2: 退回多节点同退目标节点编码列表（仅 REJECT 时使用）
     *
     * <p>对标飞书"退回多节点同退"：勾选多个前序节点均重新审批。
     * 非空时优先于 {@link #targetNodeCode}；为空时降级到单节点退回（向后兼容）。
     */
    private List<String> targetNodeCodes;

    /**
     * GAP-P2-9: 自由流（JUMP）运行时指定目标节点办理人列表
     *
     * <p>对标钉钉/飞书"自由流"能力：跳转时可显式指定目标节点的办理人（用户 ID 字符串列表，
     * 如 {@code ["1001","1002"]}）。非空时覆盖目标节点 {@code permissionFlag} 解析出的默认办理人；
     * 为空时回退到节点配置的办理人解析逻辑。
     *
     * <p>仅 {@code action=JUMP} 时生效，其他操作忽略该字段。
     */
    private List<String> targetAssignees;

    /** 转办/委派目标人 */
    private String targetUserId;

    /** 转办/委派目标人姓名 */
    private String targetUserName;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
