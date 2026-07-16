package com.njydsz.common.exception.custom;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 泛型异常 Builder 基类
 *
 * <p>提取所有异常子类 Builder 中的重复代码，
 * 通过泛型实现链式调用和类型安全。
 *
 * <p>使用 {@code self()} 抽象方法替代 {@code (B) this} 强制转换，
 * 从根源消除 unchecked 警告。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 子类 Builder 继承此类，仅需声明各自特有的默认值
 * public class BusinessExceptionBuilder extends YdszExceptionBuilder<BusinessException, BusinessExceptionBuilder> {
 *     public BusinessExceptionBuilder() {
 *         defaultCode(UnifiedExceptionCode.FAIL.getCode());
 *         defaultHttpStatus(HttpStatus.BAD_REQUEST.value());
 *         defaultLevel(ExceptionLevel.ERROR);
 *         defaultCategory(ExceptionCategory.BUSINESS);
 *     }
 *     @Override
 *     protected BusinessException self() {
 *         return this;
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
 * @author ydsz-team
 * @since 3.0.0
 */
public abstract class YdszExceptionBuilder<T extends AbstractYdszException, B extends YdszExceptionBuilder<T, B>> {

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
     * 返回当前 Builder 实例，子类实现为 {@code return this;}
     *
     * <p>替代 {@code (B) this} 强制转换，从根源消除 unchecked 警告。
     * 这是 CRTP（Curiously Recurring Template Pattern）的标准实现方式。
     *
     * @return 当前 Builder 实例
     */
    protected abstract B self();

    /**
     * 子类构造函数中调用此方法设置默认值
     */
    protected B defaultCode(String code) {
        this.code = code;
        return self();
    }

    /**
     * 设置默认HTTP状态码（子类构造函数中调用）
     *
     * @param httpStatus HTTP 状态码
     * @return 当前 Builder
     */
    protected B defaultHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return self();
    }

    protected B defaultLevel(ExceptionLevel level) {
        this.level = level;
        return self();
    }

    protected B defaultCategory(ExceptionCategory category) {
        this.category = category;
        return self();
    }

    public B code(String code) {
        this.code = code;
        return self();
    }

    public B key(String key) {
        this.key = key;
        return self();
    }

    public B params(Object... params) {
        this.params = params;
        return self();
    }

    /**
     * 设置覆盖消息（优先于国际化解析）
     *
     * @param message 消息内容
     * @return 当前 Builder
     */
    public B message(String message) {
        this.message = message;
        return self();
    }

    public B httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return self();
    }

    /**
     * 设置异常级别
     *
     * @param level 异常级别
     * @return 当前 Builder
     */
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

    /**
     * 设置扩展数据
     *
     * @param extData 扩展数据
     * @return 当前 Builder
     */
    public B extData(Object extData) {
        this.extData = extData;
        return self();
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
