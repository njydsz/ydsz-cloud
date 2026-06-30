package com.njydsz.pmis.user.enums;

/**
 * 资源分配状态
 *
 * <ul>
 *   <li>RESERVED - 已预占（商机阶段，15天有效期）</li>
 *   <li>ACTIVE - 已入场（实际投入项目）</li>
 *   <li>TRANSFERRING - 调岗中（项目切换）</li>
 *   <li>RELEASED - 已离场</li>
 *   <li>CANCELLED - 已取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AssignmentStatus {
    RESERVED("RESERVED", "已预占"),
    ACTIVE("ACTIVE", "已入场"),
    TRANSFERRING("TRANSFERRING", "调岗中"),
    RELEASED("RELEASED", "已离场"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    AssignmentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == RELEASED || this == CANCELLED;
    }

    public boolean canTransitTo(AssignmentStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return switch (this) {
            case RESERVED -> target == ACTIVE || target == CANCELLED;
            case ACTIVE -> target == TRANSFERRING || target == RELEASED;
            case TRANSFERRING -> target == ACTIVE || target == RELEASED;
            default -> false;
        };
    }

    public static AssignmentStatus fromCode(String code) {
        if (code == null) return null;
        for (AssignmentStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
