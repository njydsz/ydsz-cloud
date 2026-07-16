package com.njydsz.pmis.project.domain.enums;

/**
 * 收入确认方法
 *
 * <ul>
 *   <li>MILESTONE - 里程碑法</li>
 *   <li>PERCENTAGE - 完工百分比法</li>
 *   <li>PERCENT_COMPLETE - 进度比例法</li>
 *   <li>POINTS - 人天点数法</li>
 *   <li>MANUAL - 手工确认</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum RevenueRecognitionMethod {
    MILESTONE("MILESTONE", "里程碑法"),
    PERCENTAGE("PERCENTAGE", "完工百分比法"),
    PERCENT_COMPLETE("PERCENT_COMPLETE", "进度比例法"),
    POINTS("POINTS", "人天点数法"),
    MANUAL("MANUAL", "手工确认");

    /** 方法编码（大小写不敏感） */
    private final String code;
    /** 方法中文描述 */
    private final String desc;

    RevenueRecognitionMethod(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取方法编码
     *
     * @return 方法编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取方法中文描述
     *
     * @return 方法中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据编码反查枚举
     *
     * @param code 方法编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static RevenueRecognitionMethod fromCode(String code) {
        if (code == null) return null;
        for (RevenueRecognitionMethod r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }
}
