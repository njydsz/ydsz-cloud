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
 *   <li>A1xxxx - 通用错误</li>
 *   <li>A2xxxx - 认证授权</li>
 *   <li>B3xxxx - 用户/组织/人员</li>
 *   <li>B4xxxx - 项目/合同/商机</li>
 *   <li>B5xxxx - 财务/成本/收入/利润</li>
 *   <li>B6xxxx - 资源/工时/人员调度</li>
 *   <li>B7xxxx - 工作流/审批</li>
 *   <li>B8xxxx - 报表/驾驶舱</li>
 *   <li>C9xxxx - 系统/未知</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ResultCode
 * @see BaseResponse#error(ResultCode)
 */
public enum BaseResultCode implements ResultCode {

    // ==================== 成功 ====================
    SUCCESS("A00000", "ok"),

    // ==================== A1xxxx 通用 ====================
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

    // ==================== B4xxxx 项目/合同/商机 ====================
    PROJECT_NOT_FOUND("B40001", "项目不存在"),
    PROJECT_STATUS_INVALID("B40002", "项目状态不允许该操作"),
    OPPORTUNITY_NOT_FOUND("B40101", "商机不存在"),
    CONTRACT_NOT_FOUND("B40201", "合同不存在"),
    CONTRACT_AMOUNT_EXCEED("B40202", "合同金额超限"),

    // ==================== B5xxxx 财务/成本/收入/利润 ====================
    COST_OVERFLOW("B50001", "成本超预算"),
    INVOICE_EXCEED("B50002", "开票金额超限"),
    PAYMENT_NOT_FOUND("B50101", "回款记录不存在"),
    PROFIT_NEGATIVE("B50201", "项目利润为负"),

    // ==================== B6xxxx 资源/工时/人员调度 ====================
    RESOURCE_CONFLICT("B60001", "资源冲突"),
    BENCH_OVER_LIMIT("B60002", "Bench 闲置超限"),
    TIMESHEET_DUPLICATE("B60101", "工时重复填报"),
    TIMESHEET_LOCKED("B60102", "工时已锁定"),

    // ==================== B7xxxx 工作流/审批 ====================
    WORKFLOW_NOT_FOUND("B70001", "流程不存在"),
    WORKFLOW_REJECT("B70002", "流程被驳回"),
    WORKFLOW_NO_PERMISSION("B70003", "无审批权限"),

    // ==================== B8xxxx 报表/驾驶舱 ====================
    REPORT_GENERATE_FAILED("B80001", "报表生成失败"),

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
                 BIZ_ERROR, CONTRACT_AMOUNT_EXCEED, COST_OVERFLOW, INVOICE_EXCEED,
                 PROFIT_NEGATIVE, BENCH_OVER_LIMIT, WORKFLOW_REJECT,
                 PASSWORD_WEAK, PASSWORD_REUSED,
                 DB_CONSTRAINT_VIOLATION, DB_DATA_INTEGRITY -> 400;
            case METHOD_NOT_ALLOWED -> 405;
            case NOT_FOUND, USER_NOT_FOUND, DEPARTMENT_NOT_FOUND, EMPLOYEE_NOT_FOUND,
                 PROJECT_NOT_FOUND, OPPORTUNITY_NOT_FOUND, CONTRACT_NOT_FOUND,
                 PAYMENT_NOT_FOUND, WORKFLOW_NOT_FOUND -> 404;
            case DUPLICATE_KEY, USERNAME_DUPLICATE, TIMESHEET_DUPLICATE,
                 RESOURCE_CONFLICT, PROJECT_STATUS_INVALID,
                 DB_DUPLICATE_KEY, DB_LOCK_CONTENTION, RESOURCE_LOCKED -> 409;
            case RATE_LIMIT, QUOTA_EXCEEDED -> 429;
            case REQUEST_TIMEOUT -> 408;
            case INTERNAL_ERROR, UNKNOWN, REPORT_GENERATE_FAILED -> 500;
            case SERVICE_UNAVAILABLE, DB_QUERY_TIMEOUT, DB_CONNECTION_FAILED -> 503;
            // 2xxxx 认证授权
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID,
                 PASSWORD_EXPIRED,
                 MFA_REQUIRED, MFA_INVALID, SESSION_KICKED,
                 PASSWORD_INCORRECT -> 401;
            case FORBIDDEN, DATA_SCOPE_FORBIDDEN, USER_DISABLED,
                 WORKFLOW_NO_PERMISSION -> 403;
            case ACCOUNT_LOCKED, USER_LOCKED, TIMESHEET_LOCKED -> 423;
            case SUCCESS -> 200;
        };
    }
}
