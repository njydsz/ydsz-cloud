package com.njydsz.pmis.project.domain.enums;

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

    /** 告警等级编码（大小写不敏感） */
    private final String code;
    /** 告警等级中文描述 */
    private final String desc;

    EvmAlertLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取告警等级编码
     *
     * @return 告警等级编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取告警等级中文描述
     *
     * @return 告警等级中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据 CPI/SPI 与阈值评估告警等级
     *
     * @param cpi       成本绩效指数
     * @param spi       进度绩效指数
     * @param cpiYellow CPI 黄色阈值
     * @param cpiRed    CPI 红色阈值
     * @param spiYellow SPI 黄色阈值
     * @param spiRed    SPI 红色阈值
     * @return 告警等级（任一指标跌破红色阈值返回 RED；任一跌破黄色阈值返回 YELLOW；否则 NORMAL）
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

    /**
     * 根据编码反查枚举
     *
     * @param code 告警等级编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static EvmAlertLevel fromCode(String code) {
        if (code == null) return null;
        for (EvmAlertLevel v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        return null;
    }
}
