package com.njydsz.common.exception.custom;
import java.util.LinkedHashMap;
import java.util.Map;
import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.exception.code.IExceptionResultCode;
import com.njydsz.common.exception.core.ExceptionContext;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 异常抽象基类
 *
 * <p>封装所有异常的公共字段和逻辑，消除子类代码重复。
 * 子类只需通过构造函数传入各自的默认值（如错误码、HTTP 状态码、级别、分类）即可。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>公共字段管理</b>：code / key / params / httpStatus</li>
 *   <li><b>国际化消息解析</b>：通过 {@link MessageSourceHolder} 按请求线程 Locale 解析 i18n 文案</li>
 *   <li><b>异常元数据</b>：level / category / snapshot / extData / timestamp 委托给 {@link ExceptionContext}</li>
 * </ul>
 *
 * <p><b>国际化消息解析：</b>通过 {@link MessageSourceHolder} 静态持有者获取 Spring MessageSource，
 * 避免异常类对 Spring 上下文的硬依赖。{@code getMessage()} 直接按当前请求 Locale 解析 i18n 文案，
 * {@link org.springframework.context.i18n.LocaleContextHolder} 基于线程绑定天然保证多语言不串文案。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExceptionCode
 * @see ExceptionCategory
 * @see ExceptionLevel
 * @see ExceptionContext
 */
public abstract class AbstractYdszException extends RuntimeException implements IExceptionResultCode {

    private static final long serialVersionUID = 1L;

    protected String code;
    protected String key;
    protected transient Object[] params;
    /**
     * 通过 setMessage() 显式覆盖的消息（优先于 i18n 解析）
     */
    protected volatile String overrideMessage;
    /**
     * 懒加载解析的消息键
     */
    protected String messageKey;
    /**
     * 懒加载解析的消息参数
     */
    protected transient Object[] messageParams;
    /**
     * HTTP 状态码
     */
    protected int httpStatus;
    /**
     * 异常元数据上下文（level / category / snapshot / extData / timestamp）。
     *
     * <p>延迟初始化：仅在需要设置元数据时分配，避免简单异常场景的对象创建开销。
     */
    protected transient ExceptionContext ctx;

    /**
     * 默认构造函数
     */
    protected AbstractYdszException() {
    }

    /**
     * 使用消息构造异常
     *
     * @param message 异常消息
     */
    protected AbstractYdszException(String message) {
        super(message);
    }

    /**
     * 使用消息和原因构造异常
     *
     * @param message 异常消息
     * @param cause   异常原因
     */
    protected AbstractYdszException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 指定原因的构造函数
     *
     * @param cause 异常原因
     */
    protected AbstractYdszException(Throwable cause) {
        super(cause);
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
    }

    /**
     * 初始化默认值（final 方法防止子类重写导致 this 逃逸）
     *
     * @param httpStatus HTTP 状态码
     * @param level      异常级别
     * @param category   异常分类
     */
    protected final void initDefaults(int httpStatus, ExceptionLevel level, ExceptionCategory category) {
        this.httpStatus = httpStatus;
        this.ctx = new ExceptionContext(level, category);
    }

