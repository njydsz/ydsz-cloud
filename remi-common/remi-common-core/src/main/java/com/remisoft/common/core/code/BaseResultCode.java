package com.remisoft.common.core.code;

/**
 * 标准结果码枚举（协议级错误码）
 *
 * <p>Core 模块仅保留与 HTTP 语义对齐的协议级错误码，遵循"最小核心"原则：
 * <ul>
 *   <li>A0xxxx — 成功</li>
 *   <li>A1xxxx — 客户端参数/语义错误（4xx）</li>
 *   <li>A2xxxx — 认证授权错误（401/403）</li>
 *   <li>B1xxxx — 服务端的系统级错误（5xx）</li>
 *   <li>C9xxxx — 未知兜底</li>
 * </ul>
 *
 * <p>以下类别的错误码已下沉至对应业务模块，不再在 core 中定义：
 * <ul>
 *   <li>数据库错误（DB_*）→ remi-common-jdbc / 业务模块</li>
 *   <li>缓存错误（CACHE_*）→ remi-common-redis / remi-common-cache</li>
 *   <li>消息队列错误（MQ_*）→ remi-common-queue</li>
 *   <li>细粒度认证错误（TOKEN_EXPIRED / MFA_* / PASSWORD_* 等）→ remi-common-auth</li>
 *   <li>第三方服务错误（THIRD_PARTY_* / CIRCUIT_BREAKER_*）→ 各业务模块</li>
 * </ul>
 *
 * <p>业务模块自定义错误码请实现 {@link ResultCode} 接口，在各模块内自行定义。
 *
 * @author remi-team
 * @since 1.0.0
 * @see ResultCode
 * @see com.remisoft.common.core.response.BaseResponse#error(ResultCode)
 */
public enum BaseResultCode implements ResultCode {

    // ============================== 成功 ==============================
    SUCCESS("A00000", "ok", 200),

    // ============================== 客户端错误 (4xx) ==============================
    /** 请求参数错误 */
    BAD_REQUEST("A10001", "请求参数错误", 400),
    /** 参数校验失败（JSR-303 校验不通过） */
    VALIDATION_FAILED("A10002", "参数校验失败", 400),
    /** 缺少必填参数 */
    MISSING_PARAMETER("A10003", "缺少参数", 400),
    /** HTTP 方法不允许 */
    METHOD_NOT_ALLOWED("A10004", "请求方法不允许", 405),
    /** 不支持的媒体类型 */
    UNSUPPORTED_MEDIA_TYPE("A10005", "不支持的媒体类型", 415),
    /** 资源不存在 */
    NOT_FOUND("A10101", "资源不存在", 404),
    /** 资源已存在（重复创建） */
    DUPLICATE_KEY("A10102", "资源已存在", 409),
    /** 业务规则校验失败 */
    BIZ_ERROR("A10103", "业务规则校验失败", 400),
    /** 请求超时 */
    REQUEST_TIMEOUT("A10203", "请求超时", 408),
    /** 未登录或 Token 无效 */
    UNAUTHORIZED("A20001", "未登录", 401),
    /** 无权限访问 */
    FORBIDDEN("A20101", "无权限访问", 403),
    /** 请求过多（限流） */
    TOO_MANY_REQUESTS("A10603", "请求过多", 429),

    // ============================== 服务端错误 (5xx) ==============================
    /** 系统内部错误 */
    INTERNAL_ERROR("B10201", "系统内部错误", 500),
    /** 服务暂不可用 */
    SERVICE_UNAVAILABLE("B10202", "服务暂不可用", 503),

    // ============================== 未知兜底 ==============================
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
     * 遵循 REST 语义。
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
