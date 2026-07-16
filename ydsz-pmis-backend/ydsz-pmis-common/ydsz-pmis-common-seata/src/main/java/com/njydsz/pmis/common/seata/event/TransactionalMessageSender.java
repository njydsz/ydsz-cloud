package com.njydsz.pmis.common.seata.event;

/**
 * 事务性消息发送接口（Transaction Outbox 模式）
 *
 * <p><b>P1-5</b>：与 {@code common-event} 模块的 Outbox 模式协同。
 * 当分布式事务提交后，通过此接口触发 outbox 消息发送，事务回滚则不发送。
 *
 * <p>实现方式：业务代码在事务内调用 {@link #sendAfterCommit}，
 * 实现类将消息暂存到 outbox 表，事务提交后由 {@code OutboxProcessor} 异步发送。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public interface TransactionalMessageSender {

    /**
     * 在事务提交后发送消息
     *
     * @param topic   消息主题
     * @param payload 消息内容
     * @param xid     全局事务 ID（用于关联事务和消息）
     */
    void sendAfterCommit(String topic, String payload, String xid);
}
