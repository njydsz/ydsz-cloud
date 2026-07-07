package com.njydsz.pmis.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码
 *
 * <p>错误码段位规划：
 * <ul>
 *   <li>0 - 成功</li>
 *   <li>1xxxx - 通用错误</li>
 *   <li>2xxxx - 认证授权</li>
 *   <li>3xxxx - 用户/组织/人员</li>
 *   <li>4xxxx - 项目/合同/商机</li>
 *   <li>5xxxx - 财务/成本/收入/利润</li>
 *   <li>6xxxx - 资源/工时/人员调度</li>
 *   <li>7xxxx - 工作流/审批</li>
 *   <li>8xxxx - 报表/驾驶舱</li>
 *   <li>9xxxx - 系统/未知</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public enum BizErrorCode {

    OK(0, "ok"),

    // ========== 1xxxx 通用 ==========
    BAD_REQUEST(10001, "请求参数错误"),
    VALIDATION_FAILED(10002, "参数校验失败"),
    MISSING_PARAMETER(10003, "缺少参数"),
    METHOD_NOT_ALLOWED(10004, "请求方法不允许"),
    UNSUPPORTED_MEDIA_TYPE(10005, "不支持的媒体类型"),
    NOT_FOUND(10101, "资源不存在"),
    DUPLICATE_KEY(10102, "资源已存在"),
    BIZ_ERROR(10103, "业务规则校验失败"),
    INTERNAL_ERROR(10201, "系统内部错误"),
    SERVICE_UNAVAILABLE(10202, "服务暂不可用"),
    REQUEST_TIMEOUT(10203, "请求超时"),
    RATE_LIMIT(10301, "请求频率超限"),
    // H9.2 修复：数据库相关错误码（与 GlobalExceptionHandler H9.1 配合使用）
    DB_DUPLICATE_KEY(10401, "数据唯一性冲突"),
    DB_CONSTRAINT_VIOLATION(10402, "数据约束冲突"),
    DB_DATA_INTEGRITY(10403, "数据完整性错误"),
    DB_QUERY_TIMEOUT(10404, "数据库查询超时"),
    DB_CONNECTION_FAILED(10405, "数据库连接失败"),
    DB_LOCK_CONTENTION(10406, "数据库锁冲突"),
    // 分布式锁冲突（P2-3：@DistributedLock 获取锁失败）
    RESOURCE_LOCKED(10501, "资源锁冲突"),

    // ========== 2xxxx 认证授权 ==========
    UNAUTHORIZED(20001, "未登录"),
    TOKEN_EXPIRED(20002, "Token 已过期"),
    TOKEN_INVALID(20003, "Token 无效"),
    FORBIDDEN(20101, "无权限访问"),
    DATA_SCOPE_FORBIDDEN(20102, "数据权限不足"),
    PASSWORD_WEAK(20103, "密码强度不足"),
    PASSWORD_EXPIRED(20104, "密码已过期，请修改"),
    PASSWORD_REUSED(20105, "不能使用最近使用过的密码"),
    REAUTH_REQUIRED(20106, "该操作需要二次认证"),
    REAUTH_INVALID(20107, "二次认证 token 无效或已过期"),
    MFA_REQUIRED(20108, "需要双因素认证"),
    MFA_INVALID(20109, "双因素认证码无效"),
    ACCOUNT_LOCKED(20110, "账号已锁定"),
    SESSION_KICKED(20111, "账号已在其他设备登录"),

    // ========== 3xxxx 用户/组织/人员 ==========
    USER_NOT_FOUND(30001, "用户不存在"),
    PASSWORD_INCORRECT(30002, "密码错误"),
    USER_DISABLED(30003, "用户已停用"),
    USER_LOCKED(30004, "用户已被锁定"),
    USERNAME_DUPLICATE(30005, "用户名已存在"),
    DEPARTMENT_NOT_FOUND(30101, "部门不存在"),
    EMPLOYEE_NOT_FOUND(30201, "员工不存在"),

    // ========== 4xxxx 项目/合同/商机 ==========
    PROJECT_NOT_FOUND(40001, "项目不存在"),
    PROJECT_STATUS_INVALID(40002, "项目状态不允许该操作"),
    OPPORTUNITY_NOT_FOUND(40101, "商机不存在"),
    CONTRACT_NOT_FOUND(40201, "合同不存在"),
    CONTRACT_AMOUNT_EXCEED(40202, "合同金额超限"),

    // ========== 5xxxx 财务/成本/收入/利润 ==========
    COST_OVERFLOW(50001, "成本超预算"),
    INVOICE_EXCEED(50002, "开票金额超限"),
    PAYMENT_NOT_FOUND(50101, "回款记录不存在"),
    PROFIT_NEGATIVE(50201, "项目利润为负"),

    // ========== 6xxxx 资源/工时/人员调度 ==========
    RESOURCE_CONFLICT(60001, "资源冲突"),
    BENCH_OVER_LIMIT(60002, "Bench 闲置超限"),
    TIMESHEET_DUPLICATE(60101, "工时重复填报"),
    TIMESHEET_LOCKED(60102, "工时已锁定"),

    // ========== 7xxxx 工作流/审批 ==========
    WORKFLOW_NOT_FOUND(70001, "流程不存在"),
    WORKFLOW_REJECT(70002, "流程被驳回"),
    WORKFLOW_NO_PERMISSION(70003, "无审批权限"),

    // ========== 8xxxx 报表/驾驶舱 ==========
    REPORT_GENERATE_FAILED(80001, "报表生成失败"),

    // ========== 9xxxx 系统 ==========
    UNKNOWN(99999, "未知错误");

    /** 业务错误码 */
    private final int code;

    /** 错误码对应的可读提示信息 */
    private final String message;

    /**
     * 枚举构造函数
     *
     * @param code    业务错误码
     * @param message 错误码对应的可读提示信息
     */
    BizErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取国际化消息 key
     *
     * @return 形如 "error.BAD_REQUEST" 的 key
     */
    public String getMessageKey() {
        return "error." + name();
    }

    /**
     * 将业务错误码映射到合适的 HTTP 状态码
     *
     * <p>遵循 REST 语义：
     * <ul>
     *   <li>参数/校验类错误 → 400 BAD_REQUEST</li>
     *   <li>认证类错误（未登录/Token 失效） → 401 UNAUTHORIZED</li>
     *   <li>授权类错误（无权限/账号禁用） → 403 FORBIDDEN</li>
     *   <li>资源不存在 → 404 NOT_FOUND</li>
     *   <li>资源冲突/并发冲突 → 409 CONFLICT</li>
     *   <li>资源锁定 → 423 LOCKED</li>
     *   <li>限流 → 429 TOO_MANY_REQUESTS</li>
     *   <li>服务端错误 → 500 INTERNAL_SERVER_ERROR</li>
     *   <li>服务不可用 → 503 SERVICE_UNAVAILABLE</li>
     * </ul>
     *
     * @return 对应的 HTTP 状态码
     */
    public HttpStatus getHttpStatus() {
        return switch (this) {
            // 1xxxx 通用
            case BAD_REQUEST, VALIDATION_FAILED, MISSING_PARAMETER, UNSUPPORTED_MEDIA_TYPE,
                 BIZ_ERROR, CONTRACT_AMOUNT_EXCEED, COST_OVERFLOW, INVOICE_EXCEED,
                 PROFIT_NEGATIVE, BENCH_OVER_LIMIT, WORKFLOW_REJECT,
                 PASSWORD_WEAK, PASSWORD_REUSED,
                 DB_CONSTRAINT_VIOLATION, DB_DATA_INTEGRITY -> HttpStatus.BAD_REQUEST;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case NOT_FOUND, USER_NOT_FOUND, DEPARTMENT_NOT_FOUND, EMPLOYEE_NOT_FOUND,
                 PROJECT_NOT_FOUND, OPPORTUNITY_NOT_FOUND, CONTRACT_NOT_FOUND,
                 PAYMENT_NOT_FOUND, WORKFLOW_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_KEY, USERNAME_DUPLICATE, TIMESHEET_DUPLICATE,
                 RESOURCE_CONFLICT, PROJECT_STATUS_INVALID,
                 DB_DUPLICATE_KEY, DB_LOCK_CONTENTION, RESOURCE_LOCKED -> HttpStatus.CONFLICT;
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case REQUEST_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case INTERNAL_ERROR, UNKNOWN, REPORT_GENERATE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SERVICE_UNAVAILABLE, DB_QUERY_TIMEOUT, DB_CONNECTION_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            // 2xxxx 认证授权
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID,
                 PASSWORD_EXPIRED, REAUTH_REQUIRED, REAUTH_INVALID,
                 MFA_REQUIRED, MFA_INVALID, SESSION_KICKED,
                 PASSWORD_INCORRECT -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, DATA_SCOPE_FORBIDDEN, USER_DISABLED,
                 WORKFLOW_NO_PERMISSION -> HttpStatus.FORBIDDEN;
            case ACCOUNT_LOCKED, USER_LOCKED, TIMESHEET_LOCKED -> HttpStatus.LOCKED;
            case OK -> HttpStatus.OK;
        };
    }
}
