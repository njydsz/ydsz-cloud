package com.remisoft.common.core.code;

/**
 * 系统通用结果码。
 */
public enum BaseResultCode {

    SUCCESS("A00000", "ok", 200),

    BAD_REQUEST("A10001", "请求参数错误", 400),
    VALIDATION_FAILED("A10002", "参数校验失败", 400),
    NOT_FOUND("A10101", "资源不存在", 404),
    RATE_LIMIT("A10301", "请求频率超限", 429),

    INTERNAL_ERROR("B10201", "系统内部错误", 500),
    SERVICE_UNAVAILABLE("B10202", "服务暂不可用", 503),

    UNAUTHORIZED("A20001", "未登录", 401),
    TOKEN_EXPIRED("A20002", "Token 已过期", 401),
    FORBIDDEN("A20101", "无权限访问", 403),
    DATA_SCOPE_FORBIDDEN("A20102", "数据权限不足", 403),

    UNKNOWN("C99999", "未知错误", 500);

    private final String code;
    private final String msg;
    private final int httpStatus;

    BaseResultCode(String code, String msg, int httpStatus) {
        this.code = code;
        this.msg = msg;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public int getHttpStatusCode() {
        return httpStatus;
    }

    /**
     * 国际化消息 key（使用枚举名拼接）。
     */
    public String getMessageKey() {
        return "error." + name();
    }
}
