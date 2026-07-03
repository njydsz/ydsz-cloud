package com.njydsz.pmis.literule.api;

/**
 * 规则生命周期状态枚举
 *
 * @author ydsz-pmis
 * @since 1.2.0
 */
public enum RuleStatus {

    /** 草稿：规则已创建但未提交审核 */
    DRAFT("草稿"),

    /** 待审核：规则已提交，等待审核 */
    REVIEW("待审核"),

    /** 已发布：规则已审核通过并生效 */
    PUBLISHED("已发布"),

    /** 已停用：规则被手动停用 */
    DISABLED("已停用"),

    /** 已归档：规则已废弃，仅保留历史记录 */
    ARCHIVED("已归档");

    private final String desc;

    RuleStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 从字符串安全解析状态枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 对应的 RuleStatus；未匹配返回 null
     * @since 1.3.0
     */
    public static RuleStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return RuleStatus.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 检查是否允许的转换
     */
    public boolean canTransitionTo(RuleStatus target) {
        return switch (this) {
            case DRAFT -> target == REVIEW || target == PUBLISHED || target == ARCHIVED;
            case REVIEW -> target == PUBLISHED || target == DRAFT;
            case PUBLISHED -> target == DISABLED || target == ARCHIVED;
            case DISABLED -> target == PUBLISHED || target == ARCHIVED;
            case ARCHIVED -> false; // 已归档不可再变更
        };
    }
}