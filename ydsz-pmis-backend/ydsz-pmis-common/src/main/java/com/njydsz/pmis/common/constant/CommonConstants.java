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

    /** 用户角色 Header (网关透传) */
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /** 用户权限 Header (网关透传) */
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    /**
     * 内部头 HMAC 签名 Header（P0-C5）。
     *
     * <p>网关在透传 {@code X-User-*} 系列头时，同时注入该签名头，
     * 下游服务通过校验签名拦截外部直接调用（绕过网关）伪造的内部头。
     * 签名算法：HMAC-SHA256(secret, traceId|userId|username|roles|permissions)。
     */
    public static final String HEADER_INTERNAL_SIG = "X-Internal-Sig";

    /** 内部头签名时间戳 Header（P0-C5，用于防重放） */
    public static final String HEADER_INTERNAL_TS = "X-Internal-Ts";

    /** 内部头签名有效期（秒），超过即视为非法 */
    public static final long INTERNAL_SIG_TTL_SECONDS = 60;

    /**
     * 逻辑删除：未删除
     *
     * <p>注：早期版本曾保留 DEFAULT_PASSWORD = "admin123" 常量，但该值已被
     * {@link com.njydsz.pmis.common.security.PasswordPolicy} 列入弱密码黑名单，
     * 二者存在矛盾。该常量在主代码中无任何引用，故移除。
     * 初始 admin 账号密码由部署脚本（deploy/sql/V1.0.0_001__init_pmis_schema.sql）
     * 直接以哈希形式注入，并在首次登录时强制修改。
     */
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
