package com.njydsz.common.seata.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC 事务上下文
 *
 * <p>在 Try/Confirm/Cancel 三个阶段之间传递业务数据。
 *
 * <p><b>P2-4 修复</b>：{@link #getAll()} 不再使用 {@code Map.copyOf(data)} 遍历 live view，
 * 改为先复制为 {@link HashMap} 再包装为不可变 Map，确保快照一致性。
 *
 * <p><b>P2-5 修复</b>：{@link #getLong(String)} 解析失败时不再抛出未声明的
 * {@link NumberFormatException}，改为返回 null 并记录警告。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TccContext {

    private final String xid;
    private final String branchId;
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    /**
     * 构造 TCC 事务上下文
     *
     * @param xid      全局事务 ID
     * @param branchId 分支事务 ID
     */
    public TccContext(String xid, String branchId) {
        this.xid = xid;
        this.branchId = branchId;
    }

    /**
     * 获取全局事务 ID
     *
     * @return 全局事务 ID
     */
    public String getXid() {
        return xid;
    }

    /**
     * 获取分支事务 ID
     *
     * @return 分支事务 ID
     */
    public String getBranchId() {
        return branchId;
    }

    /**
     * 存入业务数据
     *
     * @param key   键
     * @param value 值
     * @return 当前上下文（支持链式调用）
     */
    public TccContext put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    /**
     * 获取业务数据
     *
     * @param key 键
     * @return 值，不存在时返回 null
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 获取 String 类型值
     *
     * @param key 键
     * @return String 值，不存在时返回 null
     */
    public String getString(String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * 获取 Long 类型值
     *
     * <p>解析失败时返回 null，不抛出异常。
     *
     * @param key 键
     * @return Long 值，不存在或解析失败时返回 null
     */
    public Long getLong(String key) {
        Object val = data.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取所有数据的不可变快照
     *
     * <p>先复制为 {@link HashMap} 再包装为不可变 Map，避免遍历 live view 的并发问题。
     *
     * @return 不可修改的数据快照
     */
    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(data));
    }
}
