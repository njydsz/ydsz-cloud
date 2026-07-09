package com.njydsz.pmis.project.enums.initiation;

/**
 * 项目变更类型
 *
 * <ul>
 *   <li>SCOPE - 范围变更</li>
 *   <li>COST - 成本预算变更</li>
 *   <li>CONTRACT - 合同变更</li>
 *   <li>STAFF - 人员变更</li>
 *   <li>SCHEDULE - 进度变更</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ChangeType {
    SCOPE("SCOPE", "范围变更"),
    COST("COST", "成本预算变更"),
    CONTRACT("CONTRACT", "合同变更"),
    STAFF("STAFF", "人员变更"),
    SCHEDULE("SCHEDULE", "进度变更");

    private final String code;
    private final String desc;

    ChangeType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static ChangeType fromCode(String code) {
        if (code == null) return null;
        for (ChangeType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
