package com.remisoft.common.seata.impl;

import com.remisoft.common.seata.api.XidPropagator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 XID 传播器实现
 *
 * <p>使用 ThreadLocal 存储 XID，支持 HTTP Header 和 MQ 属性的序列化/反序列化。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class DefaultXidPropagator implements XidPropagator {

    private static final Logger log = LoggerFactory.getLogger(DefaultXidPropagator.class);

    // P0-F5: 委托 AbstractTransactionManager 的统一 ThreadLocal

    /**
     * 将 XID 序列化为传输格式（直接返回原值）
     *
     * @param xid 全局事务 ID
     * @return 序列化后的字符串
     */
    @Override
    public String serialize(String xid) {
        return xid;
    }

    /**
     * 从传输格式反序列化 XID（去除首尾空白）
     *
     * @param header 传输内容
     * @return 解析出的 XID，无效时返回 null
     */
    @Override
    public String deserialize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.trim();
    }

    /**
     * 将 XID 绑定到当前线程（委托给 AbstractTransactionManager 的 ThreadLocal）
     *
     * @param xid 全局事务 ID
     */
    @Override
    public void bind(String xid) {
        if (xid != null) {
            AbstractTransactionManager.setXidToHolder(xid);
            log.debug("XID bound to current thread: {}", xid);
        }
    }

    /**
     * 从当前线程获取 XID
     *
     * @return 当前线程的 XID，无事务上下文时返回 null
     */
    @Override
    public String currentXid() {
        return AbstractTransactionManager.getXidFromHolder();
    }

    /**
     * 清除当前线程的 XID 绑定
     */
    @Override
    public void unbind() {
        AbstractTransactionManager.removeXidFromHolder();
    }
}
