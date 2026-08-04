package com.remisoft.common.exception.custom;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.exception.core.ExceptionInfo;
import com.remisoft.common.exception.enums.ExceptionCategory;
import com.remisoft.common.exception.enums.ExceptionCode;
import com.remisoft.common.exception.enums.ExceptionLevel;

/**
 * 异常抽象基类
 *
 * <p>封装所有异常的公共字段和逻辑，消除子类代码重复。
 * 子类只需通过构造函数传入各自的默认值（如错误码、HTTP 状态码、级别、分类）即可。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>公共字段管理</b>：code / key / params / httpStatus / level / category / path / timestamp</li>
 *   <li><b>国际化消息解析</b>：懒加载 + 缓存，调用 {@link #getMessage()} 时才解析 i18n 文案</li>
 *   <li><b>链路追踪</b>：自动写入 path、timestamp，便于分布式追踪</li>
 * </ul>
 *
 * <p><b>消息解析器注入：</b>由 {@code I18nConfiguration} 通过
 * {@link #setMessageResolver(BiFunction)} 注入国际化函数，避免硬依赖 Spring MessageSource。
 * 使用 {@link AtomicReference} 而非 volatile，提供更优的并发性能。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see ExceptionCode
 * @see ExceptionCategory
 * @see ExceptionLevel
 */
public abstract class AbstractYdszException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(AbstractYdszException.class);

    /**
     * 由 I18nConfiguration 注入，用于异常消息国际化解析
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
    protected String key;
    protected transient Object[] params;
    /** 懒加载消息缓存，首次调用 getMessage() 时解析 */
    protected volatile String message;
    /** 懒加载解析的消息键 */
    protected String messageKey;
    /** 懒加载解析的消息参数 */
    protected transient Object[] messageParams;
    protected transient volatile boolean messageResolved;
    /** HTTP 状态码 */
    protected int httpStatus;
    protected ExceptionLevel level;
    protected ExceptionCategory category;
    protected transient LocalDateTime timestamp;
    protected String path;
    /** 附加数据（通过 BusinessException.data() 设置） */
    protected transient Object extData;

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
        this.messageKey = key;
        this.messageParams = this.params;
    }

    /**
     * 便捷初始化方法：一次性设置所有字段
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
     * 便捷初始化方法：一次性设置所有字段（使用自定义 code）
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

    /**
     * 将异常自身的上下文投影为可序列化的 {@link ExceptionInfo}，供全局异常处理器输出响应体。
     *
     * <p>内部调用 {@link #getMessage()}，会触发国际化文案的懒加载解析，
     * 因此本方法必须在请求线程（LocaleContextHolder 已就绪）内调用，
     * 若在异步线程中调用将按默认 Locale 解析。
     *
     * <p>仅投影通用字段（code / key / message / httpStatus / path / timestamp），
     * {@code traceId} 与 {@code details} 由子类或调用方补充。
     *
     * @return 新建的异常信息对象，永不为 {@code null}；各字段可能为 {@code null}（取决于异常构造时是否赋值）
     */
    protected ExceptionInfo buildExceptionInfo() {
        ExceptionInfo info = new ExceptionInfo();
        info.setCode(this.code);
        info.setKey(this.key);
        info.setMessage(getMessage());
        info.setHttpStatus(this.httpStatus);
        info.setPath(this.path);
        info.setTimestamp(this.timestamp);
        return info;
    }

    /**
     * 解析国际化消息（使用外部注入的 resolver）
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

    /**
     * 将 {@code null} 参数数组归一化为空数组。
     *
     * <p>目的是让 {@code params} 字段保持非 null 不变量，
     * 使 i18n 格式化、参数克隆与序列化路径无需重复做空判断。
     *
     * @param params 原始消息参数数组，可为 {@code null}
     * @return 入参本身；入参为 {@code null} 时返回长度为 0 的新数组，永不返回 {@code null}
     */
    protected static Object[] normalizeParams(Object[] params) {
        return params == null ? new Object[]{} : params;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 获取消息格式化参数（返回副本，防止外部修改内部状态）。
     *
     * @return 参数数组；未设置时返回 {@code null}
     */
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

    /**
     * 设置异常消息（直接覆盖，跳过懒加载解析）
     *
     * @param message 异常消息
     */
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

    public Object getExtData() {
        return extData;
    }

    public void setExtData(Object extData) {
        this.extData = extData;
    }
}
