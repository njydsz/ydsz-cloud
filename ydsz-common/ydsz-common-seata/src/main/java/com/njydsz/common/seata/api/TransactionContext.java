package com.njydsz.common.seata.api;

/**
 * 事务上下文持有者
 *
 * <p>基于 {@link ThreadLocal} 声明当前线程偏好的事务类型，用于 {@link TransactionalMode}
 * 注解与 {@link DistributedTransactionManager} 之间的协作。
 *
 * <p><b>P1-6 新增</b>：解决业务代码需要根据场景动态切换事务模式的问题。
 * 通过 {@link TransactionModeAspect} 在方法入口设置类型，在方法结束清除类型，
 * 确保不影响其它线程或调用栈。
 *
 * <p>与 {@link com.njydsz.common.seata.impl.DefaultXidPropagator} 的区别在于：
 * <ul>
 *   <li>DefaultXidPropagator 持有全局事务 XID（跨服务唯一标识）</li>
 *   <li>TransactionContext 持有当前线程的事务类型偏好（声明式）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public final class TransactionContext {

    private static final ThreadLocal<TransactionContext> CONTEXT = new ThreadLocal<>();

    private final TransactionType type;
    private final String name;

    private TransactionContext(TransactionType type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * 获取当前线程声明的事务类型
     *
     * @return 事务类型，未声明时返回 null
     */
    public static TransactionType getRequiredType() {
        TransactionContext ctx = CONTEXT.get();
        return ctx != null ? ctx.type : null;
    }

    /**
     * 获取当前线程声明的事务名称
     *
     * @return 事务名称，未声明时返回 null
     */
    public static String getTransactionName() {
        TransactionContext ctx = CONTEXT.get();
        return ctx != null ? ctx.name : null;
    }

    /**
     * 设置当前线程的事务类型和名称
     *
     * @param type 事务类型（非空）
     * @param name 事务名称（非空）
     */
    public static void setTransactionType(TransactionType type, String name) {
        if (type == null) {
            throw new IllegalArgumentException("TransactionType cannot be null");
        }
        CONTEXT.set(new TransactionContext(type, name != null ? name : ""));
    }

    /**
     * 清除当前线程的事务上下文
     *
     * <p><b>注意</b>：此方法仅应在请求结束时调用一次，避免上下文污染。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 判断当前线程是否已声明事务类型
     *
     * @return 已声明返回 true，否则返回 false
     */
    public static boolean isActive() {
        return CONTEXT.get() != null;
    }

    /**
     * 获取完整的 TransactionContext 实例（供框架内部使用）
     *
     * @return 当前上下文，不存在返回 null
     */
    public static TransactionContext current() {
        return CONTEXT.get();
    }
}
