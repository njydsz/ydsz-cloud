package com.njydsz.common.seata.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 全局事务 XID 上下文持有者
 *
 * <p>基于 {@link TransmittableThreadLocal} 实现，支持跨线程池的 XID 透传，
 * 解决父子线程、线程池场景下 XID 丢失的问题。
 *
 * <p><b>P2-3 新增</b>：从 {@code AbstractTransactionManager} 抽取出独立的 XID 持有者，
 * 解决 {@code AbstractTransactionManager} 与 {@code DefaultXidPropagator} 之间的循环依赖。
 *
 * <p>工作原理：
 * <ul>
 *   <li>{@code AbstractTransactionManager} 在 begin 时 set XID，在 commit/rollback 后 remove</li>
 *   <li>{@code DefaultXidPropagator} 在 HTTP 入口 restore XID，在出口 propagate XID</li>
 *   <li>{@code FeignXidRequestInterceptor} / {@code XidServletFilter} 依赖此持有者实现上下文传播</li>
 *   <li>{@code SeataTaskDecorator} / {@code SeataExecutors} 通过 TTL 自动透传 XID 到子线程</li>
 * </ul>
 *
 * <p>线程安全性：每个线程拥有独立的 XID 副本，TTL 在任务提交/执行时自动 snapshot & restore。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public final class XidContextHolder {

    /**
     * 基于 TransmittableThreadLocal 的 XID 持有者
     *
     * <p>当使用 {@code TtlExecutors} 包装线程池，或手动调用
     * {@code TtlRunnable.get(runnable)} / {@code TtlCallable.get(callable)} 时，
     * 子线程自动获取父线程的 XID 副本。
     */
    private static final TransmittableThreadLocal<String> XID_HOLDER = new TransmittableThreadLocal<>();

    private XidContextHolder() {
        // utility class
    }

    /**
     * 获取当前线程的全局事务 XID
     *
     * @return XID，无事务上下文时返回 null
     */
    public static String getXid() {
        return XID_HOLDER.get();
    }

    /**
     * 设置当前线程的全局事务 XID
     *
     * @param xid 全局事务 ID，null 等效于 {@link #remove()}
     */
    public static void setXid(String xid) {
        if (xid == null) {
            XID_HOLDER.remove();
        } else {
            XID_HOLDER.set(xid);
        }
    }

    /**
     * 清除当前线程的全局事务 XID
     *
     * <p>应在事务完成后调用，避免 ThreadLocal 内存泄漏。
     */
    public static void remove() {
        XID_HOLDER.remove();
    }

    /**
     * 判断当前线程是否存在事务上下文
     *
     * @return 存在返回 true，否则返回 false
     */
    public static boolean hasXid() {
        return XID_HOLDER.get() != null;
    }

    // ============= 兼容旧 API（保持向后兼容） =============

    /**
     * @deprecated 使用 {@link #getXid()} 替代
     */
    @Deprecated
    public static String getXidFromHolder() {
        return getXid();
    }

    /**
     * @deprecated 使用 {@link #setXid(String)} 替代
     */
    @Deprecated
    public static void setXidToHolder(String xid) {
        setXid(xid);
    }

    /**
     * @deprecated 使用 {@link #remove()} 替代
     */
    @Deprecated
    public static void removeXidFromHolder() {
        remove();
    }
}
