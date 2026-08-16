package com.njydsz.common.core.response;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonPropertyOrder;

/**
 * 统一API返回结果封装类
 *
 * <p>用于前后端交互的标准返回格式，封装了响应码、消息、数据和时间戳。
 *
 * <p><b>响应结构：</b>
 * <ul>
 *   <li>code: 响应码，A00000表示成功，其他表示失败</li>
 *   <li>msg: 响应消息</li>
 *   <li>data: 响应数据</li>
 *   <li>timestamp: 响应时间戳</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 返回成功
 * return BaseResponse.success(user);
 *
 * // 返回失败（走 i18n）
 * return BaseResponse.error(BaseResultCode.NOT_FOUND);
 * }</pre>
 *
 * @param <T> 数据泛型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see IResponse
 */
@Getter
@Setter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "traceId", "requestId", "spanId", "timestamp", "extensions"})
@JsonClass(description = "统一API响应基类，标记可安全反序列化")
public class BaseResponse<T> implements IResponse<T>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码
     */
    public static final String SUCCESS = BaseResultCode.SUCCESS.getCode();

    /**
     * 未知错误状态码（复用 {@link BaseResultCode#UNKNOWN}，与错误码体系保持一致）。
     *
     * <p>该常量表示系统未知错误（C99999），用于与 {@link #SUCCESS} 进行反向校验场景。
     * 命名明确区分"通用错误"与"未知错误"语义，避免与 {@code error()} 方法名混淆。
     *
     * @since 1.7.0
     */
    public static final String UNKNOWN_CODE = BaseResultCode.UNKNOWN.getCode();

    /**
     * 操作成功国际化消息 key（core 模块）
     */
    public static final String MSG_OPERATION_SUCCESS = "core.success";

    /**
     * 操作失败国际化消息 key（core 模块）
     */
    public static final String MSG_OPERATION_FAIL = "core.error";

    /**
     * 返回编码
     */
    @Setter
    private String code;

    /**
     * 返回信息
     */
    @Setter
    private String msg;

    /**
     * 返回数据
     *
     * <p>泛型类型 T 无法限定为 Serializable（API 响应可携带任意类型数据），
     * Java 序列化非主要序列化方式（项目使用 Jackson JSON），此处抑制编译器警告。
     */
    @Setter
    private T data;

    /**
     * 链路追踪 ID。
     *
     * <p>首次调用 {@link #getTraceId()} 时从 RequestContext/MDC 懒解析，
     * 避免批量/流式场景下每次构造响应对象都产生 MDC 读取开销。</p>
     */
    private transient volatile String traceId;

    /**
     * 请求 ID（用于客户端/前端精准排障，单个请求唯一）。
     *
     * <p>volatile + 懒加载：为 {@code null} 时首次访问从 {@link RequestContext#getRequestId()} 解析并缓存。</p>
     *
     * @since 1.10.0
     */
    private transient volatile String requestId;

    /**
     * 当前服务调用的 Span ID（W3C Trace Context span ID）。
     *
     * <p>volatile + 懒加载：traceId 被解析后若 spanId 为空，
     * 自动调用 {@link com.njydsz.common.core.trace.TraceIdGenerator#generateSpanId()} 生成。</p>
     *
     * @since 1.10.0
     */
    private transient volatile String spanId;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 扩展字段（可选）。
     *
     * <p>用于携带额外的上下文信息，如 debugInfo、cost 等。
     * 为 {@code null} 时不序列化（通过 {@code @JsonInclude(NON_NULL)} 控制）。</p>
     *
     * @since 1.6.0
     */
    private Map<String, Object> extensions;

    // 分页字段已迁移至 {@link PageResponse}（v1.9.3）。分页接口请直接返回 {@code PageResponse<T>}。

    /**
     * 默认构造函数。
     *
     * <p>traceId 采用懒加载：首次调用 {@link #getTraceId()} 时从 RequestContext/MDC 解析，
     * 避免批量/流式场景下每次构造都产生 MDC 读取开销。</p>
     */
    public BaseResponse() {
        this.timestamp = System.currentTimeMillis();
        // traceId / requestId / spanId 懒初始化（getter 触发）
    }

    /**
     * 全参数构造函数。
     *
     * @param code 响应码
     * @param msg 响应消息
     * @param data 响应数据
     */
    public BaseResponse(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        // traceId / requestId / spanId 懒初始化（getter 触发）
    }

    /**
     * 全参数构造函数（含显式可观测性字段）。
     *
     * <p>显式传入的 traceId / requestId / spanId 直接写入，跳过懒解析路径。</p>
     *
     * @param code      响应码
     * @param msg       响应消息
     * @param data      响应数据
     * @param requestId 请求 ID
     * @param spanId    Span ID
     * @since 1.10.0
     */
    public BaseResponse(String code, String msg, T data, String requestId, String spanId) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.requestId = requestId;
        this.spanId = spanId;
        this.timestamp = System.currentTimeMillis();
        // traceId 仍走懒加载（未显式传值时）
    }

    /**
     * 懒解析当前链路 traceId：优先从 {@link RequestContext}（统一上下文主源），回退 MDC。
     *
     * <p>仅在首次调用 {@link #getTraceId()} 时执行一次，之后结果缓存到 traceId 字段。</p>
     *
     * @return 当前 traceId；均不存在时返回 null
     */
    private static String resolveTraceId() {
        String traceId = RequestContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }

    /**
     * 创建BaseResponse实例
     *
     * @param code 状态码
     * @param msg 消息
     * @param data 数据
     * @param <T> 数据类型
     * @return BaseResponse实例
     */
    public static <T> BaseResponse<T> of(String code, String msg, T data) {
        return new BaseResponse<>(code, msg, data);
    }

    /**
     * 返回成功消息
     *
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success() {
        return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), null);
    }

    /**
     * 返回成功数据
     *
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success(T data) {
        return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), data);
    }

    /**
     * 返回成功消息
     *
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> successMsg(String msg) {
        return of(SUCCESS, msg, null);
    }

    /**
     * 返回成功消息
     *
     * @param msg 消息内容
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 成功消息
     */
    public static <T> BaseResponse<T> success(String msg, T data) {
        return of(SUCCESS, msg, data);
    }

    /**
     * 返回失败消息
     *
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error() {
        return of(UNKNOWN_CODE, resolveMessage(MSG_OPERATION_FAIL, "操作失败"), null);
    }

    /**
     * 返回失败消息
     *
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String msg) {
        return of(UNKNOWN_CODE, msg, null);
    }

    /**
     * 返回失败消息
     *
     * @param code 错误码
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String code, String msg) {
        return of(code, msg, null);
    }

    /**
     * 返回失败消息
     *
     * @param code 错误码
     * @param msg 消息内容
     * @param data 数据内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String code, String msg, T data) {
        return of(code, msg, data);
    }

    /**
     * 国际化消息解析器接口
     */
    @FunctionalInterface
    public interface MessageResolver {
        /**
         * 解析国际化消息
         *
         * @param key 国际化消息 key
         * @param defaultValue 默认消息文本
         * @return 解析后的消息内容
         */
        String resolve(String key, String defaultValue);
    }

    /**
     * 国际化消息解析器实例（AtomicReference 保证线程安全和一次性设置）。
     *
     * <p>采用一次性设置语义：启动时由 {@code CoreAutoConfiguration} 注入，
     * 后续不可修改，消除全局可变状态的线程安全隐患。</p>
     */
    private static final AtomicReference<MessageResolver> RESOLVER = new AtomicReference<>();

    /**
     * 一次性设置全局消息解析器（仅首次调用生效）。
     *
     * <p>由 {@code CoreAutoConfiguration} 在应用启动时调用。
     * 由于采用一次性设置语义，重复调用不会覆盖已有解析器，
     * 确保 i18n 解析行为在应用生命周期内保持一致。</p>
     *
     * @param resolver 消息解析器实现
     * @return true=设置成功（首次），false=已存在解析器（忽略）
     * @since 1.6.0
     */
    public static boolean setResolverIfAbsent(MessageResolver resolver) {
        boolean success = RESOLVER.compareAndSet(null, resolver);
        if (!success && resolver != null) {
            LoggerFactory.getLogger(BaseResponse.class)
                    .debug("MessageResolver already registered, ignoring subsequent setResolverIfAbsent call");
        }
        return success;
    }

    /**
     * 解析国际化消息，若未设置解析器则返回默认值
     *
     * @param key 国际化消息 key
     * @param defaultValue 默认消息文本
     * @return 解析后的消息内容
     */
    protected static String resolveMessage(String key, String defaultValue) {
        MessageResolver currentResolver = RESOLVER.get();
        if (currentResolver != null) {
            String result = currentResolver.resolve(key, defaultValue);
            return result != null ? result : defaultValue;
        }
        return defaultValue;
    }

    /**
     * 检查国际化消息解析器是否已注册
     *
     * @return 已注册返回 true，否则返回 false
     */
    public static boolean isResolverRegistered() {
        return RESOLVER.get() != null;
    }

    /**
     * 返回失败消息。
     *
     * <p>走 i18n 链路：使用 {@link ResultCode#getKey()} 作为国际化 key 解析消息，
     * 解析失败时回退到 {@link ResultCode#getMsg()}。
     * HTTP 状态码通过 {@code ExceptionCode.getHttpStatus()} 由异常处理器决定。
     *
     * @param resultCode 结果码
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode) {
        return of(resultCode.getCode(),
                  resolveMessage(resultCode.getKey(), resultCode.getMsg()),
                  null);
    }

    /**
     * 返回失败消息（自定义消息覆盖）。
     *
     * <p>自定义消息直接作为响应 message，不走 i18n 解析。
     *
     * @param resultCode 结果码
     * @param msg 自定义消息（覆盖 ResultCode 默认消息，不走 i18n）
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode, String msg) {
        return of(resultCode.getCode(), msg, null);
    }

    /**
     * 获取响应时间戳
     *
     * @return 响应时间戳（毫秒）
     */
    @Override
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * 判断是否成功
     *
     * @return 成功返回 true，否则返回 false
     */
    @Override
    public boolean isSuccess() {
        return SUCCESS.equals(this.code);
    }

    /**
     * 判断是否失败
     *
     * @return 失败返回true，否则返回false
     */
    public boolean isFailed() {
        return !isSuccess();
    }

    /**
     * 获取链路追踪 ID（懒加载）。
     *
     * <p>首次调用时从 {@link RequestContext} / MDC 解析并缓存结果，
     * 后续调用直接返回缓存值，避免每次读取都访问 MDC。</p>
     *
     * @return 当前 traceId；均不存在时返回 null
     */
    public String getTraceId() {
        String tid = traceId;
        if (tid == null) {
            tid = resolveTraceId();
            traceId = tid;
        }
        return tid;
    }

    /**
     * 显式分配 traceId（覆盖懒解析值）。
     *
     * <p>供网关/过滤器在入口处强制设置 traceId 使用，替代旧版 {@code setTraceId}。
     * 仅应在明确需要覆盖懒解析值时使用（如网关统一写入 traceId 到响应体）。</p>
     *
     * @param traceId 要设置的 traceId
     * @since 1.11.0
     */
    public void assignTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 获取请求 ID（懒加载）。
     *
     * <p>首次调用时从 {@link RequestContext#getRequestId()} 解析并缓存结果，
     * 后续调用直接返回缓存值。</p>
     *
     * @return 当前 requestId；上下文未设置时返回 null
     */
    public String getRequestId() {
        String rid = requestId;
        if (rid == null) {
            rid = RequestContext.getRequestId();
            if (rid != null) {
                requestId = rid;
            }
        }
        return rid;
    }

    /**
     * 获取 Span ID（懒加载）。
     *
     * <p>traceId 被解析后若 spanId 为空，自动生成 16 字符 spanId 并缓存。
     * 仅当 traceId 非 null 时才会生成 spanId。</p>
     *
     * @return spanId；traceId 未设置时不生成，返回 null
     */
    public String getSpanId() {
        String sid = spanId;
        if (sid == null) {
            // ensure traceId resolved first so we know if it's present
            String tid = getTraceId();
            if (tid != null) {
                sid = com.njydsz.common.core.trace.TraceIdGenerator.generateSpanId();
                spanId = sid;
            }
        }
        return sid;
    }

    // ======================== 扩展字段操作 ========================

    /**
     * 添加扩展字段（链式调用）。
     *
     * <p>用于携带额外的上下文信息，如 requestId、debugInfo、cost 等。
     * 示例：{@code return BaseResponse.success(data).putExtension("requestId", "req-123");}</p>
     *
     * @param key   扩展键
     * @param value 扩展值
     * @return 当前响应对象（支持链式调用）
     * @since 1.6.0
     */
    public BaseResponse<T> putExtension(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("extension key must not be null or empty");
        }
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    /**
     * 获取扩展字段。
     *
     * @param key 扩展键
     * @return 扩展值；不存在返回 null
     * @since 1.6.0
     */
    public Object getExtension(String key) {
        return this.extensions != null ? this.extensions.get(key) : null;
    }

    /**
     * 获取全部扩展字段（直接暴露内部 Map，供高级场景使用）。
     *
     * <p>调用方可直接操作返回的 Map 实现批量添加、移除等定制逻辑，
     * 避免为核心响应类引入过多的封装方法。</p>
     *
     * @return 扩展字段 Map（可能为 null 表示无扩展）
     * @since 1.8.0
     */
    public Map<String, Object> getExtensions() {
        return this.extensions != null
                ? Collections.unmodifiableMap(this.extensions)
                : Collections.emptyMap();
    }
}
