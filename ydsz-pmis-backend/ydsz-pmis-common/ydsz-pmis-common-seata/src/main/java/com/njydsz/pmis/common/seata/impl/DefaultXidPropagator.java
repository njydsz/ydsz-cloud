package com.njydsz.pmis.common.seata.impl;

import com.njydsz.pmis.common.seata.api.XidPropagator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 XID 传播器实现
 *
 * <p>使用 ThreadLocal 存储 XID，支持 HTTP Header 和 MQ 属性的序列化/反序列化。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class DefaultXidPropagator implements XidPropagator {

    private static final Logger log = LoggerFactory.getLogger(DefaultXidPropagator.class);

    private static final ThreadLocal<String> XID_CONTEXT = new ThreadLocal<>();

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
            XID_CONTEXT.set(xid);
            log.debug("XID bound to current thread: {}", xid);
        }
    }

    @Override
    public String currentXid() {
        return XID_CONTEXT.get();
    }

    @Override
    public void unbind() {
        XID_CONTEXT.remove();
    }
}
