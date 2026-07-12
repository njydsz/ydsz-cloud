package com.njydsz.pmis.common.audit.enums;

/**
 * 审计结果状态枚举
 * <p>
 * 定义审计记录对目标业务方法调用结果的状态分类。用于事后排查与统计（如失败率、
 * 拒绝率等指标）。与 HTTP 状态码解耦，仅描述业务方法的执行结果。
 * </p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public enum AuditStatus {

    /** 业务方法正常返回 */
    SUCCESS(1, "成功"),

    /** 业务方法抛出异常或返回失败标识 */
    FAILURE(0, "失败"),

    /** 批量操作部分成功部分失败（如批量导入 100 条有 3 条失败） */
    PARTIAL(2, "部分成功"),

    /** 权限校验未通过或风控拒绝，未真正执行业务 */
    REJECTED(3, "被拒绝"),

    /** 业务方法执行超时 */
    TIMEOUT(4, "超时"),

    /** 状态未知（如审计落盘失败、上下文缺失等） */
    UNKNOWN(-1, "未知");

    /**
     * 状态编码（数据库持久化值；UNKNOWN 用 -1 以兼容历史脏数据）
     */
    private final int code;

    /**
     * 状态描述（界面展示文案）
     */
    private final String description;

    AuditStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取状态编码
     *
     * @return 编码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取状态描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取审计状态枚举
     *
     * @param code 编码
     * @return 审计状态；未匹配时返回 {@link #UNKNOWN} 兜底
     */
    public static AuditStatus fromCode(int code) {
        for (AuditStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * 判断是否为成功状态（成功或部分成功）
     *
     * @return 是成功状态返回 true
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == PARTIAL;
    }
}
