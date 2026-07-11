package com.njydsz.pmis.project.domain.enums;

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

    /** 状态编码（大小写不敏感） */
    private final String code;
    /** 状态中文描述 */
    private final String desc;

    RiskStatus(String code, String desc) {
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
     * @return true 表示当前状态为终态（已关闭），不可再迁移
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
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

    /**
     * 根据编码反查枚举
     *
     * @param code 状态编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static RiskStatus fromCode(String code) {
        if (code == null) return null;
        for (RiskStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return null;
    }
}
