package com.remisoft.common.exception.enums;

/**
 * 异常类别枚举
 *
 * <p>用于标识异常所属的分类，便于异常统计和分类处理。
 * 监控系统可基于此维度对异常进行分类统计、聚类分析。
 *
 * <p><b>5 大分类（自 1.0.0 起）：</b>
 * <ul>
 *   <li>{@link #BUSINESS} (A) - 业务逻辑校验失败、业务流程中断，对应 HTTP 4xx</li>
 *   <li>{@link #SYSTEM} (B) - 系统内部错误、空指针、数据库错误，对应 HTTP 5xx</li>
 *   <li>{@link #SECURITY} (C) - 认证失败、授权不足、访问被拒绝，对应 HTTP 401/403</li>
 *   <li>{@link #RATE_LIMIT} (D) - 限流、熔断、降级、热点参数限流，对应 HTTP 429/503</li>
 *   <li>{@link #EXTERNAL} (E) - 外部/三方服务调用失败，对应 HTTP 502/504</li>
 * </ul>
 *
 * <p><b>细分场景（兼容历史）：</b>
 * <ul>
 *   <li>{@link #VALIDATION} (V) - 请求参数校验失败、参数格式错误</li>
 *   <li>{@link #INFRASTRUCTURE} (I) - 网络错误、文件操作失败、缓存异常</li>
 *   <li>{@link #TIMEOUT} (T) - 接口调用超时、数据库查询超时</li>
 *   <li>{@link #CONCURRENCY} (C) - 乐观锁冲突、并发修改冲突（与 SECURITY 同字符）</li>
 *   <li>{@link #DUPLICATE} (D) - 数据唯一约束冲突、重复提交（与 RATE_LIMIT 同字符）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ExceptionLevel
 */
public enum ExceptionCategory {

    /**
     * 业务异常 (A)
     * <p>业务逻辑校验失败、业务流程中断等
     */
    BUSINESS("A", "业务异常"),

    /**
     * 系统异常 (B)
     * <p>系统内部错误、空指针、数据库错误等
     */
    SYSTEM("B", "系统异常"),

    /**
     * 安全/权限异常 (C)
     * <p>认证失败、授权不足、访问被拒绝等
     */
    SECURITY("C", "安全异常"),

    /**
     * 限流/熔断/降级异常 (D)
     * <p>请求被限流、触发熔断、服务降级、热点参数限流等
     */
    RATE_LIMIT("D", "限流熔断异常"),

    /**
     * 外部/三方服务异常 (E)
     * <p>调用外部服务（支付、短信、邮件、API网关等）失败
     */
    EXTERNAL("E", "外部服务异常"),

    // ============ 兼容历史分类（细分场景） ============

    /**
     * 参数校验异常 (V)
     * <p>请求参数校验失败、参数格式错误等
     */
    VALIDATION("V", "参数异常"),

    /**
     * 基础设施异常 (I)
     * <p>网络错误、文件操作失败、缓存异常等
     */
    INFRASTRUCTURE("I", "基础设施异常"),

    /**
     * 超时异常 (T)
     * <p>接口调用超时、数据库查询超时、第三方服务超时等
     */
    TIMEOUT("T", "超时异常"),

    /**
     * 并发异常 (C)
     * <p>乐观锁冲突、并发修改冲突等
     */
    CONCURRENCY("C", "并发异常"),

    /**
     * 重复异常 (D)
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

    /**
     * 判断是否为 5 大主分类之一
     *
     * @return true-是主分类，false-是细分分类
     */
    public boolean isPrimary() {
        return this == BUSINESS || this == SYSTEM || this == SECURITY
                || this == RATE_LIMIT || this == EXTERNAL;
    }

    /**
     * 根据分类编码获取枚举
     *
     * @param code 分类编码（A/B/C/D/E/V/I/T/R）
     * @return 匹配的枚举实例，未找到返回 BUSINESS
     */
    public static ExceptionCategory fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return BUSINESS;
        }
        String upper = code.toUpperCase();
        for (ExceptionCategory category : values()) {
            if (category.code.equals(upper)) {
                return category;
            }
        }
        return BUSINESS;
    }
}
