package com.njydsz.common.core.code;

/**
 * 标准结果码枚举（协议级错误码标识）。
 *
 * <p>作为 {@link ResultCode} 的唯一直接实现，提供全局通用的错误码标识常量。
 * 每个常量是两元组 {@code (code, msg)}：
 * <ul>
 *   <li>code — 前端/客户端识别错误的字符串标识</li>
 *   <li>msg — 默认兜底消息（当 i18n 未配置时直接作为响应 message 返回）</li>
 * </ul>
 *
 * <p>HTTP 状态码等异常下沉语义由 {@code ExceptionCode} 定义。
 * 业务模块自定义错误码请实现 {@link ResultCode} 接口（通常通过 {@code ExceptionCode}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ResultCode
 * @see com.njydsz.common.core.response.BaseResponse#error(ResultCode)
 */
public enum BaseResultCode implements ResultCode {

    // ============================== 成功 ==============================
    SUCCESS("A00000", "ok"),

    // ============================== 客户端错误 (4xx) ==============================
    /** 请求参数错误 */
    BAD_REQUEST("A10001", "请求参数错误"),
    /** 参数校验失败（JSR-303 校验不通过） */
    VALIDATION_FAILED("A10002", "参数校验失败"),
    /** 缺少必填参数 */
    MISSING_PARAMETER("A10003", "缺少参数"),
    /** HTTP 方法不允许 */
    METHOD_NOT_ALLOWED("A10004", "请求方法不允许"),
    /** 不支持的媒体类型 */
    UNSUPPORTED_MEDIA_TYPE("A10005", "不支持的媒体类型"),
    /** 资源不存在 */
    NOT_FOUND("A10101", "资源不存在"),
    /** 资源已存在（重复创建） */
    DUPLICATE_KEY("A10102", "资源已存在"),
    /** 业务规则校验失败 */
    BIZ_ERROR("A10103", "业务规则校验失败"),
    /** 请求超时 */
    REQUEST_TIMEOUT("A10203", "请求超时"),
    /** 未登录或 Token 无效 */
    UNAUTHORIZED("A20001", "未登录"),
    /** 无权限访问 */
    FORBIDDEN("A20101", "无权限访问"),
    /** 请求过多（限流） */
    TOO_MANY_REQUESTS("A10603", "请求过多"),

    // ============================== 服务端错误 (5xx) ==============================
    /** 系统内部错误 */
    INTERNAL_ERROR("B10201", "系统内部错误"),
    /** 服务暂不可用 */
    SERVICE_UNAVAILABLE("B10202", "服务暂不可用"),

    // ============================== 未知兜底 ==============================
    /** 未知错误（兜底） */
    UNKNOWN("C99999", "未知错误");

    private final String code;
    private final String msg;

    BaseResultCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
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
