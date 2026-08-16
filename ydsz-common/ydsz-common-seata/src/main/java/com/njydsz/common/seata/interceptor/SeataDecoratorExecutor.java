package com.njydsz.common.seata.interceptor;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.seata.context.XidContextHolder;

/**
 * Seata 感知的 Executor 包装器
 *
 * <p>将普通 Executor 包装为支持 XID 传递的执行器。
 * 用于装饰已有的 Executor 实例。
 *
 * <p><b>P2-3 修复</b>：改为依赖 {@link XidContextHolder}，
 * 不再使用 {@code AbstractTransactionManager} 的包级私有方法。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
class SeataDecoratorExecutor implements Executor {

    private static final Logger LOG = LoggerFactory.getLogger(SeataDecoratorExecutor.class);

    private final Executor delegate;

    SeataDecoratorExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        // 捕获提交线程的 XID 并包装任务
        String capturedXid = XidContextHolder.getXid();
        LOG.debug("Capturing XID for async execution: {}", capturedXid);
        delegate.execute(new SeataRunnable(command, capturedXid));
    }

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
                XidContextHolder.setXid(capturedXid);
            }
            try {
                delegate.run();
            } finally {
                XidContextHolder.remove();
            }
        }
    }
}
