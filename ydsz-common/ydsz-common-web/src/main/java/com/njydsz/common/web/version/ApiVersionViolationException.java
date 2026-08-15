package com.njydsz.common.web.version;

import java.util.List;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * API 版本注解违规异常。
 *
 * <p>由 {@link ApiVersionChecker} 在应用启动阶段扫描并发现 {@code @ApiVersion} 注解违规时抛出，
 * 携带违规详情列表。异常使用统一错误码体系，绑定
 * {@link CoreExceptionCode#PARAM_ERROR}（A07006 / param.error / HTTP 400 / BUSINESS）。
 *
 * <p><b>错误码语义：</b>版本注解配置属于"参数/规则校验"范畴，启动阶段发现即抛出阻止启动，
 * 与运行时参数校验违规使用同一错误码，保持错误码体系一致性。
 *
 * <p>所有构造器签名保持向后兼容；内部通过继承 {@link BusinessException} 并绑定
 * {@link CoreExceptionCode#PARAM_ERROR} 错误码，实现统一异常响应格式输出。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ApiVersionChecker
 * @see CoreExceptionCode#PARAM_ERROR
 */
public class ApiVersionViolationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 最小版本号（仅供调试参考，违规信息主要承载于 {@link #getMessage()} 返回的字符串中）。
     */
    private final int minVersion = 0;

    // ==================== 构造函数（保持向后兼容） ====================

    /**
     * 默认构造（无自定义消息）。
     *
     * <p>绑定 {@link CoreExceptionCode#PARAM_ERROR} 错误码，消息由 i18n 解析器根据
     * {@code param.error} 键自动填充，适用于无自定义描述的场景。
     */
    public ApiVersionViolationException() {
        super(CoreExceptionCode.PARAM_ERROR);
    }

    /**
     * 携带自定义消息的构造函数。
     *
     * <p>消息通过 {@link #setMessage(String)} 设置，优先于 i18n 解析；
     * 同时绑定 {@link CoreExceptionCode#PARAM_ERROR} 错误码体系。
     *
     * @param message 自定义异常消息（详述违规描述）
     */
    public ApiVersionViolationException(String message) {
        super(CoreExceptionCode.PARAM_ERROR);
        setMessage(message);
    }

    /**
     * 携带详细违规列表的构造函数（主要使用入口）。
     *
     * <p>将 {@code violations} 列表格式化为多行文本作为消息；
     * 同时绑定 {@link CoreExceptionCode#PARAM_ERROR} 错误码体系。
     *
     * @param message    违规概要描述
     * @param violations 具体违规条目列表
     */
    public ApiVersionViolationException(String message, List<String> violations) {
        super(CoreExceptionCode.PARAM_ERROR);
        setMessage(message + "\n\n版本注解违规详情:\n" + String.join("\n", violations));
    }

    /**
     * 携带自定义消息和原始异常链的构造函数。
     *
     * <p>用于包装底层解析异常，完整保留异常链；
     * 同时绑定 {@link CoreExceptionCode#PARAM_ERROR} 错误码体系。
     *
     * @param message 自定义异常消息
     * @param cause   引发本异常的原始异常（保留异常链）
     */
    public ApiVersionViolationException(String message, Throwable cause) {
        super(CoreExceptionCode.PARAM_ERROR, cause);
        setMessage(message);
    }

    /**
     * 仅携带原始异常链的构造函数。
     *
     * <p>适用于直接透传底层异常的场景；
     * 同时绑定 {@link CoreExceptionCode#PARAM_ERROR} 错误码体系。
     *
     * @param cause 引发本异常的原始异常
     */
    public ApiVersionViolationException(Throwable cause) {
        super(CoreExceptionCode.PARAM_ERROR, cause);
    }

    // ==================== Getter ====================

    /**
     * 获取最小版本号（仅供调试参考，违规信息主要承载于 {@link #getMessage()} 返回的字符串中）。
     *
     * @return 最小版本号
     */
    public int getMinVersion() {
        return minVersion;
    }
}
