package com.njydsz.userinfo.domain.enums.resource;

/**
 * 资源池类型
 *
 * <ul>
 *   <li>HQ - 总部池（L13+ 高级资源）</li>
 *   <li>DIVISION - 事业部池（L4-L12 主力资源）</li>
 *   <li>RESERVE - 备用池（L1-L3 储备/培训资源）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum PoolType {
    HQ("HQ", "总部池", 1),
    DIVISION("DIVISION", "事业部池", 2),
    RESERVE("RESERVE", "备用池", 3);

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;
    /** 优先级（数字越小优先级越高） */
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
     *
     * <p>L13+ 归 HQ 池，L4-L12 归 DIVISION 池，其余归 RESERVE 池。
     *
     * @param levelCode 职级编码（如 L1、L15）
     * @return 推算出的资源池类型；解析失败时返回 RESERVE
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

    /**
     * 根据编码解析枚举
     *
     * @param code 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；code 为 null 或无匹配时返回 null
     */
    public static PoolType fromCode(String code) {
        if (code == null) return null;
        for (PoolType p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        return null;
    }
}
