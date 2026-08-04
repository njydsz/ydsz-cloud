package com.njydsz.common.core.response;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.ProblemDetail;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.slf4j.MDC;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
 * @see PageResponse
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp", "extensions"})
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
     * 失败状态码（复用 {@link BaseResultCode#UNKNOWN}，与错误码体系保持一致）。
     *
     * @deprecated 使用 {@link #UNKNOWN_CODE} 替代，语义更明确
     */
    @Deprecated
    public static final String ERROR = UNKNOWN_CODE;

    /**
     * 国际化消息 key
     */
    public static final String MSG_OPERATION_SUCCESS = "response.success";

    /**
     * 操作失败国际化消息 key
     */
    public static final String MSG_OPERATION_FAIL = "response.error";

    /**
     * 返回编码
     */
    @EqualsAndHashCode.Include
    private String code;

    /**
     * 返回信息
     */
    private String msg;

    /**
     * 返回数据
     *
     * <p>泛型类型 T 无法限定为 Serializable（API 响应可携带任意类型数据），
     * Java 序列化非主要序列化方式（项目使用 Jackson JSON），此处抑制编译器警告。
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 扩展字段（可选）。
     *
     * <p>用于携带额外的上下文信息，如 requestId、debugInfo、cost 等。
     * 为 {@code null} 时不序列化（通过 {@code @JsonInclude(NON_NULL)} 控制）。</p>
     *
     * @since 1.6.0
     */
    private Map<String, Object> extensions;

    /**
     * 默认构造函数
     */
    public BaseResponse() {
        this.timestamp = System.currentTimeMillis();
        this.traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }

    /**
     * 全参数构造函数
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
        this.traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
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
        BaseResponse<T> response = new BaseResponse<>();
        response.code = SUCCESS;
        response.msg = msg;
        return response;
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
        return of(ERROR, resolveMessage(MSG_OPERATION_FAIL, "操作失败"), null);
    }

    /**
     * 返回失败消息
     *
     * @param msg 消息内容
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(String msg) {
        return of(ERROR, msg, null);
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
            org.slf4j.LoggerFactory.getLogger(BaseResponse.class)
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
     * 返回失败消息
     *
     * <p>自动走 i18n 链路：使用 {@link ResultCode#getMessageKey()} 作为
     * 国际化 key 解析消息，解析失败时回退到 {@link ResultCode#getMsg()}。</p>
     *
     * @param resultCode 结果码
     * @param <T> 数据类型
     * @return 失败消息
     */
    public static <T> BaseResponse<T> error(ResultCode resultCode) {
        return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), null);
    }

    /**
     * 返回失败消息
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
     * 从 Throwable 构建标准化错误响应（RFC 7807 Problem Details）。
     *
     * <p>提供与异常体系的轻量级桥接，允许在 core 模块中将任意异常转换为
     * 符合 RFC 7807 规范的 {@link ProblemDetail} 响应。</p>
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>如果异常实现了 {@link ResultCode} 接口（或包含 getCode/getMsg 方法），
     *       优先使用其错误码和 HTTP 状态码</li>
     *   <li>如果异常是标准 RuntimeException，使用 UNKNOWN 错误码</li>
     *   <li>异常消息作为 ProblemDetail.detail 返回</li>
     * </ul>
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * try {
     *     // ... 业务逻辑
     * } catch (BusinessException e) {
     *     return BaseResponse.error(e, URI.create("/api/v1/orders"));
     * } catch (Exception e) {
     *     return BaseResponse.error(e);
     * }
     * }</pre>
     *
     * @param throwable 异常对象
     * @param instance  请求路径 URI（可为 null）
     * @return 携带 {@link ProblemDetail} 的错误响应
     * @since 1.6.0
     * @see ProblemDetail
     */
    public static BaseResponse<ProblemDetail> error(Throwable throwable, URI instance) {
        if (throwable == null) {
            return errorWithDetail(BaseResultCode.UNKNOWN, "未知错误", instance);
        }

        String detail = throwable.getMessage();
        if (detail == null || detail.isEmpty()) {
            detail = throwable.getClass().getSimpleName();
        }

        // 尝试从异常中提取 ResultCode
        ResultCode resultCode = extractResultCode(throwable);
        if (resultCode != null) {
            ProblemDetail problem = ProblemDetail.builder()
                    .type(URI.create(ProblemDetail.DEFAULT_TYPE_PREFIX + resultCode.getCode()))
                    .title(resultCode.getMsg())
                    .status(resultCode.getHttpStatusCode())
                    .detail(detail)
                    .instance(instance)
                    .errorCode(resultCode.getCode())
                    .timestamp(java.time.Instant.now())
                    .build();
            return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
        }

        // 默认使用 UNKNOWN
        return errorWithDetail(BaseResultCode.UNKNOWN, detail, instance);
    }

    /**
     * 从 Throwable 构建标准化错误响应（便捷重载，不携带 instance）。
     *
     * @param throwable 异常对象
     * @return 携带 {@link ProblemDetail} 的错误响应
     * @since 1.6.0
     * @see #error(Throwable, URI)
     */
    public static BaseResponse<ProblemDetail> error(Throwable throwable) {
        return error(throwable, null);
    }

    /**
     * 从异常中提取 ResultCode。
     *
     * <p>支持以下场景：
     * <ul>
     *   <li>异常本身实现了 {@link ResultCode} 接口</li>
     *   <li>异常持有 resultCause 字段（通过反射检测，兼容 ydsz-common-exception 模块）</li>
     * </ul>
     *
     * @param throwable 异常对象
     * @return 提取到的 ResultCode；无法提取时返回 null
     */
    private static ResultCode extractResultCode(Throwable throwable) {
        if (throwable instanceof ResultCode) {
            return (ResultCode) throwable;
        }

        // 尝试通过反射获取异常中的 resultCode/code 字段（兼容 exception 模块的 AbstractYdszException）
        try {
            java.lang.reflect.Field codeField = findField(throwable.getClass(), "resultCode");
            if (codeField != null) {
                codeField.setAccessible(true);
                Object value = codeField.get(throwable);
                if (value instanceof ResultCode) {
                    return (ResultCode) value;
                }
            }
        } catch (Exception ignored) {
            // 反射失败时输出 DEBUG 日志，便于 SECURITY 模式下排查
            org.slf4j.LoggerFactory.getLogger(BaseResponse.class)
                    .debug("Failed to extract ResultCode from exception {} via reflection: {}",
                            throwable.getClass().getName(), ignored.getMessage());
        }

        return null;
    }

    /**
     * 在类层次结构中查找指定字段（包括父类）。
     *
     * @param clazz     起始类
     * @param fieldName 字段名
     * @return 找到的字段；未找到返回 null
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息
     *
     * <p>将标准化的错误详情封装到 {@link ProblemDetail} 中作为 data 返回，
     * 便于前端和第三方系统按 RFC 7807 规范处理错误。
     * 返回类型明确为 {@code BaseResponse<ProblemDetail>}，无需类型强转。</p>
     *
     * @param resultCode 结果码
     * @param detail     错误详情（实例特定信息）
     * @return 携带 {@link ProblemDetail} 的失败消息
     * @since 1.6.0
     * @see ProblemDetail
     */
    public static BaseResponse<ProblemDetail> errorWithDetail(ResultCode resultCode, String detail) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail);
        return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
    }

    /**
     * 返回携带 RFC 7807 Problem Details 的失败消息（含请求路径）
     *
     * <p>返回类型明确为 {@code BaseResponse<ProblemDetail>}，调用方无需强转。</p>
     *
     * @param resultCode 结果码
     * @param detail     错误详情
     * @param instance   请求路径 URI
     * @return 携带 {@link ProblemDetail} 的失败消息
     * @since 1.6.0
     */
    public static BaseResponse<ProblemDetail> errorWithDetail(ResultCode resultCode, String detail, URI instance) {
        ProblemDetail problem = ProblemDetail.of(resultCode, detail, instance);
        return of(resultCode.getCode(), resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()), problem);
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
     * 批量添加扩展字段（链式调用）。
     *
     * @param exts 扩展键值对（可为 null）
     * @return 当前响应对象（支持链式调用）
     * @since 1.6.0
     */
    public BaseResponse<T> putExtensions(Map<String, Object> exts) {
        if (exts != null && !exts.isEmpty()) {
            if (this.extensions == null) {
                this.extensions = new HashMap<>();
            }
            this.extensions.putAll(exts);
        }
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
     * 移除扩展字段（链式调用）。
     *
     * @param key 扩展键
     * @return 当前响应对象（支持链式调用）
     * @since 1.6.0
     */
    public BaseResponse<T> removeExtension(String key) {
        if (this.extensions != null) {
            this.extensions.remove(key);
            // 如果移除后为 null，清理 Map 以节省内存并避免序列化
            if (this.extensions.isEmpty()) {
                this.extensions = null;
            }
        }
        return this;
    }

    /**
     * 判断是否有任何扩展字段。
     *
     * @return 存在扩展字段返回 true；否则返回 false
     * @since 1.6.0
     */
    public boolean hasExtensions() {
        return this.extensions != null && !this.extensions.isEmpty();
    }
}
