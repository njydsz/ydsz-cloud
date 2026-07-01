package com.njydsz.pmis.common.constant;

/**
 * 公共常量
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 系统默认字符集 */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /** 链路追踪 ID Header */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 链路追踪 ID MDC Key */
    public static final String MDC_TRACE_ID = "traceId";

    /** 用户 ID Header (网关透传) */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 用户名 Header (网关透传) */
    public static final String HEADER_USERNAME = "X-Username";

    /** 用户所属部门 ID Header (网关透传) */
    public static final String HEADER_USER_DEPT = "X-User-Dept-Id";

    /** 默认密码 (admin) */
    public static final String DEFAULT_PASSWORD = "admin123";

    /** 逻辑删除：未删除 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删除 */
    public static final int DELETED = 1;

    /** 业务状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 业务状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 业务状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 业务状态：生效 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 业务状态：已结束 */
    public static final String STATUS_FINISHED = "FINISHED";
}
