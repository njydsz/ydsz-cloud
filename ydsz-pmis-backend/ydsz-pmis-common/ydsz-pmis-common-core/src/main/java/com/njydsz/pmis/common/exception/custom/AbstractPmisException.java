package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;
import com.njydsz.pmis.common.exception.i18n.MessageResolverHolder;
import lombok.Getter;

import java.io.Serial;

/**
 * 抽象异常基类
 *
 * <p>所有自定义异常继承此类，统一携带异常码、消息键、参数、HTTP 状态码、级别和分类信息。
 * 支持国际化消息处理、链式构建器和附加数据。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Getter
public abstract class AbstractPmisException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 异常码字符串 */
    protected String code;

    /** 国际化消息键 */
    protected String key;

    /** 消息参数 */
    protected transient Object[] params;

    /** HTTP 状态码 */
    protected int httpStatus = 400;

    /** 异常级别 */
    protected ExceptionLevel level = ExceptionLevel.ERROR;

    /** 异常分类 */
    protected ExceptionCategory category = ExceptionCategory.BUSINESS;

    /** 请求路径（用于问题定位） */
    protected String path;

    /** 附加数据 */
    protected transient Object extData;

    /** 直接消息（覆盖 key 对应的 i18n 消息） */
    protected String message;

    /** 懒加载解析后的消息（DCL 双重检查锁） */
    private volatile String resolvedMessage;

    /** 标记消息是否已解析（避免 null 消息重复解析） */
    private volatile boolean messageResolved;

    protected AbstractPmisException() {
        super();
    }

    protected AbstractPmisException(String message) {
        super(message);
        this.message = message;
    }

    protected AbstractPmisException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    protected AbstractPmisException(Throwable cause) {
        super(cause);
    }

    /**
     * 使用异常码和消息构造
     *
     * @param code    异常码
     * @param message 消息
     */
    protected AbstractPmisException(ExceptionCode code, String message) {
        super(message);
        this.message = message;
        this.code = code.getCode();
        this.key = code.getKey();
        this.httpStatus = code.getHttpStatus();
    }

    /**
     * 使用异常码、消息和参数构造
     *
     * @param code    异常码
     * @param message 消息
     * @param args    消息参数
     */
    protected AbstractPmisException(ExceptionCode code, String message, Object... args) {
        super(message);
        this.message = message;
        this.code = code.getCode();
        this.key = code.getKey();
        this.httpStatus = code.getHttpStatus();
        this.params = args;
    }

    /**
     * 初始化核心字段
     *
     * @param code   异常码
     * @param key    消息键
     * @param params 消息参数
     */
    protected void initFields(String code, String key, Object[] params) {
        this.code = code;
        this.key = key;
        this.params = params;
    }

    /**
     * 设置 HTTP 状态码
     *
     * @param httpStatus HTTP 状态码
     */
    protected void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * 设置异常级别
     *
     * @param level 异常级别
     */
    protected void setLevel(ExceptionLevel level) {
        this.level = level;
    }

    /**
     * 设置异常分类
     *
     * @param category 异常分类
     */
    protected void setCategory(ExceptionCategory category) {
        this.category = category;
    }

    /**
     * 设置请求路径
     *
     * @param path 请求路径
     */
    protected void setPath(String path) {
        this.path = path;
    }

    /**
     * 设置附加数据
     *
     * @param extData 附加数据
     */
    protected void setExtData(Object extData) {
        this.extData = extData;
    }

    /**
     * 添加附加数据（链式调用）
     *
     * @param key   数据键
     * @param value 数据值
     * @return 当前异常对象
     */
    public AbstractPmisException data(String key, Object value) {
        if (this.extData == null) {
            this.extData = new java.util.concurrent.ConcurrentHashMap<>();
        }
        if (this.extData instanceof java.util.Map) {
            ((java.util.Map<String, Object>) this.extData).put(key, value);
        }
        return this;
    }

    /**
     * 获取异常消息（懒加载 i18n 解析 + DCL 双重检查锁）
     *
     * <p>解析策略：
     * <ol>
     *   <li>若 {@link #message} 已设置（直接消息），直接返回</li>
     *   <li>若 {@link #key} 是 i18n key（以 "error." 开头），通过
     *       {@link MessageResolverHolder} 懒加载解析</li>
     *   <li>DCL 保证多线程下只解析一次</li>
     *   <li>解析器未注册时回退到 key 本身</li>
     * </ol>
     *
     * @return 异常消息
     */
    @Override
    public String getMessage() {
        // 1. 直接消息优先
        if (message != null) {
            return message;
        }
        // 2. 无 i18n key 时回退到父类消息
        if (key == null || key.isEmpty()) {
            return super.getMessage();
        }
        // 3. 非 i18n key 直接返回 key
        if (!key.startsWith("error.")) {
            return key;
        }
        // 4. DCL 懒加载解析 i18n 消息
        if (!messageResolved) {
            synchronized (this) {
                if (!messageResolved) {
                    resolvedMessage = MessageResolverHolder.resolve(key, params);
                    messageResolved = true;
                }
            }
        }
        return resolvedMessage != null ? resolvedMessage : key;
    }

    /**
     * 获取消息键
     *
     * @return 消息键
     */
    public String getMessageKey() {
        return key;
    }

    /**
     * 获取消息参数
     *
     * @return 消息参数数组
     */
    public Object[] getMessageParams() {
        return params;
    }
}
