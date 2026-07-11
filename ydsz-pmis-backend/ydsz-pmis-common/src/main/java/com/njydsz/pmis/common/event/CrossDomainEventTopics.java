package com.njydsz.pmis.common.event;

/**
 * 跨域 RocketMQ Topic 注册表
 *
 * <p>DDD 拆分后，三大业务域之间的异步通信通过 RocketMQ 事件总线完成。
 * 所有跨域 Topic 集中在此注册，便于运维排查和消费者管理。
 *
 * <h2>Topic 命名规范</h2>
 * <ul>
 *   <li>格式：{@code pmis_{source}_{event_type}}</li>
 *   <li>source 为产生事件的服务域：sales / finance / project</li>
 *   <li>event_type 为事件语义：created / updated / status_changed / approved</li>
 * </ul>
 *
 * <h2>消费者组命名规范</h2>
 * <ul>
 *   <li>格式：{@code cg_{consumer}_{topic}}</li>
 *   <li>consumer 为消费方服务域</li>
 * </ul>
 *
 * <h2>当前注册 Topic</h2>
 * <table border="1">
 *   <tr><th>Topic</th><th>生产者</th><th>消费者</th><th>说明</th></tr>
 *   <tr><td>{@link #SALES_CONTRACT_SIGNED}</td><td>sales</td><td>finance, project</td><td>合同签订 → 财务创建收款计划、项目创建立项</td></tr>
 *   <tr><td>{@link #SALES_OPPORTUNITY_WON}</td><td>sales</td><td>project</td><td>商机赢单 → 项目创建立项</td></tr>
 *   <tr><td>{@link #FINANCE_PAYMENT_RECEIVED}</td><td>finance</td><td>project, sales</td><td>回款到账 → 项目更新预算、销售更新合同回款进度</td></tr>
 *   <tr><td>{@link #FINANCE_INVOICE_ISSUED}</td><td>finance</td><td>sales</td><td>发票开具 → 销售更新合同开票进度</td></tr>
 *   <tr><td>{@link #PROJECT_INITIATION_CREATED}</td><td>project</td><td>finance, sales</td><td>立项创建 → 财务初始化预算、销售关联合同</td></tr>
 *   <tr><td>{@link #PROJECT_BUDGET_EXCEEDED}</td><td>project</td><td>finance</td><td>预算超限 → 财务冻结付款审批</td></tr>
 *   <tr><td>{@link #PROJECT_CLOSURE_APPROVED}</td><td>project</td><td>finance, sales</td><td>收尾审批通过 → 财务结算、销售释放合同保证金</td></tr>
 * </table>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class CrossDomainEventTopics {

    private CrossDomainEventTopics() {}

    // ==================== Sales → others ====================

    /** 合同签订事件：sales → finance（创建收款计划）、project（创建立项） */
    public static final String SALES_CONTRACT_SIGNED = "pmis_sales_contract_signed";

    /** 商机赢单事件：sales → project（创建立项） */
    public static final String SALES_OPPORTUNITY_WON = "pmis_sales_opportunity_won";

    // ==================== Finance → others ====================

    /** 回款到账事件：finance → project（更新预算可用额）、sales（更新合同回款进度） */
    public static final String FINANCE_PAYMENT_RECEIVED = "pmis_finance_payment_received";

    /** 发票开具事件：finance → sales（更新合同开票进度） */
    public static final String FINANCE_INVOICE_ISSUED = "pmis_finance_invoice_issued";

    // ==================== Project → others ====================

    /** 立项创建事件：project → finance（初始化预算）、sales（关联合同） */
    public static final String PROJECT_INITIATION_CREATED = "pmis_project_initiation_created";

    /** 预算超限事件：project → finance（冻结付款审批） */
    public static final String PROJECT_BUDGET_EXCEEDED = "pmis_project_budget_exceeded";

    /** 收尾审批通过事件：project → finance（结算）、sales（释放合同保证金） */
    public static final String PROJECT_CLOSURE_APPROVED = "pmis_project_closure_approved";

    // ==================== Consumer Groups ====================

    /** finance 消费合同签订事件 */
    public static final String CG_FINANCE_CONTRACT_SIGNED = "cg_finance_contract_signed";
    /** project 消费合同签订事件 */
    public static final String CG_PROJECT_CONTRACT_SIGNED = "cg_project_contract_signed";
    /** project 消费商机赢单事件 */
    public static final String CG_PROJECT_OPPORTUNITY_WON = "cg_project_opportunity_won";
    /** project 消费回款到账事件 */
    public static final String CG_PROJECT_PAYMENT_RECEIVED = "cg_project_payment_received";
    /** sales 消费回款到账事件 */
    public static final String CG_SALES_PAYMENT_RECEIVED = "cg_sales_payment_received";
    /** sales 消费发票开具事件 */
    public static final String CG_SALES_INVOICE_ISSUED = "cg_sales_invoice_issued";
    /** finance 消费立项创建事件 */
    public static final String CG_FINANCE_INITIATION_CREATED = "cg_finance_initiation_created";
    /** sales 消费立项创建事件 */
    public static final String CG_SALES_INITIATION_CREATED = "cg_sales_initiation_created";
    /** finance 消费预算超限事件 */
    public static final String CG_FINANCE_BUDGET_EXCEEDED = "cg_finance_budget_exceeded";
    /** finance 消费收尾审批事件 */
    public static final String CG_FINANCE_CLOSURE_APPROVED = "cg_finance_closure_approved";
    /** sales 消费收尾审批事件 */
    public static final String CG_SALES_CLOSURE_APPROVED = "cg_sales_closure_approved";
}
