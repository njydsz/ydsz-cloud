package com.njydsz.pmis.execution.enums;

/**
 * 质保期状态
 *
 * <ul>
 *   <li>ACTIVE - 在用</li>
 *   <li>EXPIRING_SOON - 即将到期（≤30 天）</li>
 *   <li>EXPIRED - 已过期</li>
 *   <li>TERMINATED - 已终止（提前结束）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum WarrantyStatus {
    ACTIVE("ACTIVE", "在用"),
    EXPIRING_SOON("EXPIRING_SOON", "即将到期"),
    EXPIRED("EXPIRED", "已过期"),
    TERMINATED("TERMINATED", "已终止");

    private final String code;
    private final String desc;

    WarrantyStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已过期/已终止），不可再迁移
     */
    public boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED;
    }

    /**
     * 状态机迁移规则：
     * - ACTIVE → EXPIRING_SOON → EXPIRED
     * - ACTIVE → TERMINATED（手动提前终止）
     * - EXPIRING_SOON → TERMINATED（手动提前终止）
     * - 终态不可再迁移
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
    public boolean canTransitTo(WarrantyStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case ACTIVE -> target == EXPIRING_SOON || target == EXPIRED || target == TERMINATED;
            case EXPIRING_SOON -> target == EXPIRED || target == TERMINATED;
            default -> false;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static WarrantyStatus fromCode(String code) {
        if (code == null) return null;
        for (WarrantyStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
