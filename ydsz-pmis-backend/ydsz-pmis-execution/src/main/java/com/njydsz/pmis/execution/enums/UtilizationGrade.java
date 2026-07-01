package com.njydsz.pmis.execution.enums;

/**
 * 可计费利用率考核等级
 *
 * <p>基于行业惯例（咨询/软件服务公司 70% 合格线，85% 优秀线）。
 *
 * <ul>
 *   <li>EXCELLENT 优秀：≥ 85%</li>
 *   <li>GOOD      良好：70% ~ 85%</li>
 *   <li>NORMAL    合格：50% ~ 70%</li>
 *   <li>WARN      预警：30% ~ 50%（黄色预警）</li>
 *   <li>CRITICAL  严重：&lt; 30%（红色预警）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum UtilizationGrade {

    EXCELLENT("EXCELLENT", "优秀", 85, 100),
    GOOD("GOOD", "良好", 70, 85),
    NORMAL("NORMAL", "合格", 50, 70),
    WARN("WARN", "预警", 30, 50),
    CRITICAL("CRITICAL", "严重", 0, 30);

    private final String code;
    private final String desc;
    @SuppressWarnings("unused")
    /** 阈值下界（包含） */
    private final int lowerBound;
    @SuppressWarnings("unused")
    /** 阈值上界（不包含） */
    private final int upperBound;

    UtilizationGrade(String code, String desc, int lowerBound, int upperBound) {
        this.code = code;
        this.desc = desc;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据百分比 (0-100) 返回对应考核等级
     */
    public static UtilizationGrade of(double utilizationPct) {
        if (Double.isNaN(utilizationPct) || utilizationPct < 0) {
            return CRITICAL;
        }
        if (utilizationPct >= 85) return EXCELLENT;
        if (utilizationPct >= 70) return GOOD;
        if (utilizationPct >= 50) return NORMAL;
        if (utilizationPct >= 30) return WARN;
        return CRITICAL;
    }

    public boolean isAlert() {
        return this == WARN || this == CRITICAL;
    }

    public static UtilizationGrade fromCode(String code) {
        if (code == null) return null;
        for (UtilizationGrade g : values()) {
            if (g.code.equalsIgnoreCase(code)) return g;
        }
        return null;
    }
}
