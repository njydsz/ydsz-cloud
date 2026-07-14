package com.njydsz.pmis.common.seata.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC 事务上下文
 *
 * <p>在 Try/Confirm/Cancel 三个阶段之间传递业务数据。
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public class TccContext {

    private final String xid;
    private final String branchId;
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public TccContext(String xid, String branchId) {
        this.xid = xid;
        this.branchId = branchId;
    }

    public String getXid() {
        return xid;
    }

    public String getBranchId() {
        return branchId;
    }

    public TccContext put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    public Long getLong(String key) {
        Object val = data.get(key);
        if (val instanceof Number num) {
            return num.longValue();
        }
        return val != null ? Long.parseLong(val.toString()) : null;
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(data);
    }
}
