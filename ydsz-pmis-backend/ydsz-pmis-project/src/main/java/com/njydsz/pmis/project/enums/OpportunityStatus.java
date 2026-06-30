package com.njydsz.pmis.project.enums;

/**
 * 商机状态机
 *
 * <p>状态转移图：
 * <pre>
 *   FOLLOWING ──► QUOTED ──► NEGOTIATING ──► WON ──► CONVERTED
 *      │            │              │           │
 *      ▼            ▼              ▼           ▼
 *    LOST        LOST/INVALID   LOST         LOST
 *      │
 *      ▼
 *   INVALID
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum OpportunityStatus {

    FOLLOWING("FOLLOWING", "跟进中"),
    QUOTED("QUOTED", "已报价"),
    NEGOTIATING("NEGOTIATING", "商务谈判"),
    WON("WON", "已赢单"),
    CONVERTED("CONVERTED", "已转立项"),
    LOST("LOST", "已输单"),
    INVALID("INVALID", "无效");

    private final String code;
    private final String desc;

    OpportunityStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isTerminal() {
        return this == CONVERTED || this == LOST || this == INVALID;
    }

    /**
     * 是否允许从当前状态迁移到目标状态
     */
    public boolean canTransitTo(OpportunityStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;  // 终态不能迁移
        // 任何非终态可以转为 LOST/INVALID
        if (target == LOST || target == INVALID) return true;
        // WON 可转为 CONVERTED
        if (this == WON && target == CONVERTED) return true;
        return switch (this) {
            case FOLLOWING -> target == QUOTED || target == NEGOTIATING
                    || target == LOST || target == INVALID;
            case QUOTED -> target == NEGOTIATING || target == WON
                    || target == LOST || target == INVALID;
            case NEGOTIATING -> target == WON || target == LOST || target == INVALID;
            case WON -> target == CONVERTED;
            default -> false;
        };
    }

    public static OpportunityStatus fromCode(String code) {
        if (code == null) return null;
        for (OpportunityStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
