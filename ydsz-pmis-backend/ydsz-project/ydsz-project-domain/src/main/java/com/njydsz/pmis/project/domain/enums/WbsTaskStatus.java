package com.njydsz.project.domain.enums;

/**
 * WBS 任务状态
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum WbsTaskStatus {
    PLANNED("PLANNED", "已规划"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    BLOCKED("BLOCKED", "阻塞"),
    IN_REVIEW("IN_REVIEW", "验收中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    WbsTaskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取状态编码
     *
     * @return 状态编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取状态中文描述
     *
     * @return 状态中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已完成/已取消），不可再迁移
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
    public boolean canTransitTo(WbsTaskStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case PLANNED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == BLOCKED || target == IN_REVIEW
                    || target == COMPLETED || target == CANCELLED;
            case BLOCKED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_REVIEW -> target == COMPLETED || target == IN_PROGRESS
                    || target == CANCELLED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static WbsTaskStatus fromCode(String code) {
        if (code == null) return null;
        for (WbsTaskStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
