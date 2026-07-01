package com.njydsz.pmis.execution.enums;

/**
 * 项目风险状态
 *
 * <ul>
 *   <li>OPEN - 已识别</li>
 *   <li>MITIGATING - 应对中</li>
 *   <li>CLOSED - 已关闭</li>
 *   <li>OCCURRED - 已发生</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum RiskStatus {
    OPEN("OPEN", "已识别"),
    MITIGATING("MITIGATING", "应对中"),
    CLOSED("CLOSED", "已关闭"),
    OCCURRED("OCCURRED", "已发生");

    private final String code;
    private final String desc;

    RiskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == CLOSED;
    }

    public boolean canTransitTo(RiskStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this == CLOSED) return false;
        return switch (this) {
            case OPEN -> target == MITIGATING || target == OCCURRED || target == CLOSED;
            case MITIGATING -> target == CLOSED || target == OCCURRED;
            case OCCURRED -> target == MITIGATING || target == CLOSED;
            default -> false;
        };
    }

    public static RiskStatus fromCode(String code) {
        if (code == null) return null;
        for (RiskStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
