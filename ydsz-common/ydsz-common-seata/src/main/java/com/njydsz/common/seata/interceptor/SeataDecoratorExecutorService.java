package com.njydsz.common.seata.interceptor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.seata.impl.AbstractTransactionManager;

/**
 * Seata 感知的 ExecutorService 包装器
 *
 * <p>将普通 ExecutorService 包装为支持 XID 传递的线程池服务。
 * 自动装饰 submit 和 invoke 方法，确保任务在异步线程中正确恢复 XID。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
class SeataDecoratorExecutorService implements ExecutorService {

    private static final Logger log = LoggerFactory.getLogger(SeataDecoratorExecutorService.class);

    private final ExecutorService delegate;

    SeataDecoratorExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        delegate.execute(new SeataRunnable(command, capturedXid));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        return delegate.submit(new SeataCallable<>(task, capturedXid));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        return delegate.submit(new SeataRunnable(task, capturedXid), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        return delegate.submit(new SeataRunnable(task, capturedXid));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        List<SeataCallable<T>> wrappedTasks = tasks.stream()
                .map(task -> new SeataCallable<>(task, capturedXid))
                .collect(Collectors.toList());
        return delegate.invokeAll(wrappedTasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        List<SeataCallable<T>> wrappedTasks = tasks.stream()
                .map(task -> new SeataCallable<>(task, capturedXid))
                .collect(Collectors.toList());
        return delegate.invokeAll(wrappedTasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        List<SeataCallable<T>> wrappedTasks = tasks.stream()
                .map(task -> new SeataCallable<>(task, capturedXid))
                .collect(Collectors.toList());
        return delegate.invokeAny(wrappedTasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        String capturedXid = AbstractTransactionManager.getXidFromHolder();
        List<SeataCallable<T>> wrappedTasks = tasks.stream()
                .map(task -> new SeataCallable<>(task, capturedXid))
                .collect(Collectors.toList());
        return delegate.invokeAny(wrappedTasks, timeout, unit);
    }

    // ============= 委托方法（不修改行为） =============

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    // ============= 内部包装类 =============

    /**
     * 携带 XID 上下文的 Runnable 包装器
     */
    private static class SeataRunnable implements Runnable {

        private final Runnable delegate;
        private final String capturedXid;

        SeataRunnable(Runnable delegate, String capturedXid) {
            this.delegate = delegate;
            this.capturedXid = capturedXid;
        }

        @Override
        public void run() {
            if (capturedXid != null) {
                AbstractTransactionManager.setXidToHolder(capturedXid);
            }
            try {
                delegate.run();
            } finally {
                AbstractTransactionManager.removeXidFromHolder();
            }
        }
    }

    /**
     * 携带 XID 上下文的 Callable 包装器
     */
    private static class SeataCallable<T> implements Callable<T> {

        private final Callable<T> delegate;
        private final String capturedXid;

        SeataCallable(Callable<T> delegate, String capturedXid) {
            this.delegate = delegate;
            this.capturedXid = capturedXid;
        }

        @Override
        public T call() throws Exception {
            if (capturedXid != null) {
                AbstractTransactionManager.setXidToHolder(capturedXid);
            }
            try {
                return delegate.call();
            } finally {
                AbstractTransactionManager.removeXidFromHolder();
            }
        }
    }
}
