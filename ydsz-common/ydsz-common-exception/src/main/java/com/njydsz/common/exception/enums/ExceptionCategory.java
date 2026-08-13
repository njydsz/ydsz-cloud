package com.njydsz.common.exception.enums;

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
 * <p>细分场景（如参数校验、基础设施、超时、并发、重复）使用 5 大主分类 + 异常码前缀区分，
 * 通过 {@code ExceptionCode.getCategory()} 实现细粒度归类，无需额外枚举值。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionLevel
 */
public enum ExceptionCategory {

    /**
     * 业务异常 (A)
     * <p>业务逻辑校验失败、业务流程中断等
     * @param "A" "A" 参数说明
     * @param SYSTEM("B" SYSTEM("B" 参数说明
     * @param SECURITY("C" SECURITY("C" 参数说明
     * @param RATE_LIMIT("D" RATE_LIMIT("D" 参数说明
     * @param EXTERNAL("E" EXTERNAL("E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     * @return 处理结果
     * @param "A" "A" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     */
    BUSINESS("A", "业务异常"),

    /**
     * 系统异常 (B)
     * <p>系统内部错误、空指针、数据库错误等
     * @param "B" "B" 参数说明
     * @param SECURITY("C" SECURITY("C" 参数说明
     * @param RATE_LIMIT("D" RATE_LIMIT("D" 参数说明
     * @param EXTERNAL("E" EXTERNAL("E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     * @return 处理结果
     * @param "B" "B" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     */
    SYSTEM("B", "系统异常"),

    /**
     * 安全/权限异常 (C)
     * <p>认证失败、授权不足、访问被拒绝等
     * @param "C" "C" 参数说明
     * @param RATE_LIMIT("D" RATE_LIMIT("D" 参数说明
     * @param EXTERNAL("E" EXTERNAL("E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     * @return 处理结果
     * @param "C" "C" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     */
    SECURITY("C", "安全异常"),

    /**
     * 限流/熔断/降级异常 (D)
     * <p>请求被限流、触发熔断、服务降级、热点参数限流等
     * @param "D" "D" 参数说明
     * @param EXTERNAL("E" EXTERNAL("E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     * @return 处理结果
     * @param "D" "D" 参数说明
     * @param EXTERNAL("E" EXTERNAL("E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     */
    RATE_LIMIT("D", "限流熔断异常"),

    /**
     * 外部/三方服务异常 (E)
     * <p>调用外部服务（支付、短信、邮件、API网关等）失败
     * @param "E" "E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     * @return 处理结果
     * @param "E" "E" 参数说明
     * @param "外部服务异常" "外部服务异常" 参数说明
     */
    EXTERNAL("E", "外部服务异常");

    /**
     * 分类编码
     */
    private final String code;
    /**
     * 分类描述
     */
    private final String description;

    /**
     * 构造异常分类枚举
     *
     * @param code        分类编码
     * @param description 分类描述
     * @return 处理结果
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
     * 判断是否为 5 大主分类之一（当前枚举仅保留主分类，始终返回 true）。
     *
     * @return true-是主分类
     */
    public boolean isPrimary() {
        return true;
    }

    /**
     * 根据分类编码获取枚举
     *
     * <p>仅支持 A/B/C/D/E 编码（对应 5 大主分类）。
     * 细分场景建议在异常码前缀或 {@code ExceptionCode.getCategory()} 中体现，
     * 而非通过 {@link ExceptionCategory} 枚举区分。
     *
     * @param code 分类编码（A/B/C/D/E）
     * @return 匹配的枚举实例，未找到返回 {@link #BUSINESS}
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
