package com.njydsz.common.core.code;


import com.njydsz.common.core.response.BaseResponse;

/**
 * 标准结果码枚举
 *
 * <p>提供系统级通用结果码，参考阿里巴巴《Java开发手册》错误码规范：
 * <ul>
 *   <li>A 类：用户端错误（参数校验、权限、认证等）</li>
 *   <li>B 类：当前系统业务异常</li>
 *   <li>C 类：第三方服务异常（数据库、缓存、MQ 等）</li>
 * </ul>
 *
 * <p>错误码段位规划：
 * <pre>
 *   A00000          — 成功
 *   A1xxxx (01-06)  — 通用错误（参数校验、资源不存在、限流等）
 *   A2xxxx (01)     — 认证授权
 *   B1xxxx          — 系统级业务异常（内部错误、服务不可用等）
 *   B2xxxx          — 系统状态（维护、熔断等）
 *   C1xxxx (04-07)  — 第三方服务异常（DB、缓存、MQ 等）
 *   C9xxxx          — 系统/未知（兜底错误码）
 * </pre>
 *
 * <p>业务模块专用错误码（如 B3xxxx 用户/B4xxxx 项目/B7xxxx 工作流）已下沉至各模块
 * 的 {@code XXResultCode} 枚举中统一管理。
 * 业务模块自定义错误码请实现 {@link ResultCode} 接口，在各模块内自行定义。
 *
 * <p><b>快速检索：</b></p>
 * <ul>
 *   <li>{@link #fromCode(String)} — 按 code 字符串查找</li>
 *   <li>{@link #successCodes()} — 仅成功码</li>
 *   <li>{@link #authCodes()} — 认证/授权相关</li>
 *   <li>{@link #dbCodes()} — 数据库相关</li>
 *   <li>{@link #integrationCodes()} — 第三方集成相关</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ResultCode
 * @see BaseResponse#error(ResultCode)
 */
public enum BaseResultCode implements ResultCode {

    // ============================== 成功 ==============================
    SUCCESS("A00000", "ok", 200),

    // ============================== 通用错误 (A1xxxx) ==============================
    /** 请求参数错误 */
    BAD_REQUEST("A10001", "请求参数错误", 400),
    /** 参数校验失败（JSR-303 校验不通过） */
    VALIDATION_FAILED("A10002", "参数校验失败", 400),
    /** 缺少必填参数 */
    MISSING_PARAMETER("A10003", "缺少参数", 400),
    /** HTTP 方法不允许 */
    METHOD_NOT_ALLOWED("A10004", "请求方法不允许", 405),
    /** 不支持的媒体类型 */
    UNSUPPORTED_MEDIA_TYPE("A10005", "不支持的媒体类型", 400),

    /** 资源不存在 */
    NOT_FOUND("A10101", "资源不存在", 404),
    /** 资源已存在（重复创建） */
    DUPLICATE_KEY("A10102", "资源已存在", 409),
    /** 业务规则校验失败 */
    BIZ_ERROR("A10103", "业务规则校验失败", 400),

    /** 系统内部错误 */
    INTERNAL_ERROR("B10201", "系统内部错误", 500),
    /** 服务暂不可用 */
    SERVICE_UNAVAILABLE("B10202", "服务暂不可用", 503),
    /** 请求超时 */
    REQUEST_TIMEOUT("A10203", "请求超时", 408),

    // ============================== 限流相关 (A103xx) ==============================
    /** 请求频率超限 */
    RATE_LIMIT("A10301", "请求频率超限", 429),
    /** 租户配额超限 */
    QUOTA_EXCEEDED("A10302", "租户配额超限", 429),

    // ============================== 数据库相关 (C104xx) ==============================
    /** 数据唯一性冲突 */
    DB_DUPLICATE_KEY("C10401", "数据唯一性冲突", 409),
    /** 数据约束冲突（外键等） */
    DB_CONSTRAINT_VIOLATION("C10402", "数据约束冲突", 400),
    /** 数据完整性错误 */
    DB_DATA_INTEGRITY("C10403", "数据完整性错误", 400),
    /** 数据库查询超时 */
    DB_QUERY_TIMEOUT("C10404", "数据库查询超时", 503),
    /** 数据库连接失败 */
    DB_CONNECTION_FAILED("C10405", "数据库连接失败", 503),
    /** 数据库锁冲突 */
    DB_LOCK_CONTENTION("C10406", "数据库锁冲突", 409),

    // ============================== 资源冲突 (A105xx) ==============================
    /** 资源锁冲突（如乐观锁） */
    RESOURCE_LOCKED("A10501", "资源锁冲突", 409),
    /** 资源冲突 */
    RESOURCE_CONFLICT("A10502", "资源冲突", 409),

    // ============================== 请求语义 (A106xx) ==============================
    /** 请求范围无效 */
    INVALID_RANGE("A10601", "请求范围无效", 400),
    /** 请求体过大 */
    PAYLOAD_TOO_LARGE("A10602", "请求体过大", 400),
    /** 请求过多 */
    TOO_MANY_REQUESTS("A10603", "请求过多", 429),

