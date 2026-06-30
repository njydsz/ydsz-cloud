package com.njydsz.pmis.execution.enums;

/**
 * 收入确认方法
 */
public enum RevenueRecognitionMethod {
    MILESTONE("MILESTONE", "里程碑法"),
    PERCENTAGE("PERCENTAGE", "完工百分比法"),
    PERCENT_COMPLETE("PERCENT_COMPLETE", "进度比例法"),
    POINTS("POINTS", "人天点数法"),
    MANUAL("MANUAL", "手工确认");

    private final String code;
    private final String desc;

    RevenueRecognitionMethod(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static RevenueRecognitionMethod fromCode(String code) {
        if (code == null) return null;
        for (RevenueRecognitionMethod r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }
}
