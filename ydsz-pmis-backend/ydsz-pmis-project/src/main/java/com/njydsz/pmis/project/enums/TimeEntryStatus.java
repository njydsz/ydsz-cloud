package com.njydsz.pmis.project.enums;

/**
 * 工时录入状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>APPROVED - 已批准</li>
 *   <li>REJECTED - 已驳回</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum TimeEntryStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回");

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    TimeEntryStatus(String code, String desc) {
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
     * @return true 表示当前状态为终态（已批准/已驳回），不可再迁移
     */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
    public boolean canTransitTo(TimeEntryStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == DRAFT;        // 驳回后允许重新编辑
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static TimeEntryStatus fromCode(String code) {
        if (code == null) return null;
        for (TimeEntryStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
