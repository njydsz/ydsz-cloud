package com.njydsz.pmis.literule.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认断点注册表实现（P2-3）
 *
 * <p>基于 {@link ConcurrentHashMap} 维护规则编码集合，支撑断点的增删查。
 * 应用层通过 {@link com.njydsz.pmis.literule.api.RuleEngine#getBreakpointHook()}
 * 获取 hook 后，可调用 {@link #addBreakpoint(String)} / {@link #removeBreakpoint(String)}
 * 动态管理断点。
 *
 * <p>典型用法：
 * <pre>
 *   engine.getBreakpointHook().addBreakpoint("CPI_WARN");
 *   // ... 用户在线调试，规则评估时会在 CPI_WARN 前后触发 onBeforeEvaluate / onAfterEvaluate
 *   engine.getBreakpointHook().removeBreakpoint("CPI_WARN");
 * </pre>
 *
 * <p>线程安全：所有读写操作基于 {@link ConcurrentHashMap#keySet()}，支持多线程并发访问。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class DefaultBreakpointHook implements BreakpointHook {

    /** 已设置断点的规则编码集合 */
    private final Set<String> breakpoints = ConcurrentHashMap.newKeySet();

    /** 是否全局启用断点调试（关闭后即使集合非空也不触发） */
    private volatile boolean enabled = true;

    /**
     * 添加断点
     *
     * @param ruleCode 规则编码
     */
    public void addBreakpoint(String ruleCode) {
        if (ruleCode != null && !ruleCode.isBlank()) {
            breakpoints.add(ruleCode);
        }
    }

    /**
     * 移除断点
     *
     * @param ruleCode 规则编码
     */
    public void removeBreakpoint(String ruleCode) {
        if (ruleCode != null) {
            breakpoints.remove(ruleCode);
        }
    }

    /**
     * 清空全部断点
     */
    public void clearBreakpoints() {
        breakpoints.clear();
    }

    /**
     * 获取已设置断点的规则编码集合（只读视图）
     *
     * @return 不可修改的规则编码集合
     */
    public Set<String> getBreakpoints() {
        return Collections.unmodifiableSet(breakpoints);
    }

    /**
     * 设置断点调试总开关
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否启用断点调试
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean hasBreakpoint(String ruleCode) {
        if (!enabled || ruleCode == null) {
            return false;
        }
        return breakpoints.contains(ruleCode);
    }
}
