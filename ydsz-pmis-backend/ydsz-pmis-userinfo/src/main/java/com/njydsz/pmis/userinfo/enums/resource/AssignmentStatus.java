package com.njydsz.pmis.userinfo.enums.resource;

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

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    AssignmentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 判断是否为终态（已离场/已取消）
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == RELEASED || this == CANCELLED;
    }

    /**
     * 判断当前状态是否可流转到目标状态
     *
     * @param target 目标状态
     * @return 允许流转返回 true，否则返回 false；target 为 null 返回 false
     */
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

    /**
     * 根据编码解析枚举
     *
     * @param code 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；code 为 null 或无匹配时返回 null
     */
    public static AssignmentStatus fromCode(String code) {
        if (code == null) return null;
        for (AssignmentStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
