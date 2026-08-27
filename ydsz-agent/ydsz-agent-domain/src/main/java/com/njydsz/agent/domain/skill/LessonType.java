package com.njydsz.agent.domain.skill;

/**
 * 经验类型枚举。
 *
 * <p>定义 Skill 执行过程中记录的经验类型。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public enum LessonType {

    /** 最佳实践：成功执行中总结出的有效方法 */
    BEST_PRACTICE("BEST_PRACTICE", "最佳实践"),

    /** 错误教训：执行失败或异常中总结的教训 */
    ERROR_LESSON("ERROR_LESSON", "错误教训"),

    /** 用户偏好：记录用户的特定偏好和习惯 */
    USER_PREFERENCE("USER_PREFERENCE", "用户偏好"),

    /** 优化建议：性能或效果优化建议 */
    OPTIMIZATION("OPTIMIZATION", "优化建议"),

    /** 注意事项：需要特别关注的点 */
    CAUTION("CAUTION", "注意事项");

    private final String code;
    private final String description;

    LessonType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据状态码查找枚举。
     *
     * @param code 状态码
     * @return 对应枚举，未找到返回 null
     */
    public static LessonType fromCode(String code) {
        for (LessonType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
