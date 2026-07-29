package com.njydsz.common.exception.custom;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 异常构建器抽象基类
 *
 * <p>提供通用的链式构建 API，子类只需实现 {@link #doBuild} 方法。
 *
 * @param <E> 异常类型
 * @param <B> 构建器类型（自引用）
 * @author ydsz-team
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

    public B code(String code) {
        this.code = code;
        return self();
    }

    public B key(String key) {
        this.key = key;
        return self();
    }

    public B params(Object[] params) {
        this.params = params;
        return self();
    }

    public B httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return self();
    }

    public B level(ExceptionLevel level) {
        this.level = level;
        return self();
    }

    public B category(ExceptionCategory category) {
        this.category = category;
        return self();
    }

    public B cause(Throwable cause) {
        this.cause = cause;
        return self();
    }

    public B path(String path) {
        this.path = path;
        return self();
    }

    public B extData(Object extData) {
        this.extData = extData;
        return self();
    }

    public B message(String message) {
        this.message = message;
        return self();
    }

    /**
     * 构建异常实例
     *
     * @return 异常实例
     */
    @SuppressWarnings("unchecked")
    public E build() {
        return doBuild(
                code, null, key, params, httpStatus, level, category,
                cause, path, extData, message
        );
    }
}
