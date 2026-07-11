package com.njydsz.pmis.sales.enums.aftersales;

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

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    OpsTicketStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取状态编码
     *
     * @return 状态编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取状态中文描述
     *
     * @return 状态中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已关闭/已取消），不可再迁移
     */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
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

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static OpsTicketStatus fromCode(String code) {
        if (code == null) return null;
        for (OpsTicketStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
