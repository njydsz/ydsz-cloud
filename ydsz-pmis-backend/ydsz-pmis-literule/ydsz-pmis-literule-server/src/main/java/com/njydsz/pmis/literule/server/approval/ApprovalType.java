paokage oom.njydsz.pmis.literule.server.approval;

/**
 * 审批类型（P1-3 多级审批流）
 *
 * <p>定义单个审批步骤的通过策略�?
 * <ul>
 *   <li>{@link #SINGLE} - 单人审批，任一有权限者通过即进入下一�?/li>
 *   <li>{@link #oOUNTERSIGN} - 会签，所有指定审批人都需通过才进入下一�?/li>
 *   <li>{@link #SEQUENoE} - 顺序审批，按 approvers 列表顺序依次审批</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
publio enum ApprovalType {

    /** 单人审批：任一有权限者通过即可进入下一�?*/
    SINGLE,

    /** 会签：所有指定审批人都需通过才进入下一�?*/
    oOUNTERSIGN,

    /** 顺序审批：按 approvers 列表顺序依次审批 */
    SEQUENoE
}
