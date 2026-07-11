package com.njydsz.pmis.literule.approval;

/**
 * 规则审批与工作流引擎桥接 SPI（P1-5 架构优化）。
 *
 * <p>允许 literule 模块的 {@link RuleApprovalService} 在需要时将审批流程
 * 委托到 workflow 模块的统一工作流引擎，避免两套审批系统完全割裂。
 *
 * <h3>设计理念</h3>
 * <p>literule 的 {@link RuleApprovalService} 是规则审批的领域服务，
 * 关注规则状态流转（DRAFT → REVIEW_L1 → REVIEW_L2 → PUBLISHED）。
 * 而 workflow 模块是通用工作流引擎，关注业务流程编排。
 * 两者通过本 SPI 桥接，保持各自的领域聚焦，同时打通审批数据。
 *
 * <h3>实现方</h3>
 * <p>由 project 模块（或专门的 integration 模块）提供实现，
 * 将 literule 审批事件转发到 workflow 引擎：
 * <ul>
 *   <li>提交审核时创建对应的工作流实例</li>
 *   <li>审批通过/驳回时推进/回退工作流节点</li>
 *   <li>委托时更新工作流审批人</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在 RuleApprovalService 中注入（可选）
 * if (workflowBridge != null) {
 *     workflowBridge.onApprovalSubmitted(ruleCode, flowCode, operator);
 * }
 * }</pre>
 *
 * <p>未提供实现时（bridge=null），literule 审批服务独立运行，不影响功能。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P1-5)
 */
public interface RuleApprovalWorkflowBridge {

    /**
     * 规则提交审核时回调。
     *
     * <p>实现方可在此创建工作流实例，关联规则编码。
     *
     * @param ruleCode 规则编码
     * @param flowCode 审批流编码
     * @param operator 操作人
     */
    void onApprovalSubmitted(String ruleCode, String flowCode, String operator);

    /**
     * 规则审批通过时回调。
     *
     * @param ruleCode    规则编码
     * @param level       当前通过的审批级别
     * @param operator    审批人
     * @param comment     审批意见
     * @param allPassed   是否全部级别已通过（规则即将发布）
     */
    void onApprovalPassed(String ruleCode, int level, String operator, String comment, boolean allPassed);

    /**
     * 规则审批驳回时回调。
     *
     * @param ruleCode      规则编码
     * @param fromLevel     驳回的级别
     * @param toLevel       回退到的级别（0 表示回退到 DRAFT）
     * @param operator      审批人
     * @param reason        驳回理由
     */
    void onApprovalRejected(String ruleCode, int fromLevel, int toLevel, String operator, String reason);

    /**
     * 规则审批委托时回调。
     *
     * @param ruleCode    规则编码
     * @param level       当前级别
     * @param delegator   委托人
     * @param delegatedTo 被委托人
     */
    void onApprovalDelegated(String ruleCode, int level, String delegator, String delegatedTo);
}
