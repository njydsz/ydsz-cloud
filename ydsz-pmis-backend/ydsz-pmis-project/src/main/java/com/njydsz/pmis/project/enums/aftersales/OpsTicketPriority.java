package com.njydsz.pmis.project.enums.aftersales;

/**
 * 运维工单优先级与 SLA 时限
 *
 * <ul>
 *   <li>P1 - 紧急：15 分钟首次响应，4 小时解决</li>
 *   <li>P2 - 高：1 小时首次响应，24 小时解决</li>
 *   <li>P3 - 中：4 小时首次响应，72 小时解决</li>
 *   <li>P4 - 低：8 小时首次响应，7 天解决</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum OpsTicketPriority {

    P1("P1", "紧急", 15, 4 * 60),
    P2("P2", "高", 60, 24 * 60),
    P3("P3", "中", 4 * 60, 72 * 60),
    P4("P4", "低", 8 * 60, 7 * 24 * 60);

    /** 优先级编码（大小写不敏感） */
    private final String code;
    /** 优先级中文描述 */
    private final String desc;
    /** 首次响应 SLA（分钟） */
    private final int responseMinutes;
    /** 解决 SLA（分钟） */
    private final int resolveMinutes;

    OpsTicketPriority(String code, String desc, int responseMinutes, int resolveMinutes) {
        this.code = code;
        this.desc = desc;
        this.responseMinutes = responseMinutes;
        this.resolveMinutes = resolveMinutes;
    }

    /**
     * 获取优先级编码
     *
     * @return 优先级编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取优先级中文描述
     *
     * @return 优先级中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 获取首次响应 SLA 时限
     *
     * @return 首次响应 SLA（分钟）
     */
    public int getResponseMinutes() { return responseMinutes; }

    /**
     * 获取解决 SLA 时限
     *
     * @return 解决 SLA（分钟）
     */
    public int getResolveMinutes() { return resolveMinutes; }

    /**
     * 根据编码反查枚举
     *
     * @param code 优先级编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static OpsTicketPriority fromCode(String code) {
        if (code == null) return null;
        for (OpsTicketPriority p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        return null;
    }
}
