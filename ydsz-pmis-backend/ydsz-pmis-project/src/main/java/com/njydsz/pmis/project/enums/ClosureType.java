package com.njydsz.pmis.project.enums;

/**
 * 项目结项类型
 *
 * <ul>
 *   <li>FORMAL - 正式结项：所有交付物验收完成、回款结清</li>
 *   <li>PRE_CLOSURE - 预结项：交付物完成、回款未结清</li>
 *   <li>FORCED - 强制结项：异常情况强制结束</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ClosureType {
    FORMAL("FORMAL", "正式结项"),
    PRE_CLOSURE("PRE_CLOSURE", "预结项"),
    FORCED("FORCED", "强制结项");

    /** 类型编码（大小写不敏感） */
    private final String code;
    /** 类型中文描述 */
    private final String desc;

    ClosureType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取类型编码
     *
     * @return 类型编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取类型中文描述
     *
     * @return 类型中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据编码反查枚举
     *
     * @param code 结项类型编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static ClosureType fromCode(String code) {
        if (code == null) return null;
        for (ClosureType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
