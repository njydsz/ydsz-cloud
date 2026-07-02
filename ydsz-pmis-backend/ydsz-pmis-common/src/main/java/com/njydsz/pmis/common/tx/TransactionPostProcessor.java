package com.njydsz.pmis.common.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务后处理器（P1-10 分布式事务降级方案）
 *
 * <p>封装 Spring {@link TransactionSynchronization}，提供「事务提交后执行」能力，
 * 解决 {@code @Transactional} 内调用 Feign 的悬挂事务问题。</p>
 *
 * <h3>核心问题</h3>
 * <pre>
 *   @Transactional
 *   public void doBusiness() {
 *       mapper.insert(entity);          // 本地写
 *       feignClient.remoteWrite(...);  // 远程写（Feign）
 *   }
 * </pre>
 * <p>若 Feign 调用成功但本地事务回滚，远端数据已写入却无法撤销 → 悬挂事务。</p>
 *
 * <h3>解决方案</h3>
 * <p>将 Feign 调用推迟到本地事务提交后执行：</p>
 * <pre>
 *   @Transactional
 *   public void doBusiness() {
 *       mapper.insert(entity);
 *       txPostProcessor.executeAfterCommit(() -> feignClient.remoteWrite(...));
 *   }
 * </pre>
 * <ul>
 *   <li>本地事务提交 → Feign 调用执行（数据一致）</li>
 *   <li>本地事务回滚 → Feign 调用不执行（无悬挂）</li>
 *   <li>不在事务中 → 立即执行（兼容非事务场景）</li>
 * </ul>
 *
 * <h3>失败补偿</h3>
 * <p>afterCommit 中的异常不会影响已提交的事务，但会被记录为 ERROR 日志，
 * 供运维通过日志或对账任务进行人工/自动补偿。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class TransactionPostProcessor {

    /**
     * 在当前事务提交后执行动作。
     *
     * <p>若当前线程处于活跃事务中，动作会被注册为 {@link TransactionSynchronization}，
     * 在事务提交（{@code afterCommit}）时执行。</p>
     *
     * <p>若当前线程不在事务中，立即执行。</p>
     *
     * <p>动作抛出的异常会被捕获并记录为 ERROR 日志，不会影响已提交的事务。
     * 但如果不在事务中，异常会向上抛出。</p>
     *
     * @param action 事务提交后要执行的动作，不可为 null
     */
    public void executeAfterCommit(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 在事务中：注册 afterCommit 回调
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        // afterCommit 中的异常不影响已提交的事务，但需记录以便补偿
                        log.error("[TxPostProcessor] 事务提交后动作执行失败，需人工/对账补偿: {}", e.getMessage(), e);
                    }
                }
            });
        } else {
            // 不在事务中：立即执行
            try {
                action.run();
            } catch (Exception e) {
                log.error("[TxPostProcessor] 非事务动作执行失败: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * 判断当前线程是否处于活跃事务中。
     *
     * @return true 表示当前线程有活跃的事务同步
     */
    public boolean isInTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive();
    }
}
