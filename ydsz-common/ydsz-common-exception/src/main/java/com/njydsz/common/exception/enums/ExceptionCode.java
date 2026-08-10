package com.njydsz.common.exception.enums;

import com.njydsz.common.core.code.ResultCode;

/**
 * 异常码接口 — 业务异常语义扩展。
 *
 * <p>继承 {@link ResultCode} 协议层三要素（code / msg / httpStatus），
 * 增加国际化消息键、错误分类等运行时语义。
 *
 * <p><b>继承体系：</b>
 * <pre>
 *   ResultCode（协议层：code + msg + httpStatus）
 *     ↑
 *   ExceptionCode（异常层扩展：+ key + category + i18n）
 *     ↑ 全部业务模块 *ExceptionCode 枚举
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @YdszExceptionCode(module = "workflow", description = "工作流")
 * public enum WorkflowExceptionCode implements ExceptionCode {
 *     TEMPLATE_NOT_FOUND("B70001", "workflow.template.not.found", 404),
 *     ;
 *     private final String code;
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

    // ======================== 协议层实现（覆盖 ResultCode 三方法） ========================

    /**
     * 错误码字符串（如 "B70001"）。
     *
     * <p>前端/客户端通过该字符串识别错误类别并匹配展示文案。
     */
    @Override
    String getCode();

    /**
     * i18n 解析失败时的兜底文案。默认委托 {@link #getKey()}，
     * 业务可覆盖以提供中文兜底文案。
     */
    @Override
    default String getMsg() {
        return getKey();
    }

    /**
     * 错误码对应的 HTTP 状态码。
     *
     * <p>默认返回 400（HTTP Bad Request）。强烈建议每个枚举显式覆盖此方法，
     * 声明该错误对应的标准 HTTP 状态码（401/403/404/409/429/500/503 等）。
     */
    @Override
    default int getHttpStatus() {
        return 400;
    }

    // ======================== 异常层扩展能力 ========================

    /**
     * i18n 国际化消息键（如 "workflow.template.not.found"）。
     *
     * <p>由上层 {@code BaseResponse.error(ExceptionCode)} i18n 链路使用。
     */
    String getKey();

    /**
     * i18n 消息键（供 BaseResponse.error 使用）。
     *
     * <p>默认委托 {@link #getKey()}，业务可覆盖以提供不同的消息键
     * （例如使用枚举常量名而非自定义 key）。
     */
    default String getMessageKey() {
        return getKey();
    }

    /**
     * 错误分类：从主错误码首字符推断。
     *
     * <ul>
     *   <li>A 开头 — 用户端错误（BUSINESS）</li>
     *   <li>B 开头 — 系统级异常（SYSTEM）</li>
     *   <li>C 开头 — 安全类异常（SECURITY）</li>
     *   <li>其他 — 默认 BUSINESS</li>
     * </ul>
     */
    default ExceptionCategory getCategory() {
        String code = getCode();
        if (code == null || code.isEmpty()) return ExceptionCategory.BUSINESS;
        return switch (Character.toUpperCase(code.charAt(0))) {
            case 'B' -> ExceptionCategory.SYSTEM;
            case 'C' -> ExceptionCategory.SECURITY;
            default  -> ExceptionCategory.BUSINESS;
        };
    }
}
