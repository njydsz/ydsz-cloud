/**
 * 分布式事务后置处理层。
 *
 * <p>封装 Seata AT 模式 / TCC 模式 / Saga 模式 的事务后置处理器（TransactionPostProcessor），
 * 统一处理"主事务提交后 / 回滚后"的清理 / 补偿 / 通知操作。
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>主事务提交后：发送 MQ 消息（确保消息不会在事务回滚时发出）</li>
 *   <li>主事务回滚后：释放分布式锁 / 清理缓存</li>
 *   <li>主事务提交后：记录审计日志（异步落库）</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>Seata 客户端由 {@code SeataAutoConfiguration} 自动装配</li>
 *   <li>事务分组（{@code tx-service-group}）需与 TC Server 配置一致</li>
 *   <li>全局事务超时时间 ≤ 60s，避免长事务拖垮数据库</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.tx;
