package com.njydsz.common.exception.enums;

import com.njydsz.common.core.code.ResultCode;

/**
 * 异常码接口 — 业务异常语义扩展。
 *
 * <p>继承 {@link ResultCode} 协议层三要素（code / key / msg），
 * 增加 HTTP 状态码、错误分类等异常层语义。i18n 解析由 core 模块统一处理。
 *
 * <p><b>继承体系：</b>
 * <pre>
 *   ResultCode（协议层：code + key + msg）
 *     ↑
 *   ExceptionCode（异常层扩展：+ httpStatus + category）
 *     ↑ 全部业务模块 *ExceptionCode 枚举
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @YdszExceptionCode(module = "workflow", description = "工作流")
 * public enum WorkflowExceptionCode implements ExceptionCode {
 *     TEMPLATE_NOT_FOUND("workflow.template.not.found", 404),
 *     ;
 *     private final String key;
 *     private final int httpStatus;
 *     // ...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ResultCode
 * @see ExceptionCategory
 * @see ExceptionLevel
 */
public interface ExceptionCode extends ResultCode {

    // ======================== 协议层实现（覆盖 ResultCode 默认值） ========================

    /**
     * 错误码字符串（由 key 派生）。
     *
     * <p>默认将 key 完整作为 code（业务枚举可以覆盖）。
     */
    @Override
    default String getCode() {
        return getKey();
    }

    /**
     * i18n 解析失败时的兜底文案。默认委托 {@link #getKey()}，
     * 业务可覆盖以提供中文兜底文案。
     */
    @Override
    default String getMsg() {
        return getKey();
    }

    /**
     * 将异常映射到对应的 HTTP 状态码。
     *
     * <p>默认返回 400（HTTP Bad Request）。每个枚举强烈建议显式覆盖此方法，
     * 声明该异常对应的标准 HTTP 状态码（401/403/404/409/429/500/503 等）。
     *
     * @return 对应的 HTTP 状态码
     */
    default int getHttpStatus() {
        return 400;
    }

    // ======================== 异常层扩展能力 ========================

    /**
     * 错误分类：从国际化 key 推断前缀（如 "user." → BUSINESS，"sys." → SYSTEM）。
     *
     * <ul>
     *   <li>"sys." / "system." 开头 — 系统级异常（SYSTEM）</li>
     *   <li>"sec." / "auth." 开头 — 安全类异常（SECURITY）</li>
     *   <li>其他 — 默认 BUSINESS</li>
     * </ul>
     */
    default ExceptionCategory getCategory() {
        String key = getKey();
        if (key == null || key.isEmpty()) return ExceptionCategory.BUSINESS;
        String lower = key.toLowerCase();
        if (lower.startsWith("sys.") || lower.startsWith("system.")) {
            return ExceptionCategory.SYSTEM;
        }
        if (lower.startsWith("sec.") || lower.startsWith("auth.")) {
            return ExceptionCategory.SECURITY;
        }
        return ExceptionCategory.BUSINESS;
    }
}
