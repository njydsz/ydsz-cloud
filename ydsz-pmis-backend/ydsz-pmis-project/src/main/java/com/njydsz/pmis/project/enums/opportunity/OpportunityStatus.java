package com.njydsz.pmis.project.enums.opportunity;

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

    /**
     * 判断当前状态是否为终态（不可再迁移）。
     *
     * @return 终态返回 true，否则返回 false
     */
    public boolean isTerminal() {
        return this == CONVERTED || this == LOST || this == INVALID;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态。
     *
     * <p>终态不可迁移；非终态可迁移到 LOST/INVALID；WON 可迁移到 CONVERTED。
     *
     * @param target 目标状态，为 null 时返回 false
     * @return 允许迁移返回 true，否则返回 false
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

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static OpportunityStatus fromCode(String code) {
        if (code == null) return null;
        for (OpportunityStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
