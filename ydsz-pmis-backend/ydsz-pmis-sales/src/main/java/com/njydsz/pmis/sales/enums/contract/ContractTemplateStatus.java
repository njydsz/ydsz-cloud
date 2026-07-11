package com.njydsz.pmis.sales.enums.contract;

/**
 * 合同模板状态
 *
 * <ul>
 *   <li>DRAFT - 草稿（不可被引用）</li>
 *   <li>PUBLISHED - 已发布（可被合同引用）</li>
 *   <li>DEPRECATED - 已下线（保留历史，新合同不可引用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ContractTemplateStatus {
    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    DEPRECATED("DEPRECATED", "已下线");

    private final String code;
    private final String desc;

    ContractTemplateStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态（DEPRECATED）返回 true，否则返回 false
     */
    public boolean isTerminal() {
        return this == DEPRECATED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态。
     *
     * <p>线性转换：DRAFT→PUBLISHED→DEPRECATED；PUBLISHED 可回退到 DRAFT；DEPRECATED 为终态不可迁移。
     *
     * @param target 目标状态，为 null 或与当前状态相同时返回 false
     * @return 允许迁移返回 true，否则返回 false
     */
    public boolean canTransitTo(ContractTemplateStatus target) {
        if (target == null) return false;
        if (this == target) return false;
        if (this.isTerminal()) return false;
        return switch (this) {
            case DRAFT -> target == PUBLISHED;
            case PUBLISHED -> target == DEPRECATED || target == DRAFT;
            default -> false;
        };
    }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static ContractTemplateStatus fromCode(String code) {
        if (code == null) return null;
        for (ContractTemplateStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
