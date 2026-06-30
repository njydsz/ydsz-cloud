package com.njydsz.pmis.user.enums;

/**
 * 资源池类型
 *
 * <ul>
 *   <li>HQ - 总部池（L13+ 高级资源）</li>
 *   <li>DIVISION - 事业部池（L4-L12 主力资源）</li>
 *   <li>RESERVE - 备用池（L1-L3 储备/培训资源）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum PoolType {
    HQ("HQ", "总部池", 1),
    DIVISION("DIVISION", "事业部池", 2),
    RESERVE("RESERVE", "备用池", 3);

    private final String code;
    private final String desc;
    private final int priority;

    PoolType(String code, String desc, int priority) {
        this.code = code;
        this.desc = desc;
        this.priority = priority;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
    public int getPriority() { return priority; }

    /**
     * 按职级推算默认资源池
     */
    public static PoolType inferByLevel(String levelCode) {
        if (levelCode == null || levelCode.length() < 2) return RESERVE;
        try {
            int lv = Integer.parseInt(levelCode.substring(1));
            if (lv >= 13) return HQ;
            if (lv >= 4) return DIVISION;
            return RESERVE;
        } catch (NumberFormatException e) {
            return RESERVE;
        }
    }

    public static PoolType fromCode(String code) {
        if (code == null) return null;
        for (PoolType p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        return null;
    }
}
