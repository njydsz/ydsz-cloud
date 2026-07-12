package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

/**
 * 泛型异常 Builder 基类
 *
 * <p>提取所有异常子类 Builder 中的重复代码，
 * 通过泛型实现链式调用和类型安全。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 子类 Builder 继承此类，仅需声明各自特有的默认值
 * public class BusinessExceptionBuilder extends RemiExceptionBuilder<BusinessException, BusinessExceptionBuilder> {
 *     public BusinessExceptionBuilder() {
 *         defaultCode(UnifiedExceptionCode.FAIL.getCode());
 *         defaultHttpStatus(HttpStatus.BAD_REQUEST.value());
 *         defaultLevel(ExceptionLevel.ERROR);
 *         defaultCategory(ExceptionCategory.BUSINESS);
 *     }
 *     @Override
 *     protected BusinessException doBuild(String code, String key, Object[] params, int httpStatus,
 *                                         ExceptionLevel level, ExceptionCategory category,
 *                                         Throwable cause, String path, Object extData, String message) {
 *         // 调用具体异常构造函数
 *     }
 * }
 * }</pre>
 *
 * @param <T> 具体异常类型
 * @param <B> 具体 Builder 类型（用于链式调用）
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
public abstract class RemiExceptionBuilder<T extends AbstractRemiException, B extends RemiExceptionBuilder<T, B>> {

    protected String code;
    protected String key;
    protected Object[] params = new Object[]{};
    protected String message;
    protected int httpStatus;
    protected ExceptionLevel level;
    protected ExceptionCategory category;
    protected Throwable cause;
    protected String path;
    protected Object extData;

    /**
     * 子类构造函数中调用此方法设置默认值
     */
    @SuppressWarnings("unchecked")
    protected B defaultCode(String code) {
        this.code = code;
        return (B) this;
    }

    /**
     * 设置默认HTTP状态码（子类构造函数中调用）
     *
     * @param httpStatus HTTP 状态码
     * @return 当前 Builder
     */
    @SuppressWarnings("unchecked")
    protected B defaultHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    protected B defaultLevel(ExceptionLevel level) {
        this.level = level;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    protected B defaultCategory(ExceptionCategory category) {
        this.category = category;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B code(String code) {
        this.code = code;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B key(String key) {
        this.key = key;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B params(Object... params) {
        this.params = params;
        return (B) this;
    }

    /**
     * 设置覆盖消息（优先于国际化解析）
     *
     * @param message 消息内容
     * @return 当前 Builder
     */
    @SuppressWarnings("unchecked")
    public B message(String message) {
        this.message = message;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return (B) this;
    }

    /**
     * 设置异常级别
     *
     * @param level 异常级别
     * @return 当前 Builder
     */
    @SuppressWarnings("unchecked")
    public B level(ExceptionLevel level) {
        this.level = level;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B category(ExceptionCategory category) {
        this.category = category;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B cause(Throwable cause) {
        this.cause = cause;
        return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B path(String path) {
        this.path = path;
        return (B) this;
    }

    /**
     * 设置扩展数据
     *
     * @param extData 扩展数据
     * @return 当前 Builder
     */
    @SuppressWarnings("unchecked")
    public B extData(Object extData) {
        this.extData = extData;
        return (B) this;
    }

    /**
     * 子类实现具体的构建逻辑
     *
     * @param code       错误码
     * @param key        国际化消息键
     * @param params     消息参数
     * @param httpStatus HTTP 状态码
     * @param level      异常级别
     * @param category   异常分类
     * @param cause      异常原因
     * @param path       请求路径
     * @param extData    扩展数据
     * @param message    覆盖消息
     * @return 构建的异常实例
     */
    protected abstract T doBuild(String code, String key, Object[] params, int httpStatus,
                                 ExceptionLevel level, ExceptionCategory category,
                                 Throwable cause, String path, Object extData, String message);

    /**
     * 构建异常实例
     *
     * @return 构建的异常实例
     */
    public T build() {
        T exception = doBuild(code, key, params, httpStatus, level, category, cause, path, extData, message);
        if (message != null) {
            exception.setMessage(message);
        }
        return exception;
    }
}
