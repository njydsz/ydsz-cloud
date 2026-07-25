package com.njydsz.common.seata.impl;

import com.njydsz.common.seata.api.XidPropagator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 XID 传播器实现
 *
 * <p>使用 ThreadLocal 存储 XID，支持 HTTP Header 和 MQ 属性的序列化/反序列化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultXidPropagator implements XidPropagator {

    private static final Logger log = LoggerFactory.getLogger(DefaultXidPropagator.class);

    // P0-F5: 委托 AbstractTransactionManager 的统一 ThreadLocal

    @Override
    public String serialize(String xid) {
        return xid;
    }

    @Override
    public String deserialize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.trim();
    }

    @Override
    public void bind(String xid) {
        if (xid != null) {
            AbstractTransactionManager.setXidToHolder(xid);
            log.debug("XID bound to current thread: {}", xid);
        }
    }

    @Override
    public String currentXid() {
        return AbstractTransactionManager.getXidFromHolder();
    }

    @Override
    public void unbind() {
        AbstractTransactionManager.removeXidFromHolder();
    }
}
