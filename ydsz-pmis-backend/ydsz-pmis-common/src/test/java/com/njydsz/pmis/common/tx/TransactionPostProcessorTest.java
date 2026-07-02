package com.njydsz.pmis.common.tx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TransactionPostProcessor 单元测试（P1-10）
 *
 * <p>验证事务后处理器的核心行为：
 * 1. 在事务中：动作在 afterCommit 时执行
 * 2. 在事务中：事务回滚则动作不执行
 * 3. 不在事务中：立即执行
 * 4. afterCommit 异常不影响事务
 * </p>
 */
@DisplayName("TransactionPostProcessor 事务后处理器测试")
class TransactionPostProcessorTest {

    private final TransactionPostProcessor processor = new TransactionPostProcessor();

    @AfterEach
    void clearTxContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 开启事务同步上下文，模拟 @Transactional 方法执行环境。
     */
    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    /**
     * 模拟事务提交：调用所有已注册同步回调的 afterCommit。
     */
    private void commitTransaction() {
        List<TransactionSynchronization> syncs =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        for (TransactionSynchronization sync : syncs) {
            sync.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    /**
     * 模拟事务回滚：不调用 afterCommit，直接清理。
     */
    private void rollbackTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("在事务中注册的动作应在提交后执行")
    void executeAfterCommit_inTransaction_executesAfterCommit() {
        beginTransaction();

        AtomicBoolean executed = new AtomicBoolean(false);
        processor.executeAfterCommit(() -> executed.set(true));

        // 注册时不应执行
        assertThat(executed.get()).isFalse();

        // 模拟事务提交
        commitTransaction();

        // 提交后应执行
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("在事务中注册的动作在回滚时不应执行")
    void executeAfterCommit_inTransaction_notExecutedOnRollback() {
        beginTransaction();

        AtomicBoolean executed = new AtomicBoolean(false);
        processor.executeAfterCommit(() -> executed.set(true));

        // 注册时不应执行
        assertThat(executed.get()).isFalse();

        // 模拟事务回滚
        rollbackTransaction();

        // 回滚后不应执行
        assertThat(executed.get()).isFalse();
    }

    @Test
    @DisplayName("不在事务中时应立即执行")
    void executeAfterCommit_noTransaction_executesImmediately() {
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        AtomicBoolean executed = new AtomicBoolean(false);
        processor.executeAfterCommit(() -> executed.set(true));

        // 应立即执行
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("afterCommit 中的异常不应影响事务（静默记录日志）")
    void executeAfterCommit_afterCommitException_doesNotPropagate() {
        beginTransaction();

        processor.executeAfterCommit(() -> {
            throw new RuntimeException("after-commit error");
        });

        // 模拟事务提交，不应抛出异常
        commitTransaction();
        // 如果到达这里，说明异常被正确捕获
    }

    @Test
    @DisplayName("不在事务中时动作抛异常应向上抛出")
    void executeAfterCommit_noTransaction_exceptionPropagates() {
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        assertThatThrownBy(() ->
                processor.executeAfterCommit(() -> {
                    throw new RuntimeException("non-tx error");
                })
        ).isInstanceOf(RuntimeException.class)
         .hasMessage("non-tx error");
    }

    @Test
    @DisplayName("null action 应抛 IllegalArgumentException")
    void executeAfterCommit_nullAction_throwsException() {
        assertThatThrownBy(() -> processor.executeAfterCommit(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    @DisplayName("多个动作应按注册顺序执行")
    void executeAfterCommit_multipleActions_executedInOrder() {
        beginTransaction();

        AtomicInteger order = new AtomicInteger(0);
        List<Integer> executionOrder = new ArrayList<>();

        processor.executeAfterCommit(() -> executionOrder.add(order.incrementAndGet()));
        processor.executeAfterCommit(() -> executionOrder.add(order.incrementAndGet()));
        processor.executeAfterCommit(() -> executionOrder.add(order.incrementAndGet()));

        commitTransaction();

        assertThat(executionOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("isInTransaction 在事务中应返回 true")
    void isInTransaction_inTransaction_returnsTrue() {
        beginTransaction();
        assertThat(processor.isInTransaction()).isTrue();
        rollbackTransaction();
    }

    @Test
    @DisplayName("isInTransaction 不在事务中应返回 false")
    void isInTransaction_noTransaction_returnsFalse() {
        assertThat(processor.isInTransaction()).isFalse();
    }
}
