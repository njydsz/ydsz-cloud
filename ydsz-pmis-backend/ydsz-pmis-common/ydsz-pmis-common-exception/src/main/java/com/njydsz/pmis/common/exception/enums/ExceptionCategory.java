package com.njydsz.pmis.common.exception.enums;

/**
 * 异常类别枚举
 *
 * <p>用于标识异常所属的分类，便于异常统计和分类处理。
 * 监控系统可基于此维度对异常进行分类统计、聚类分析。
 *
 * <p><b>分类说明：</b>
 * <ul>
 *   <li>{@link #BUSINESS} - 业务逻辑校验失败、业务流程中断</li>
 *   <li>{@link #SYSTEM} - 系统内部错误、空指针、数据库错误</li>
 *   <li>{@link #EXTERNAL} - 调用外部服务（如支付、短信、邮件）失败</li>
 *   <li>{@link #SECURITY} - 认证失败、授权不足、访问被拒绝</li>
 *   <li>{@link #VALIDATION} - 请求参数校验失败、参数格式错误</li>
 *   <li>{@link #INFRASTRUCTURE} - 网络错误、文件操作失败、缓存异常</li>
 *   <li>{@link #TIMEOUT} - 接口调用超时、数据库查询超时</li>
 *   <li>{@link #CONCURRENCY} - 乐观锁冲突、并发修改冲突</li>
 *   <li>{@link #RATE_LIMIT} - 请求被限流、触发熔断</li>
 *   <li>{@link #DUPLICATE} - 数据唯一约束冲突、重复提交</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see ExceptionLevel
 */
public enum ExceptionCategory {

    /**
     * 业务异常
     * <p>业务逻辑校验失败、业务流程中断等
     */
    BUSINESS("B", "业务异常"),

    /**
     * 系统异常
     * <p>系统内部错误、空指针、数据库错误等
     */
    SYSTEM("S", "系统异常"),

    /**
     * 第三方服务异常
     * <p>调用外部服务（如支付、短信、邮件）失败
     */
    EXTERNAL("E", "第三方服务异常"),

    /**
     * 权限异常
     * <p>认证失败、授权不足、访问被拒绝等
     */
    SECURITY("K", "权限异常"),

    /**
     * 参数异常
     * <p>请求参数校验失败、参数格式错误等
     */
    VALIDATION("V", "参数异常"),

    /**
     * 基础设施异常
     * <p>网络错误、文件操作失败、缓存异常等
     */
    INFRASTRUCTURE("I", "基础设施异常"),

    /**
     * 超时异常
     * <p>接口调用超时、数据库查询超时、第三方服务超时等
     */
    TIMEOUT("T", "超时异常"),

    /**
     * 并发异常
     * <p>乐观锁冲突、并发修改冲突等
     */
    CONCURRENCY("C", "并发异常"),

    /**
     * 限流异常
     * <p>请求被限流、触发熔断等
     */
    RATE_LIMIT("R", "限流异常"),

    /**
     * 重复异常
     * <p>数据唯一约束冲突、重复提交等
     */
    DUPLICATE("D", "重复异常");

    /** 分类编码 */
    private final String code;
    /** 分类描述 */
    private final String description;

    /**
     * 构造异常分类枚举
     *
     * @param code        分类编码
     * @param description 分类描述
     */
    ExceptionCategory(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    /**
     * 获取分类描述
     *
     * @return 分类描述
     */
    public String getDescription() {
        return description;
    }
}