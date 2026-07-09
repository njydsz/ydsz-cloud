package com.njydsz.pmis.project.enums.closure;

/**
 * 项目结项状态
 *
 * <ul>
 *   <li>DRAFT - 草稿</li>
 *   <li>SUBMITTED - 已提交</li>
 *   <li>UNDER_REVIEW - 审核中</li>
 *   <li>APPROVED - 已批准</li>
 *   <li>REJECTED - 已驳回</li>
 *   <li>ARCHIVED - 已归档</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ClosureStatus {
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    UNDER_REVIEW("UNDER_REVIEW", "审核中"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回"),
    ARCHIVED("ARCHIVED", "已归档");

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    ClosureStatus(String code, String desc) {
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
     * @return true 表示当前状态为终态（已归档），不可再迁移
     */
    public boolean isTerminal() {
        return this == ARCHIVED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
    public boolean canTransitTo(ClosureStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == UNDER_REVIEW || target == REJECTED;
            case UNDER_REVIEW -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == ARCHIVED;
            case REJECTED -> target == SUBMITTED || target == DRAFT;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static ClosureStatus fromCode(String code) {
        if (code == null) return null;
        for (ClosureStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
