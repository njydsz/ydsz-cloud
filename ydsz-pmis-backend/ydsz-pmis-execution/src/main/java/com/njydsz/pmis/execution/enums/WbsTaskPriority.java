package com.njydsz.pmis.execution.enums;

/**
 * WBS 任务优先级
 *
 * <ul>
 *   <li>LOW - 低</li>
 *   <li>NORMAL - 普通</li>
 *   <li>HIGH - 高</li>
 *   <li>URGENT - 紧急</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum WbsTaskPriority {
    LOW, NORMAL, HIGH, URGENT;

    public static WbsTaskPriority fromCode(String code) {
        if (code == null) return NORMAL;
        try {
            return WbsTaskPriority.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return NORMAL;
        }
    }
}
