package com.remisoft.common.exception.custom;

import com.remisoft.common.exception.enums.ExceptionCategory;
import com.remisoft.common.exception.enums.ExceptionLevel;

/**
 * 异常构建器抽象基类
 *
 * <p>提供通用的链式构建 API，子类只需实现 {@link #doBuild} 方法。
 *
 * @param <E> 异常类型
 * @param <B> 构建器类型（自引用）
 * @author remi-team
 * @since 1.0.0
 */
public abstract class YdszExceptionBuilder<E extends AbstractYdszException, B extends YdszExceptionBuilder<E, B>> {

    protected String code;
    protected String key;
    protected Object[] params;
    protected int httpStatus;
    protected ExceptionLevel level;
    protected ExceptionCategory category;
    protected Throwable cause;
    protected String path;
    protected Object extData;
    protected String message;

    /**
     * 返回构建器自身的具体类型实例，用于支撑 CRTP 泛型自引用。
     *
     * <p>所有链式方法均返回 {@code self()} 而非 {@code this}，
     * 这样子类新增的构建方法在链式调用中不会被向上转型丢失。
     * 实现体固定为 {@code return this;}，不得返回其他对象。
     *
     * @return 当前构建器实例（子类的具体类型），永不为 {@code null}
     */
    protected abstract B self();

    /**
     * 构建异常实例（由子类实现）
     *
     * @param code      错误码
     * @param subCode   子错误码（保留参数兼容性，实际不再使用）
     * @param key       国际化消息键
     * @param params    消息参数
     * @param httpStatus HTTP 状态码
     * @param level     异常级别
     * @param category  异常分类
     * @param cause     原始异常
     * @param path      请求路径
     * @param extData   附加数据
     * @param message   自定义消息
     * @return 异常实例
     */
    protected abstract E doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
                                  ExceptionLevel level, ExceptionCategory category,
                                  Throwable cause, String path, Object extData, String message);

    /**
     * 覆盖业务错误码。
     *
     * <p>子类构建器已在构造时预置各自的默认错误码（如业务异常为 {@code FAIL}、
     * 系统异常为 {@code INTERNAL_ERROR}），仅在需要更精确的错误码时才调用本方法。
     *
     * @param code 业务错误码，用于前端分支处理与错误聚合；传 {@code null} 会清空预置默认值
     * @return 当前构建器，便于链式调用
     */
    public B code(String code) {
        this.code = code;
        return self();
    }

    /**
     * 设置国际化消息键。
     *
     * <p>异常构建时只保存 key，真正的文案在 {@code getMessage()} 时按当前 Locale 解析；
     * 未设置 key 时异常消息将退化为 {@code null}。
     *
     * @param key i18n key，需在 {@code messages.properties} 中存在，否则解析结果回退为 key 本身
     * @return 当前构建器，便于链式调用
     */
    public B key(String key) {
        this.key = key;
        return self();
    }

    /**
     * 设置国际化消息的占位符参数。
     *
     * <p>直接持有传入数组引用，构建完成后不应再修改该数组内容。
     * 参数顺序需与 {@code messages.properties} 中的 {@code {0}}、{@code {1}} 占位符一一对应。
     *
     * @param params 消息参数数组，可为 {@code null}，构建时会被归一化为空数组
     * @return 当前构建器，便于链式调用
     */
    public B params(Object[] params) {
        this.params = params;
        return self();
    }

    /**
     * 覆盖响应 HTTP 状态码。
     *
     * <p>用于在同一异常类型下区分语义，例如业务异常默认 400，
     * 资源不存在场景可显式改为 404、越权场景改为 403。
     *
     * @param httpStatus HTTP 状态码；传 0 将丢失子类预置默认值，需由上层兜底
     * @return 当前构建器，便于链式调用
     */
    public B httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return self();
    }

    /**
     * 覆盖异常级别。
     *
     * <p>级别决定日志打印等级与是否触发告警，
     * 可预期的校验类失败建议降级为 WARN，避免污染错误告警。
     *
     * @param level 异常级别；传 {@code null} 会清空子类预置默认值
     * @return 当前构建器，便于链式调用
     */
    public B level(ExceptionLevel level) {
        this.level = level;
        return self();
    }

    /**
     * 覆盖异常分类。
     *
     * <p>分类用于异常指标的维度聚合（业务/系统/第三方等），
     * 错误的分类会导致 SLO 统计失真，非必要不建议修改子类默认值。
     *
     * @param category 异常分类；传 {@code null} 会清空子类预置默认值
     * @return 当前构建器，便于链式调用
     */
    public B category(ExceptionCategory category) {
        this.category = category;
        return self();
    }

    /**
     * 设置原始异常，保留完整异常链。
     *
     * <p>包装底层异常（如 SQLException、IOException）时必须设置，
     * 否则根因堆栈会丢失，线上问题无法定位。
     *
     * @param cause 原始异常；为 {@code null} 时不建立异常链
     * @return 当前构建器，便于链式调用
     */
    public B cause(Throwable cause) {
        this.cause = cause;
        return self();
    }

    /**
     * 设置触发异常的请求路径。
     *
     * @param path 请求 URI；通常由全局异常处理器统一回填，业务代码一般无需显式设置
     * @return 当前构建器，便于链式调用
     */
    public B path(String path) {
        this.path = path;
        return self();
    }

    /**
     * 设置附加业务数据。
     *
     * <p>该数据不会自动写入异常响应体，需由上层显式读取后决定是否透出；
     * 请勿放入敏感信息，避免被日志或响应意外泄露。
     *
     * @param extData 附加数据，可为任意对象，允许为 {@code null}
     * @return 当前构建器，便于链式调用
     */
    public B extData(Object extData) {
        this.extData = extData;
        return self();
    }

    /**
     * 设置自定义异常消息。
     *
     * <p><b>注意</b>：该值仅透传给 {@link #doBuild} 由具体子类决定是否采纳。
     * 内置的 {@code BusinessExceptionBuilder} 与 {@code SysExceptionBuilder} 均忽略此值，
     * 最终消息仍由 {@link #key(String)} 经国际化解析得到；
     * 需要绕过 i18n 直接指定文案时，请改用异常类中接收 {@code message} 的构造函数。
     *
     * @param message 自定义消息文案，允许为 {@code null}
     * @return 当前构建器，便于链式调用
     */
    public B message(String message) {
        this.message = message;
        return self();
    }

    /**
     * 构建异常实例
     *
     * @return 异常实例
     */
    public E build() {
        return doBuild(
                code, null, key, params, httpStatus, level, category,
                cause, path, extData, message
        );
    }
}