    // ============================== 系统状态 (B2xxxx) ==============================
    /** 系统维护中 */
    SYSTEM_MAINTENANCE("B20001", "系统维护中", 503),
    /** 功能已禁用 */
    FEATURE_DISABLED("B20002", "功能已禁用", 409),
    /** 熔断器已开启 */
    CIRCUIT_BREAKER_OPEN("B20003", "熔断器已开启，请稍后重试", 500),

    // ============================== 第三方服务 (C105xx~C107xx) ==============================
    /** 第三方服务异常 */
    THIRD_PARTY_SERVICE_ERROR("C10501", "第三方服务异常", 500),
    /** 第三方服务调用超时 */
    THIRD_PARTY_TIMEOUT("C10502", "第三方服务调用超时", 503),
    /** 第三方服务限流 */
    THIRD_PARTY_RATE_LIMITED("C10503", "第三方服务限流", 503),
    /** 缓存操作失败 */
    CACHE_OPERATION_FAILED("C10601", "缓存操作失败", 500),
    /** 消息发送失败 */
    MQ_PUBLISH_FAILED("C10701", "消息发送失败", 500),
    /** 消息消费失败 */
    MQ_CONSUME_FAILED("C10702", "消息消费失败", 500),

    // ============================== 认证授权 (A2xxxx) ==============================
    /** 未登录 */
    UNAUTHORIZED("A20001", "未登录", 401),
    /** Token 已过期 */
    TOKEN_EXPIRED("A20002", "Token 已过期", 401),
    /** Token 无效 */
    TOKEN_INVALID("A20003", "Token 无效", 401),
    /** 无权限访问 */
    FORBIDDEN("A20101", "无权限访问", 403),
    /** 数据权限不足 */
    DATA_SCOPE_FORBIDDEN("A20102", "数据权限不足", 403),
    /** 密码强度不足 */
    PASSWORD_WEAK("A20103", "密码强度不足", 400),
    /** 密码已过期 */
    PASSWORD_EXPIRED("A20104", "密码已过期，请修改", 401),
    /** 不能使用最近使用过的密码 */
    PASSWORD_REUSED("A20105", "不能使用最近使用过的密码", 400),
    /** 需要双因素认证 */
    MFA_REQUIRED("A20108", "需要双因素认证", 401),
    /** 双因素认证码无效 */
    MFA_INVALID("A20109", "双因素认证码无效", 401),
    /** 账号已锁定 */
    ACCOUNT_LOCKED("A20110", "账号已锁定", 423),
    /** 账号已在其他设备登录 */
    SESSION_KICKED("A20111", "账号已在其他设备登录", 401),

    // ============================== 未知错误 (C9xxxx) ==============================
    /** 未知错误（兜底） */
    UNKNOWN("C99999", "未知错误", 500);

    private final String code;
    private final String msg;
    private final int httpStatus;

    BaseResultCode(String code, String msg, int httpStatus) {
        this.code = code;
        this.msg = msg;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    /**
     * 返回结果码对应的 HTTP 状态码。
     *
     * <p>每个枚举显式声明其 HTTP 语义（数据驱动，无 switch），
     * 遵循 REST 语义：
     * <ul>
     *   <li>参数/校验类错误 -> 400 BAD_REQUEST</li>
     *   <li>认证类错误 -> 401 UNAUTHORIZED</li>
     *   <li>授权类错误 -> 403 FORBIDDEN</li>
     *   <li>资源不存在 -> 404 NOT_FOUND</li>
     *   <li>资源冲突 -> 409 CONFLICT</li>
     *   <li>账号锁定 -> 423 LOCKED</li>
     *   <li>限流 -> 429 TOO_MANY_REQUESTS</li>
     *   <li>服务端错误 -> 500 INTERNAL_SERVER_ERROR</li>
     *   <li>服务不可用 -> 503 SERVICE_UNAVAILABLE</li>
     * </ul>
     *
     * @return 对应的 HTTP 状态码
     */
    @Override
    public int getHttpStatusCode() {
        return httpStatus;
    }

    // ======================== 静态查询 API ========================

    private static final java.util.Map<String, BaseResultCode> CODE_MAP =
            java.util.Collections.unmodifiableMap(
                    java.util.Arrays.stream(values())
                            .collect(java.util.stream.Collectors.toMap(
                                    BaseResultCode::getCode,
                                    java.util.function.Function.identity(),
                                    (a, b) -> a,
                                    java.util.LinkedHashMap::new)));

    /**
     * 根据 code 字符串查找对应的结果码。
     *
     * @param code 结果码字符串（如 "A00000"、"A10101"）
     * @return 对应的结果码；未找到时返回 {@link #UNKNOWN}
     */
    public static BaseResultCode fromCode(String code) {
        return CODE_MAP.getOrDefault(code, UNKNOWN);
    }
}
