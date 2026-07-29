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
 * <ul>
 *   <li>A00000 - 成功</li>
 *   <li>A1xxxx - 通用错误（参数校验、资源不存在、限流等）</li>
 *   <li>B1xxxx - 系统级业务异常（内部错误、服务不可用等）</li>
 *   <li>A2xxxx - 认证授权</li>
 *   <li>B3xxxx - 用户/组织/人员</li>
 *   <li>B7xxxx - 工作流/审批</li>
 *   <li>C9xxxx - 系统/未知（第三方服务异常）
 * </ul>
 * <p>业务模块自定义错误码请实现 {@link ResultCode} 接口，在各模块内自行定义。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ResultCode
 * @see BaseResponse#error(ResultCode)
 */
public enum BaseResultCode implements ResultCode {

    // ==================== 成功 ====================
    SUCCESS("A00000", "ok"),

    // ==================== A1xxxx 通用 / B1xxxx 系统级 ====================
    BAD_REQUEST("A10001", "请求参数错误"),
    VALIDATION_FAILED("A10002", "参数校验失败"),
    MISSING_PARAMETER("A10003", "缺少参数"),
    METHOD_NOT_ALLOWED("A10004", "请求方法不允许"),
    UNSUPPORTED_MEDIA_TYPE("A10005", "不支持的媒体类型"),
    NOT_FOUND("A10101", "资源不存在"),
    DUPLICATE_KEY("A10102", "资源已存在"),
    BIZ_ERROR("A10103", "业务规则校验失败"),
    INTERNAL_ERROR("B10201", "系统内部错误"),
    SERVICE_UNAVAILABLE("B10202", "服务暂不可用"),
    REQUEST_TIMEOUT("A10203", "请求超时"),
    RATE_LIMIT("A10301", "请求频率超限"),
    QUOTA_EXCEEDED("A10302", "租户配额超限"),
    DB_DUPLICATE_KEY("C10401", "数据唯一性冲突"),
    DB_CONSTRAINT_VIOLATION("C10402", "数据约束冲突"),
    DB_DATA_INTEGRITY("C10403", "数据完整性错误"),
    DB_QUERY_TIMEOUT("C10404", "数据库查询超时"),
    DB_CONNECTION_FAILED("C10405", "数据库连接失败"),
    DB_LOCK_CONTENTION("C10406", "数据库锁冲突"),
    RESOURCE_LOCKED("A10501", "资源锁冲突"),

    // ==================== A106xx 请求语义 ====================
    INVALID_RANGE("A10601", "请求范围无效"),
    PAYLOAD_TOO_LARGE("A10602", "请求体过大"),
    TOO_MANY_REQUESTS("A10603", "请求过多"),

    // ==================== B2xxxx 系统状态 ====================
    SYSTEM_MAINTENANCE("B20001", "系统维护中"),
    FEATURE_DISABLED("B20002", "功能已禁用"),
    CIRCUIT_BREAKER_OPEN("B20003", "熔断器已开启，请稍后重试"),

    // ==================== C1xxxx 第三方服务 ====================
    THIRD_PARTY_SERVICE_ERROR("C10501", "第三方服务异常"),
    THIRD_PARTY_TIMEOUT("C10502", "第三方服务调用超时"),
    THIRD_PARTY_RATE_LIMITED("C10503", "第三方服务限流"),
    CACHE_OPERATION_FAILED("C10601", "缓存操作失败"),
    MQ_PUBLISH_FAILED("C10701", "消息发送失败"),
    MQ_CONSUME_FAILED("C10702", "消息消费失败"),

    // ==================== A2xxxx 认证授权 ====================
    UNAUTHORIZED("A20001", "未登录"),
    TOKEN_EXPIRED("A20002", "Token 已过期"),
    TOKEN_INVALID("A20003", "Token 无效"),
    FORBIDDEN("A20101", "无权限访问"),
    DATA_SCOPE_FORBIDDEN("A20102", "数据权限不足"),
    PASSWORD_WEAK("A20103", "密码强度不足"),
    PASSWORD_EXPIRED("A20104", "密码已过期，请修改"),
    PASSWORD_REUSED("A20105", "不能使用最近使用过的密码"),
    MFA_REQUIRED("A20108", "需要双因素认证"),
    MFA_INVALID("A20109", "双因素认证码无效"),
    ACCOUNT_LOCKED("A20110", "账号已锁定"),
    SESSION_KICKED("A20111", "账号已在其他设备登录"),

