package com.njydsz.common.web.version;

import java.util.List;

/**
 * API 版本注解校验失败异常。
 *
 * <p>当 {@link ApiVersionChecker} 检测到不符合规范的 API 版本注解时抛出，阻止应用启动。
 *
 * <p><b>常见触发原因：</b>
 * <ul>
 *   <li>{@code @ApiVersion.since} 格式不符合 {@code vN} 或 {@code vN.N}</li>
 *   <li>{@code deprecatedAt} 版本号不大于 {@code since}（如 since=v2, deprecatedAt=v1，逻辑矛盾）</li>
 *   <li>{@code sunsetAt} 日期格式不符合 ISO-8601</li>
 *   <li>{@code sunsetAt} 已过期（30 天前的 sunset 应删除接口）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class ApiVersionViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 违规详情列表 */
    private final List<String> violations;

    /**
     * 构造校验失败异常
     *
     * @param message    错误摘要
     * @param violations 违规详情
     */
    public ApiVersionViolationException(String message, List<String> violations) {
        super(message);
        this.violations = violations;
    }

    /**
     * 获取所有违规详情
     *
     * @return 违规描述列表
     */
    public List<String> getViolations() {
        return violations;
    }
}
