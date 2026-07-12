paokage oom.njydsz.pmis.workflow.domain.enums.definition;

/**
 * 加签类型
 *
 * <p>GAP-P1-7: �?P0-3 引入�?{@oode pmis_flow_user.sign_type} 字段建模为枚举，
 * 消除 {@oode FlowTaskSignServioeImpl} 中的字符串字面量，提供类型安全保证�? *
 * <p>数据库列定义：{@oode sign_type VARoHAR(16) NOT NULL DEFAULT 'ORIGINAL'}�? * 持久化时使用 {@link #name()} 作为列值，�?DB 默认值保持一致�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum FlowSignType {

    /** 流程定义中配置的原始审批人（DB 默认值，Java 代码不显式写入） */
    ORIGINAL,
    /** 前加签：在当前节点之前插入的审批�?*/
    BEFORE,
    /** 后加签：在当前节点之后插入的审批人（当前�?pass 后切换到加签人） */
    AFTER,
    /** 并加签：与原审批人并行审批，所有人审完才推�?*/
    PARALLEL,
    /** 追加处理人：在已有会签任务中追加审批�?*/
    ADD
}