    // ==================== B3xxxx 用户/组织/人员 ====================
    USER_NOT_FOUND("B30001", "用户不存在"),
    PASSWORD_INCORRECT("B30002", "密码错误"),
    USER_DISABLED("B30003", "用户已停用"),
    USER_LOCKED("B30004", "用户已被锁定"),
    USERNAME_DUPLICATE("B30005", "用户名已存在"),
    DEPARTMENT_NOT_FOUND("B30101", "部门不存在"),
    EMPLOYEE_NOT_FOUND("B30201", "员工不存在"),

    // ==================== B7xxxx 工作流/审批 ====================
    WORKFLOW_NOT_FOUND("B70001", "流程不存在"),
    WORKFLOW_REJECT("B70002", "流程被驳回"),
    WORKFLOW_NO_PERMISSION("B70003", "无审批权限"),

    // ==================== C9xxxx 系统/未知 ====================
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

    /**
     * 将结果码映射到合适的 HTTP 状态码
     *
     * <p>遵循 REST 语义：
     * <ul>
     *   <li>参数/校验类错误 -> 400 BAD_REQUEST</li>
     *   <li>认证类错误 -> 401 UNAUTHORIZED</li>
     *   <li>授权类错误 -> 403 FORBIDDEN</li>
     *   <li>资源不存在 -> 404 NOT_FOUND</li>
     *   <li>资源冲突 -> 409 CONFLICT</li>
     *   <li>资源锁定 -> 423 LOCKED</li>
     *   <li>限流 -> 429 TOO_MANY_REQUESTS</li>
     *   <li>服务端错误 -> 500 INTERNAL_SERVER_ERROR</li>
     *   <li>服务不可用 -> 503 SERVICE_UNAVAILABLE</li>
     * </ul>
     *
     * @return 对应的 HTTP 状态码
     */
    public int getHttpStatusCode() {
        return switch (this) {
            // 1xxxx 通用
            case BAD_REQUEST, VALIDATION_FAILED, MISSING_PARAMETER, UNSUPPORTED_MEDIA_TYPE,
                 BIZ_ERROR, WORKFLOW_REJECT,
                 PASSWORD_WEAK, PASSWORD_REUSED,
                 DB_CONSTRAINT_VIOLATION, DB_DATA_INTEGRITY,
                 INVALID_RANGE, PAYLOAD_TOO_LARGE -> 400;
            case METHOD_NOT_ALLOWED -> 405;
            case NOT_FOUND, USER_NOT_FOUND, DEPARTMENT_NOT_FOUND, EMPLOYEE_NOT_FOUND,
                 WORKFLOW_NOT_FOUND -> 404;
            case DUPLICATE_KEY, USERNAME_DUPLICATE,
                 DB_DUPLICATE_KEY, DB_LOCK_CONTENTION, RESOURCE_LOCKED,
                 FEATURE_DISABLED -> 409;
            case RATE_LIMIT, QUOTA_EXCEEDED, TOO_MANY_REQUESTS -> 429;
            case REQUEST_TIMEOUT -> 408;
            case INTERNAL_ERROR, UNKNOWN,
                 THIRD_PARTY_SERVICE_ERROR, CACHE_OPERATION_FAILED,
                 MQ_PUBLISH_FAILED, MQ_CONSUME_FAILED,
                 CIRCUIT_BREAKER_OPEN -> 500;
            case SERVICE_UNAVAILABLE, DB_QUERY_TIMEOUT, DB_CONNECTION_FAILED,
                 THIRD_PARTY_TIMEOUT, THIRD_PARTY_RATE_LIMITED,
                 SYSTEM_MAINTENANCE -> 503;
            // 2xxxx 认证授权
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID,
                 PASSWORD_EXPIRED,
                 MFA_REQUIRED, MFA_INVALID, SESSION_KICKED,
                 PASSWORD_INCORRECT -> 401;
            case FORBIDDEN, DATA_SCOPE_FORBIDDEN, USER_DISABLED,
                 WORKFLOW_NO_PERMISSION -> 403;
            case ACCOUNT_LOCKED, USER_LOCKED -> 423;
            case SUCCESS -> 200;
        };
    }
}
