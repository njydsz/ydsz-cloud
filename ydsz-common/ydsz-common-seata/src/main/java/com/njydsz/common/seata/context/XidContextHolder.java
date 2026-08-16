package com.njydsz.common.seata.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.njydsz.common.seata.api.TransactionType;

/**
 * XID 与事务上下文持有者
 *
 * <p>基于 {@link TransmittableThreadLocal} 存储全局事务 XID 及事务类型/名称，
 * 支持线程池场景下的上下文透传。
 *
 * <p>合并了原 {@code TransactionContext} 的事务类型标记能力，
 * 消除双 ThreadLocal 并存导致的内存占用与生命周期不一致问题。
 *
 * <p><b>使用注意</b>：必须在请求结束时调用 {@link #remove()} 清理，
 * 建议在 ServletFilter 或 AOP 切面的 finally 块中统一清理。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public final class XidContextHolder {

    private static final TransmittableThreadLocal<XidContext> HOLDER = new TransmittableThreadLocal<>();

    private XidContextHolder() {
        // 工具类，禁止实例化
    }

    /**
     * 设置全局事务 XID
     *
     * @param xid 全局事务 ID
     */
    public static void setXid(String xid) {
        XidContext ctx = HOLDER.get();
        if (ctx == null) {
            ctx = new XidContext();
            HOLDER.set(ctx);
        }
        ctx.xid = xid;
    }

    /**
     * 获取当前线程的全局事务 XID
     *
     * @return 全局事务 ID，无事务上下文时返回 null
     */
    public static String getXid() {
        XidContext ctx = HOLDER.get();
        return ctx != null ? ctx.xid : null;
    }

    /**
     * 设置当前线程的事务类型与名称
     *
     * @param type 事务类型（非空）
     * @param name 事务名称（可为空）
     */
    public static void setTransactionType(TransactionType type, String name) {
        if (type == null) {
            throw new IllegalArgumentException("TransactionType cannot be null");
        }
        XidContext ctx = HOLDER.get();
        if (ctx == null) {
            ctx = new XidContext();
            HOLDER.set(ctx);
        }
        ctx.type = type;
        ctx.name = name != null ? name : "";
    }

    /**
     * 获取当前线程声明的事务类型
     *
     * @return 事务类型，未声明时返回 null
     */
    public static TransactionType getRequiredType() {
        XidContext ctx = HOLDER.get();
        return ctx != null ? ctx.type : null;
    }

    /**
     * 获取当前线程声明的事务名称
     *
     * @return 事务名称，未声明时返回 null
     */
    public static String getTransactionName() {
        XidContext ctx = HOLDER.get();
        return ctx != null ? ctx.name : null;
    }

    /**
     * 判断当前线程是否已声明事务类型
     *
     * @return 已声明返回 true，否则返回 false
     */
    public static boolean isTransactionActive() {
        XidContext ctx = HOLDER.get();
        return ctx != null && ctx.type != null;
    }

    /**
     * 获取完整的 XID 上下文（供框架内部使用）
     *
     * @return 当前上下文，不存在返回 null
     */
    public static XidContext current() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的事务上下文
     *
     * <p><b>注意</b>：此方法仅应在请求结束时调用一次，避免上下文污染。
     */
    public static void remove() {
        HOLDER.remove();
    }

    /**
     * XID 上下文数据对象
     *
     * <p>封装全局事务 ID、事务类型、事务名称和开始时间，支持 TTL 透传。
     */
    public static final class XidContext {

        private String xid;
        private TransactionType type;
        private String name;
        private long startTime;

        public String getXid() {
            return xid;
        }

        public TransactionType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }
    }
}
