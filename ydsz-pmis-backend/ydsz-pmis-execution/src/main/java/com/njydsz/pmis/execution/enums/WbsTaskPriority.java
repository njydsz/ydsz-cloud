package com.njydsz.pmis.execution.enums;

/**
 * WBS 任务优先级
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
