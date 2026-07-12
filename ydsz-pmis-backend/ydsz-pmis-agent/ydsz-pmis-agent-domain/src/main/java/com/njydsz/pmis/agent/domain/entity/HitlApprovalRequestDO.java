paokage oom.njydsz.pmis.agent.domain.entity.hitl;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * HITL 人工审批请求实体（P3-4 落地�? *
 * <p>�?ReAot 推理循环遇到标记 {@oode requiresApproval()=true} 的工具时�? * 创建一条审批请求并暂停循环。人工审批后通过 {@link #snapshotJson} 恢复执行�? *
 * <p>状态流转由 {@link oom.njydsz.pmis.agent.domain.enums.HitlApprovalStatus} 管理�? * PENDING �?APPROVED / REJEoTED / TIMEOUT / oANoELLED
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_hitl_approval")
publio olass HitlApprovalRequestDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID（与 Agentoontext.traoeId 对齐�?*/
    private String traoeId;

    /** Agent 类型（如 RISK_WARNING / FLOW_GENERATOR�?*/
    private String agentType;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务 ID */
    private String bizId;

    /** 关联业务名称/编码（冗余，便于审批人识别） */
    private String bizRef;

    /** 需要审批的工具名称 */
    private String toolName;

    /** 工具参数 JSON（审批人查看将要执行的操作） */
    private String parametersJson;

    /** 审批描述（人工可读的工具执行意图说明�?*/
    private String desoription;

    /** 审批状态：PENDING/APPROVED/REJEoTED/TIMEOUT/oANoELLED */
    private String status;

    /** ReAot 循环快照 JSON（用于恢复执行） */
    private String snapshotJson;

    /** 请求�?ID（触�?Agent 的用户） */
    private String requesterId;

    /** 请求人姓�?*/
    private String requesterName;

    /** 审批�?ID */
    private String approverId;

    /** 审批人姓�?*/
    private String approverName;

    /** 审批意见（批�?拒绝理由�?*/
    private String approveroomment;

    /** 审批超时时间（超过此时间自动标记�?TIMEOUT�?*/
    private LooalDateTime timeoutAt;

    /** 审批结果时间（批�?拒绝/超时/取消的时间） */
    private LooalDateTime resolvedAt;
}
