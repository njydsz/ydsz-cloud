package com.njydsz.pmis.project.enums;

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

    public boolean isTerminal() {
        return this == DEPRECATED;
    }

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

    public static ContractTemplateStatus fromCode(String code) {
        if (code == null) return null;
        for (ContractTemplateStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
