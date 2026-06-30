package com.njydsz.pmis.execution.enums;

/**
 * 运维工单状态
 *
 * <ul>
 *   <li>OPEN - 待派单</li>
 *   <li>ASSIGNED - 已派单</li>
 *   <li>IN_PROGRESS - 处理中</li>
 *   <li>RESOLVED - 已解决</li>
 *   <li>CLOSED - 已关闭</li>
 *   <li>CANCELLED - 已取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum OpsTicketStatus {
    OPEN("OPEN", "待派单"),
    ASSIGNED("ASSIGNED", "已派单"),
    IN_PROGRESS("IN_PROGRESS", "处理中"),
    RESOLVED("RESOLVED", "已解决"),
    CLOSED("CLOSED", "已关闭"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    OpsTicketStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    public boolean canTransitTo(OpsTicketStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        if (this.isTerminal()) return false;
        return switch (this) {
            case OPEN -> target == ASSIGNED || target == IN_PROGRESS
                    || target == CANCELLED;
            case ASSIGNED -> target == IN_PROGRESS || target == RESOLVED
                    || target == CANCELLED;
            case IN_PROGRESS -> target == RESOLVED || target == CANCELLED;
            case RESOLVED -> target == CLOSED || target == IN_PROGRESS
                    || target == CANCELLED;
            default -> false;
        };
    }

    public static OpsTicketStatus fromCode(String code) {
        if (code == null) return null;
        for (OpsTicketStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
