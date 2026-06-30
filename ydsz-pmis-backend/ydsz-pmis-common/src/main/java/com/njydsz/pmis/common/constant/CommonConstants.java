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

    /** 用户信息 Header (网关透传) */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_DEPT = "X-User-Dept-Id";

    /** 默认密码 (admin) */
    public static final String DEFAULT_PASSWORD = "admin123";

    /** 逻辑删除 */
    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    /** 业务状态 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FINISHED = "FINISHED";
}
