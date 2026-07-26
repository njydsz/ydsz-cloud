package com.njydsz.common.exception.custom;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 异常抽象基类
 *
 * <p>封装所有异常的公共字段和逻辑，消除子类代码重复。
 * 子类只需通过构造函数传入各自的默认值即可。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractYdszException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(AbstractYdszException.class);

    /**
     * 由 I18nConfiguration 注入，用于异常消息国际化解析
     * <p>使用 AtomicReference 替代 volatile 字段，提供更好的线程安全性和性能
     */
    private static final AtomicReference<BiFunction<String, Object[], String>> MESSAGE_RESOLVER_HOLDER =
        new AtomicReference<>();

    /**
     * 设置消息解析器（由异常模块配置类注入）
     *
     * @param resolver 消息解析函数 (key, params) -> resolved message
     */
    public static void setMessageResolver(BiFunction<String, Object[], String> resolver) {
        MESSAGE_RESOLVER_HOLDER.set(resolver);
    }

    /**
     * 获取当前消息解析器（用于测试验证）
     *
     * @return 当前消息解析函数
     */
    public static BiFunction<String, Object[], String> getMessageResolver() {
        return MESSAGE_RESOLVER_HOLDER.get();
    }

    protected String code;
    protected String subCode;
    protected String key;
    protected transient Object[] params;
    /** 懒加载消息缓存，首次调用 getMessage() 时解析 */
    protected volatile String message;
    /** 懒加载解析的消息键 */
    protected String messageKey;
    /** 懒加载解析的消息参数 */
    protected transient Object[] messageParams;
    protected transient volatile boolean messageResolved;
    /** HTTP 状态码，保持 int 类型以支持自定义状态码和序列化兼容 */
    protected int httpStatus;
    protected ExceptionLevel level;
    protected ExceptionCategory category;
    protected transient LocalDateTime timestamp;
    protected String path;
    protected transient Object extData;
    protected String userMessage;

    /**
     * 默认构造函数
     */
    protected AbstractYdszException() {
        super();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 使用消息构造异常
     *
     * @param message 异常消息
     */
    protected AbstractYdszException(String message) {
        super(message);
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 使用消息和原因构造异常
     *
     * @param message 异常消息
     * @param cause   异常原因
     */
    protected AbstractYdszException(String message, Throwable cause) {
        super(message, cause);
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 指定原因的构造函数
     *
     * @param cause 异常原因
     */
    protected AbstractYdszException(Throwable cause) {
        super(cause);
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 完整构造函数
     *
     * @param message            异常消息
     * @param cause              异常原因
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否可写堆栈
     */
    protected AbstractYdszException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 初始化默认值（final 方法防止子类重写导致 this 逃逸）
     */
    protected final void initDefaults(int httpStatus, ExceptionLevel level, ExceptionCategory category) {
        this.httpStatus = httpStatus;
        this.level = level;
        this.category = category;
    }

    /**
     * 初始化字段（final 方法防止子类重写导致 this 逃逸）
     */
    protected final void initFields(String code, String key, Object[] params) {
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        // 存储懒加载所需的消息 key 和 params
        this.messageKey = key;
        this.messageParams = this.params;
    }

    /**
     * 便捷初始化方法：一次性设置所有字段（消除子类构造函数重复代码）
     *
     * <p>子类构造函数中调用此方法，替代分别调用 {@link #initDefaults} 和 {@link #initFields}。
     *
     * @param exceptionCode 异常码枚举（提供 code/key/httpStatus）
     * @param params        消息参数
     * @param level         异常级别
     * @param category      异常分类
     */
    protected final void init(ExceptionCode exceptionCode, Object[] params,
                               ExceptionLevel level, ExceptionCategory category) {
        initDefaults(exceptionCode.getHttpStatus(), level, category);
        initFields(exceptionCode.getCode(), exceptionCode.getKey(), params);
    }

    /**
     * 便捷初始化方法：一次性设置所有字段（使用默认 HTTP 状态码）
     *
     * @param code        异常码字符串
     * @param key         国际化消息键
     * @param params      消息参数
     * @param httpStatus  HTTP 状态码
     * @param level       异常级别
     * @param category    异常分类
     */
    protected final void init(String code, String key, Object[] params,
                               int httpStatus, ExceptionLevel level, ExceptionCategory category) {
        initDefaults(httpStatus, level, category);
        initFields(code, key, params);
    }

    protected ExceptionInfo buildExceptionInfo() {
        ExceptionInfo info = new ExceptionInfo();
        info.setCode(this.code);
        info.setSubCode(this.subCode);
        info.setKey(this.key);
        info.setMessage(getMessage());
        info.setHttpStatus(this.httpStatus);
        info.setPath(this.path);
        info.setTimestamp(this.timestamp);
        return info;
    }

    /**
     * 解析国际化消息（使用外部注入的 resolver，不再依赖 SpringBeanUtils）
     * <p>使用 AtomicReference.get() 确保线程安全读取
     */
    protected static String resolveMessage(String key, Object[] params) {
        try {
            BiFunction<String, Object[], String> resolver = MESSAGE_RESOLVER_HOLDER.get();
            if (resolver != null) {
                return resolver.apply(key, params);
            }
        } catch (Exception e) {
            log.warn("【异常模块】国际化消息解析失败，回退返回原始 key | key={} | error={}", key, e.getMessage());
        }
        return key;
    }

    protected static Object[] normalizeParams(Object[] params) {
        return params == null ? new Object[]{} : params;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取子错误码（4 位数字字符串）
     *
     * @return 子错误码，默认 "0000"
     */
    public String getSubCode() {
        return subCode == null || subCode.isEmpty() ? "0000" : subCode;
    }

    /**
     * 设置子错误码
     *
     * @param subCode 4 位数字字符串
     */
    public void setSubCode(String subCode) {
        this.subCode = subCode;
    }

    /**
     * 获取完整错误码（主错误码 + 子错误码）
     *
     * <p>如 "A01001-0001"；无子错误码时仅返回主错误码。
     *
     * @return 完整错误码
     */
    public String getFullCode() {
        String effectiveSub = getSubCode();
        if ("0000".equals(effectiveSub)) {
            return code;
        }
        return code + "-" + effectiveSub;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object[] getParams() {
        return params != null ? params.clone() : null;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }

    /**
     * 获取异常消息（懒加载解析）
     *
     * <p>首次调用时通过 messageKey 和 messageParams 解析国际化消息，
     * 解析结果会被缓存，后续调用直接返回缓存值。
     * 使用双重检查锁（DCL）保证线程安全。</p>
     *
     * @return 解析后的异常消息
     */
    @Override
    public String getMessage() {
        if (!messageResolved) {
            synchronized (this) {
                if (!messageResolved) {
                    if (messageKey != null) {
                        message = resolveMessage(messageKey, messageParams);
                    } else if (message == null) {
                        message = super.getMessage();
                    }
                    messageResolved = true;
                }
            }
        }
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        this.messageResolved = true;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public ExceptionLevel getLevel() {
        return level;
    }

    public void setLevel(ExceptionLevel level) {
        this.level = level;
    }

    public ExceptionCategory getCategory() {
        return category;
    }

    public void setCategory(ExceptionCategory category) {
        this.category = category;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUserMessage() {
        return userMessage != null ? userMessage : getMessage();
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public Object getExtData() {
        return extData;
    }

    public void setExtData(Object extData) {
        this.extData = extData;
    }
}
