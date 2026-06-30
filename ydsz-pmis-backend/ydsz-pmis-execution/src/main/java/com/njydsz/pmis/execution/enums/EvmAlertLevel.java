package com.njydsz.pmis.execution.enums;

/**
 * EVM 挣值告警等级
 *
 * <ul>
 *   <li>NORMAL - 健康（CPI≥0.95 且 SPI≥0.95）</li>
 *   <li>YELLOW - 预警（CPI/SPI 任意一项 <0.95）</li>
 *   <li>RED - 严重（CPI/SPI 任意一项 <0.85 或成本偏差>10%）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum EvmAlertLevel {
    NORMAL("NORMAL", "健康"),
    YELLOW("YELLOW", "预警"),
    RED("RED", "严重");

    private final String code;
    private final String desc;

    EvmAlertLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 根据 CPI/SPI 与阈值评估告警等级
     */
    public static EvmAlertLevel evaluate(double cpi, double spi,
                                         double cpiYellow, double cpiRed,
                                         double spiYellow, double spiRed) {
        // 任一指标跌破红色阈值
        if (cpi < cpiRed || spi < spiRed) return RED;
        // 任一指标跌破黄色阈值
        if (cpi < cpiYellow || spi < spiYellow) return YELLOW;
        return NORMAL;
    }

    public static EvmAlertLevel fromCode(String code) {
        if (code == null) return null;
        for (EvmAlertLevel v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        return null;
    }
}
