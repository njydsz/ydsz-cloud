package com.njydsz.pmis.user.enums;

/**
 * Bench 闲置状态
 *
 * <ul>
 *   <li>ACTIVE - 闲置中（计入闲置池）</li>
 *   <li>EXITED - 已出池（被分配或转培训）</li>
 *   <li>TRAINING - 培训中（仍记为 Bench 但不计闲置成本）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum BenchStatus {
    ACTIVE("ACTIVE", "闲置中"),
    EXITED("EXITED", "已出池"),
    TRAINING("TRAINING", "培训中");

    private final String code;
    private final String desc;

    BenchStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean canExit() {
        return this == ACTIVE;
    }

    public static BenchStatus fromCode(String code) {
        if (code == null) return null;
        for (BenchStatus b : values()) {
            if (b.code.equalsIgnoreCase(code)) return b;
        }
        return null;
    }
}
