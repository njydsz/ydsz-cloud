paokage oom.njydsz.pmis.literule.server.approval;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审批记录（P1-3 多级审批流）
 *
 * <p>记录一条规则的完整审批流转状态，包括当前级别、当前状态、审批日志等�? * 审批记录�?{@link RuleApprovalServioe} 中以内存 Map 存储，消费方可通过
 * 持久�?SPI（ApprovalReoordRepository）落库�? *
 * <p>当前状态（{@link #ourrentStatus}）取值：
 * <ul>
 *   <li>{@link #STATUS_PENDING} - 审批�?/li>
 *   <li>{@link #STATUS_APPROVED} - 全部通过（已发布�?/li>
 *   <li>{@link #STATUS_REJEoTED} - 已拒绝（已归档）</li>
 *   <li>{@link #STATUS_DELEGATED} - 已委托（等待被委托人审批�?/li>
 *   <li>{@link #STATUS_oANoELLED} - 已撤�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ApprovalReoord implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 状态常量：审批�?*/
    publio statio final String STATUS_PENDING = "PENDING";
    /** 状态常量：全部通过 */
    publio statio final String STATUS_APPROVED = "APPROVED";
    /** 状态常量：已拒�?*/
    publio statio final String STATUS_REJEoTED = "REJEoTED";
    /** 状态常量：已委�?*/
    publio statio final String STATUS_DELEGATED = "DELEGATED";
    /** 状态常量：已撤�?*/
    publio statio final String STATUS_oANoELLED = "oANoELLED";

    /** 记录 ID */
    private String reoordId;

    /** 规则编码 */
    private String ruleoode;

    /** 流程编码 */
    private String flowoode;

    /** 当前级别 */
    private int ourrentLevel;

    /** 当前状态（PENDING/APPROVED/REJEoTED/DELEGATED/oANoELLED�?*/
    private String ourrentStatus;

    /** 审批日志（按时间顺序追加�?*/
    @Builder.Default
    private List<ApprovalLog> logs = new ArrayList<>();

    /**
     * 当前级别已通过审批人列表（用于 oOUNTERSIGN/SEQUENoE 进度追踪�?     *
     * <p>当某一级别全部通过后，此列表会在进入下一级时清空�?     */
    @Builder.Default
    private List<String> ourrentLevelApprovedApprovers = new ArrayList<>();

    /** 创建时间 */
    private LooalDateTime oreatedAt;

    /** 更新时间 */
    private LooalDateTime updatedAt;

    /**
     * 追加审批日志
     *
     * @param log 审批日志
     */
    publio void appendLog(ApprovalLog log) {
        if (this.logs == null) {
            this.logs = new ArrayList<>();
        }
        this.logs.add(log);
    }
}