    /**
     * 初始化字段（final 方法防止子类重写导致 this 逃逸）
     *
     * @param code   错误码
     * @param key    消息键
     * @param params 消息参数
     */
    protected final void initFields(String code, String key, Object[] params) {
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.overrideMessage = null;
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
     * <p>内部调用 {@link #getMessage()}，会触发国际化文案的懒加载解析。
     * 若 {@link MessageSourceHolder} 已注入 Spring MessageSource，则按当前请求 Locale 解析；
     * 若未注入，则返回 messageKey 本身。
     *
     * <p>投影字段包括：code / key / message / httpStatus / timestamp，
     * 以及 {@link #getSnapshot()}（如有，透写入 details 供排查定位）。
     * {@code traceId} 与 {@code details}（非 snapshot）由子类或调用方补充。
     *
     * @return 新建的异常信息对象，永不为 {@code null}；各字段可能为 {@code null}（取决于异常构造时是否赋值）
     */
    protected ExceptionInfo buildExceptionInfo() {
        ExceptionInfo info = new ExceptionInfo();
        info.setCode(this.code);
        info.setKey(this.key);
        info.setMessage(getMessage());
        info.setHttpStatus(this.httpStatus);
        if (this.ctx != null) {
            info.setTimestamp(this.ctx.getTimestamp());
        }
        // 快照透写入 details（details 可枚举，方便前端 / 日志展示）
        Map<String, String> snapshot = getSnapshot();
        if (snapshot != null && !snapshot.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>(snapshot);
            info.setDetails(details);
        }
        return info;
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

    public String getKey() {
        return key;
    }

    /**
     * 获取消息格式化参数（返回副本，防止外部修改内部状态）。
     *
     * @return 参数数组；未设置时返回 {@code null}
     */
    public Object[] getParams() {
        return params != null ? params.clone() : null;
    }

    /**
     * 将异常上下文桥接为 {@link ResultCode}，供响应构建器（{@code BaseResponse.error(Throwable)}）消费。
     *
     * <p>字段映射规则：
     * <ul>
     *   <li>{@link ResultCode#getCode()} → {@code code} 字段</li>
     *   <li>{@link ResultCode#getMsg()} → {@link #getMessage()}（含 i18n 解析）</li>
     *   <li>{@code getKey()} → {@code key} 字段（i18n 消息键，用于二次解析）</li>
     *   <li>{@link ResultCode#getHttpStatus()} → {@code httpStatus} 字段（0 视为 500）</li>
     * </ul>
     *
     * <p>若 {@code code} 与 {@code key} 均为 {@code null}（异常未被初始化），
     * 视为不参与桥接，返回 {@code null}，由调用方走 UNKNOWN 兜底逻辑。
     *
     * @return 桥接后的 ResultCode 视图；未初始化时返回 null
     * @since 1.7.0
     */
    @Override
    public ResultCode resultCode() {
        if (code == null && key == null) {
            return null;
        }
        return new ResultCode() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getKey() {
                return key;
            }

            @Override
            public String getMsg() {
                // 触发 i18n 懒加载解析，结果由 AbstractYdszException.getMessage() 返回
                return AbstractYdszException.this.getMessage();
            }
        };
    }

    /**
     * 获取异常消息（i18n 解析 - 无对象内缓存）。
     *
     * <p>直接通过 {@link MessageSourceHolder} 按当前请求 Locale 解析国际化消息。
     * {@link org.springframework.context.i18n.LocaleContextHolder} 基于 {@link ThreadLocal} 实现，
     * 同一请求线程内 Locale 恒定，天然保证多语言切换不串文案，无需异常对象内维护 ConcurrentHashMap 缓存。
     *
     * <p>若 {@link MessageSourceHolder} 未注入（如非 Spring 环境），
     * 则直接返回 messageKey 本身（兜底行为，保持向后兼容）。
     * 若通过 {@link #setMessage(String)} 显式覆盖消息，优先返回覆盖值。
     *
     * <p>调用方注意：本方法依赖请求线程绑定的 Locale（{@link org.springframework.context.i18n.LocaleContextHolder}），
     * 跨线程场景（如异步任务）需通过 {@code LocaleContextHolder.setLocale(locale)} 传递上下文。
     *
     * @return 解析后的国际化消息；解析器未注入时返回 messageKey
     */
    @Override
    public String getMessage() {
        String override = overrideMessage;
        if (override != null) {
            return override;
        }
        if (messageKey == null) {
            return super.getMessage();
        }
        return MessageSourceHolder.resolve(messageKey, messageParams);
    }

    /**
     * 设置异常消息（直接覆盖，跳过 i18n 解析）。
     *
     * <p>设置后 {@link #getMessage()} 直接返回此值，优先级最高。
     * 传 {@code null} 清除覆盖，恢复走 i18n 解析逻辑。
     *
     * @param message 异常消息；传入 {@code null} 清除覆盖
     */
    public void setMessage(String message) {
        this.overrideMessage = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public ExceptionLevel getLevel() {
        return ctx == null ? null : ctx.getLevel();
    }

    public void setLevel(ExceptionLevel level) {
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.setLevel(level);
    }

    public ExceptionCategory getCategory() {
        return ctx == null ? null : ctx.getCategory();
    }

    public void setCategory(ExceptionCategory category) {
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.setCategory(category);
    }

    public java.time.LocalDateTime getTimestamp() {
        return ctx == null ? null : ctx.getTimestamp();
    }

    public void setTimestamp(java.time.LocalDateTime timestamp) {
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.setTimestamp(timestamp);
    }

    public Map<String, Object> getExtData() {
        return ctx == null ? null : ctx.getExtData();
    }

    public void setExtData(Map<String, Object> extData) {
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.setExtData(extData);
    }

    /**
     * 获取异常上下文快照（不可变视图）。
     *
     * <p>快照通常用于记录异常抛出时的关键业务字段（如 orderId、userId 等），
     * 在全局异常处理器中透写入日志和响应 details，便于运维排查。
     *
     * @return 不可变快照 Map；未设置时返回 {@code null}
     */
    public Map<String, String> getSnapshot() {
        return ctx == null ? null : ctx.getSnapshot();
    }

    /**
     * 设置快照 Map（覆盖式）。
     *
     * <p>内部拷贝传入 Map 为 {@link java.util.LinkedHashMap}，保留插入顺序，便于排查时按设置顺序回溯。
     *
     * @param snapshot 快照 Map，可为 {@code null}
     */
    public void setSnapshot(Map<String, String> snapshot) {
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.setSnapshot(snapshot);
    }

    /**
     * 向上下文快照追加单个键值对（链式调用）。
     *
     * <p>惰性初始化内部 {@link java.util.LinkedHashMap}，首次调用时创建快照容器。
     * 适合在 throw 前逐条追加关键业务信息：
     * <pre>{@code
     * throw BusinessException.builder()
     *     .key("order.create.failed")
     *     .snapshot("orderId", orderId)
     *     .snapshot("userId", userId)
     *     .build();
     * }</pre>
     *
     * @param key   快照键，不可为 {@code null}
     * @param value 快照值（自动 {@code String.valueOf(value)} 转换），可为 {@code null}
     * @return 当前异常对象，便于链式调用
     */
    public AbstractYdszException snapshot(String key, Object value) {
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.addSnapshot(key, value);
        return this;
    }

    /**
     * 向上下文快照追加多个条目（链式调用）。
     *
     * <p>等价于多次调用 {@link #snapshot(String, Object)}，
     * 适合批量传入已有 Map：
     * <pre>{@code
     * Map<String, Object> context = Map.of("orderId", orderId, "skuId", skuId);
     * throw BusinessException.of(ORDER_CREATE_FAILED)
     *     .snapshots(context);
     * }</pre>
     *
     * @param entries 待追加的键值对，可为 {@code null}，值为 {@code null} 时将写入 {@code null}
     * @return 当前异常对象，便于链式调用
     */
    public AbstractYdszException snapshots(Map<String, ?> entries) {
        if (entries == null || entries.isEmpty()) {
            return this;
        }
        if (this.ctx == null) {
            this.ctx = new ExceptionContext();
        }
        this.ctx.addSnapshots(entries);
        return this;
    }
}
