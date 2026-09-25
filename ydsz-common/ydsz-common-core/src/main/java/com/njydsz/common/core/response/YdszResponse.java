package com.njydsz.common.core.response;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonPropertyOrder;

/**
 * 统一API返回结果封装类
 *
 * <p>用于前后端交互的标准返回格式，封装了响应码、消息、数据和时间戳。
 *
 * <p><b>响应结构：</b>
 *
 * <ul>
 *   <li>code: 响应码，A00000表示成功，其他表示失败
 *   <li>msg: 响应消息
 *   <li>data: 响应数据
 *   <li>timestamp: 响应时间戳
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 返回成功
 * return YdszResponse.success(user);
 *
 * // 返回失败（走 i18n）
 * return YdszResponse.error(YdszResultCode.NOT_FOUND);
 * }</pre>
 *
 * @param <T> 数据泛型
 * @author ydsz-team
 * @since 26.09.01
 * @see IResponse
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "code",
  "msg",
  "level",
  "data",
  "traceId",
  "requestId",
  "spanId",
  "timestamp",
  "extensions"
})
@JsonClass(description = "统一API响应基类，标记可安全反序列化")
public class YdszResponse<T> implements IResponse<T>, Serializable {

  /** 序列化版本 UID，兼容 Lombok @SuperBuilder 生成的子类序列化。 */
  private static final long serialVersionUID = 1L;

  /**
   * 成功状态码（复用 {@link YdszResultCode#SUCCESS}，与错误码体系保持一致）。
   *
   * <p>该常量表示操作成功（0），用于构造成功响应或判断响应是否成功。
   */
  public static final String SUCCESS = YdszResultCode.SUCCESS.getCode();

  /**
   * 未知错误状态码（复用 {@link YdszResultCode#UNKNOWN}，与错误码体系保持一致）。
   *
   * <p>该常量表示系统未知错误（C99999），用于与 {@link #SUCCESS} 进行反向校验场景。 命名明确区分"通用错误"与"未知错误"语义，避免与 {@code error()}
   * 方法名混淆。
   *
   * @since 26.09.01
   */
  public static final String UNKNOWN_CODE = YdszResultCode.UNKNOWN.getCode();

  /** 操作成功国际化消息 key（core 模块）。 */
  public static final String MSG_OPERATION_SUCCESS = "core.success";

  /** 操作失败国际化消息 key（core 模块）。 */
  public static final String MSG_OPERATION_FAIL = "core.error";

  /** 返回编码 */
  @Setter private String code;

  /** 返回信息 */
  @Setter private String msg;

  /**
   * 异常级别。
   *
   * <p>取值来自 {@link com.njydsz.common.exception.enums.ExceptionLevel} 枚举的 name：
   *
   * <ul>
   *   <li>INFO — 静默处理，不展示 toast
   *   <li>WARN — 轻量 toast 提示（auto-close 3s）
   *   <li>ERROR — toast 提示，需用户点击关闭
   *   <li>FATAL — 弹窗提示，阻断用户当前操作
   * </ul>
   *
   * <p>成功响应不需要级别，为 {@code null} 时不序列化（通过 {@code @JsonInclude(NON_NULL)} 控制）。
   */
  private String level;

  /**
   * 返回数据
   *
   * <p>泛型类型 T 无法限定为 Serializable（API 响应可携带任意类型数据）， Java 序列化非主要序列化方式（项目使用 Jackson JSON），此处抑制编译器警告。
   */
  @Setter private T data;

  /**
   * 链路追踪 ID。
   *
   * <p>首次调用 {@link #getTraceId()} 时从 RequestContext/MDC 懒解析， 避免批量/流式场景下每次构造响应对象都产生 MDC 读取开销。
   */
  private transient volatile String traceId;

  /**
   * 请求 ID（用于客户端/前端精准排障，单个请求唯一）。
   *
   * <p>volatile + 懒加载：为 {@code null} 时首次访问从 {@link RequestContext#getRequestId()} 解析并缓存。
   *
   * @since 26.09.01
   */
  private transient volatile String requestId;

  /**
   * 当前服务调用的 Span ID（W3C Trace Context span ID）。
   *
   * <p>volatile + 懒加载：traceId 被解析后若 spanId 为空， 自动调用 {@link
   * com.njydsz.common.core.trace.TraceIdGenerator#generateSpanId()} 生成。
   *
   * @since 26.09.01
   */
  private transient volatile String spanId;

  /** 时间戳 */
  private Long timestamp;

  /**
   * 扩展字段（可选）。
   *
   * <p>用于携带额外的上下文信息，如 debugInfo、cost 等。 为 {@code null} 时不序列化（通过 {@code @JsonInclude(NON_NULL)} 控制）。
   *
   * @since 26.09.01
   */
  private Map<String, Object> extensions;

  // 分页字段已迁移至 {@link PageResponse}（26.09.01）。分页接口请直接返回 {@code PageResponse<T>}。

  /**
   * 默认构造函数。
   *
   * <p>traceId 采用懒加载：首次调用 {@link #getTraceId()} 时从 RequestContext/MDC 解析， 避免批量/流式场景下每次构造都产生 MDC
   * 读取开销。
   */
  public YdszResponse() {
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
  public YdszResponse(String code, String msg, T data) {
    this.code = code;
    this.msg = msg;
    this.data = data;
    this.timestamp = System.currentTimeMillis();
    // traceId / requestId / spanId 懒初始化（getter 触发）
  }

  /**
   * 全参数构造函数（含显式可观测性字段）。
   *
   * <p>显式传入的 traceId / requestId / spanId 直接写入，跳过懒解析路径。
   *
   * @param code 响应码
   * @param msg 响应消息
   * @param data 响应数据
   * @param requestId 请求 ID
   * @param spanId Span ID
   * @since 26.09.01
   */
  public YdszResponse(String code, String msg, T data, String requestId, String spanId) {
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
   * <p>仅在首次调用 {@link #getTraceId()} 时执行一次，之后结果缓存到 traceId 字段。
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
   * 构造指定状态码、消息与数据的响应对象（静态工厂）。
   *
   * <p>不走 i18n 解析，msg 将直接作为响应消息返回给调用方， 适用于已包含最终展示文案的场景（如从异常中直接提取）。
   *
   * @param code 响应码，参考 {@link YdszResultCode} 体系
   * @param msg 响应消息（不走 i18n），直接写入响应体
   * @param data 为 {@code null} 时不序列化（{@code @JsonInclude(NON_NULL)}）
   * @param <T> 数据泛型
   * @return 携带完整三要素的响应实例，不会为 {@code null}
   */
  public static <T> YdszResponse<T> of(String code, String msg, T data) {
    return new YdszResponse<>(code, msg, data);
  }

  /**
   * 返回无数据的成功响应，消息走 i18n 解析（key: {@code core.success}）。
   *
   * <p>适用于写操作（删除/更新/触发类）无需返回业务数据的场景。
   *
   * @param <T> 数据泛型占位
   * @return 成功响应，data 为 {@code null}，msg 由当前 {@link LocaleContextHolder} 解析
   */
  public static <T> YdszResponse<T> success() {
    return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), null);
  }

  /**
   * 返回携带业务数据的成功响应，消息走 i18n 解析。
   *
   * <p>最常用的成功返回路径，适用于查询类接口。
   *
   * @param data 业务数据，为 {@code null} 时响应体不含 data 字段
   * @param <T> 数据泛型
   * @return 成功响应，data 字段为传入值，msg 由 i18n 解析
   */
  public static <T> YdszResponse<T> success(T data) {
    return of(SUCCESS, resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"), data);
  }

  /**
   * 返回自定义消息的成功响应（不走 i18n）。
   *
   * <p>当业务需要在成功时返回非标准提示（如"还有 N 条待审核"）时使用此方法。
   *
   * @param msg 自定义消息，直接写入响应体；不可为 {@code null}
   * @param <T> 数据泛型占位
   * @return 成功响应，data 为 {@code null}
   */
  public static <T> YdszResponse<T> successMsg(String msg) {
    return of(SUCCESS, msg, null);
  }

  /**
   * 返回自定义消息与数据的成功响应（消息不走 i18n）。
   *
   * @param msg 自定义消息，直接写入响应体；不可为 {@code null}
   * @param data 业务数据，为 {@code null} 时响应体不含 data 字段
   * @param <T> 数据泛型
   * @return 成功响应
   */
  public static <T> YdszResponse<T> success(String msg, T data) {
    return of(SUCCESS, msg, data);
  }

  /**
   * 返回系统级未知错误（code: {@code C99999}），消息走 i18n 解析。
   *
   * <p>作为兜底失败响应，适用于未映射到具体 ResultCode 的未知异常场景。
   *
   * @param <T> 数据泛型占位
   * @return code 为 {@code C99999} 的失败响应，data 为 {@code null}
   */
  public static <T> YdszResponse<T> error() {
    return of(UNKNOWN_CODE, resolveMessage(MSG_OPERATION_FAIL, "操作失败"), null);
  }

  /**
   * 返回携带自定义消息的未知错误响应（code: {@code C99999}，不走 i18n）。
   *
   * <p>适用于需要直接透传底层错误描述（如来自 {@link Throwable#getMessage()}）的场景。
   *
   * @param msg 自定义错误消息，直接写入响应体
   * @param <T> 数据泛型占位
   * @return code 为 {@code C99999} 的失败响应，data 为 {@code null}
   */
  public static <T> YdszResponse<T> error(String msg) {
    return of(UNKNOWN_CODE, msg, null);
  }

  /**
   * 返回指定错误码与消息的失败响应（不走 i18n）。
   *
   * <p>适用于业务中已知错误码但需自定义提示的场景，或用于兼容旧接口直接透传错误码。
   *
   * @param code 自定义错误码（需符合 ydsz 错误码体系格式，如 Axxxxx/Bxxxxx/Cxxxxx）
   * @param msg 错误消息，直接写入响应体；不可为 {@code null}
   * @param <T> 数据泛型占位
   * @return 指定 code/msg 的失败响应，data 为 {@code null}
   */
  public static <T> YdszResponse<T> error(String code, String msg) {
    return of(code, msg, null);
  }

  /**
   * 返回指定错误码、消息与数据的失败响应（不走 i18n）。
   *
   * <p>适用于需要在错误响应中携带额外诊断信息（如参数校验失败的字段详情）的场景。
   *
   * @param code 自定义错误码
   * @param msg 错误消息，直接写入响应体
   * @param data 扩展数据（如校验错误字段列表），为 {@code null} 时不序列化
   * @param <T> 数据泛型
   * @return 携带 code/msg/data 三要素的失败响应
   */
  public static <T> YdszResponse<T> error(String code, String msg, T data) {
    return of(code, msg, data);
  }

  /** 国际化消息解析器接口 */
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
   * <p>采用一次性设置语义：启动时由 {@code CoreAutoConfiguration} 注入， 后续不可修改，消除全局可变状态的线程安全隐患。
   */
  private static final AtomicReference<MessageResolver> RESOLVER = new AtomicReference<>();

  /**
   * 一次性设置全局消息解析器（仅首次调用生效）。
   *
   * <p>由 {@code CoreAutoConfiguration} 在应用启动时调用。 由于采用一次性设置语义，重复调用不会覆盖已有解析器， 确保 i18n
   * 解析行为在应用生命周期内保持一致。
   *
   * @param resolver 消息解析器实现
   * @return true=设置成功（首次），false=已存在解析器（忽略）
   * @since 26.09.01
   */
  public static boolean setResolverIfAbsent(MessageResolver resolver) {
    boolean success = RESOLVER.compareAndSet(null, resolver);
    if (!success && resolver != null) {
      LoggerFactory.getLogger(YdszResponse.class)
          .debug(
              "MessageResolver already registered, ignoring subsequent setResolverIfAbsent call");
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
   * 根据 {@link ResultCode} 构造失败响应，消息走 i18n 解析。
   *
   * <p>使用 {@link ResultCode#getKey()} 作为国际化 key 解析消息，解析失败时回退到 {@link ResultCode#getMsg()}。 HTTP
   * 状态码不在响应体中体现，由异常处理器通过 {@code ExceptionCode.getHttpStatus()} 设置。
   *
   * @param resultCode 预定义的结果码枚举，决定响应码、i18n key 与默认消息
   * @param <T> 数据泛型占位
   * @return code/msg 取自 {@link ResultCode} 的失败响应，data 为 {@code null}
   */
  public static <T> YdszResponse<T> error(ResultCode resultCode) {
    return of(resultCode.getCode(), resolveMessage(resultCode.getKey(), resultCode.getMsg()), null);
  }

  /**
   * 根据 {@link ResultCode} 构造失败响应，但使用自定义消息覆盖默认消息。
   *
   * <p>适用于持有结果码（便于前端按 code 分支）但需附加动态信息（如"文件 123 不存在"）的场景。
   *
   * @param resultCode 预定义的结果码枚举，决定响应码
   * @param msg 自定义消息，覆盖 {@link ResultCode#getMsg()} 与 i18n 解析结果；不可为 {@code null}
   * @param <T> 数据泛型占位
   * @return code 取自 {@link ResultCode}、msg 取自参数的失败响应，data 为 {@code null}
   */
  public static <T> YdszResponse<T> error(ResultCode resultCode, String msg) {
    return of(resultCode.getCode(), msg, null);
  }

  /**
   * 返回响应生成时的时间戳（毫秒）。
   *
   * <p>时间戳在构造函数中赋值，代表响应对象创建时刻，而非请求到达时刻。 用于客户端排序、日志关联等场景。
   *
   * @return 响应创建时的 Unix 时间戳（毫秒），不会为 {@code null}
   */
  @Override
  public Long getTimestamp() {
    return timestamp;
  }

  /**
   * 判断当前响应是否表示业务成功。
   *
   * <p>成功判定依据为 {@code code} 等于 {@link #SUCCESS}（A00000）。 code 为 {@code null} 或不匹配任何已知成功码时返回 {@code false}。
   *
   * @return {@code true} 表示业务处理成功（code = A00000）
   */
  @Override
  public boolean isSuccess() {
    return SUCCESS.equals(this.code);
  }

  /**
   * 判断当前响应是否表示业务失败，是 {@link #isSuccess()} 的逻辑取反。
   *
   * <p>等价于 {@code !isSuccess()}，语义更清晰，便于调用方在条件分支中直接阅读意图。
   *
   * @return {@code true} 表示业务处理失败（code 非 A00000）
   */
  public boolean isFailed() {
    return !isSuccess();
  }

  /**
   * 获取链路追踪 ID（懒加载）。
   *
   * <p>首次调用时从 {@link RequestContext} / MDC 解析并缓存结果， 后续调用直接返回缓存值，避免每次读取都访问 MDC。
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
   * <p>供网关/过滤器在入口处强制设置 traceId 使用，替代旧版 {@code setTraceId}。 仅应在明确需要覆盖懒解析值时使用（如网关统一写入 traceId 到响应体）。
   *
   * @param traceId 要设置的 traceId
   * @since 26.09.01
   */
  public void assignTraceId(String traceId) {
    this.traceId = traceId;
  }

  /**
   * 获取请求 ID（懒加载）。
   *
   * <p>首次调用时从 {@link RequestContext#getRequestId()} 解析并缓存结果， 后续调用直接返回缓存值。
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
   * <p>traceId 被解析后若 spanId 为空，自动生成 16 字符 spanId 并缓存。 仅当 traceId 非 null 时才会生成 spanId。
   *
   * @return spanId；traceId 未设置时不生成，返回 null
   */
  public String getSpanId() {
    String sid = spanId;
    if (sid == null) {
      // ensure traceId resolved first so we know if it's present
      String tid = getTraceId();
      if (tid != null) {
        sid = TraceIdGenerator.generateSpanId();
        spanId = sid;
      }
    }
    return sid;
  }

  // ======================== 扩展字段操作 ========================

  /**
   * 添加扩展字段（链式调用）。
   *
   * <p>用于携带额外的上下文信息，如 requestId、debugInfo、cost 等。 示例：{@code return
   * YdszResponse.success(data).putExtension("requestId", "req-123");}
   *
   * @param key 扩展键
   * @param value 扩展值
   * @return 当前响应对象（支持链式调用）
   * @since 26.09.01
   */
  public YdszResponse<T> putExtension(String key, Object value) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("extension key must not be null or empty");
    }
    if (this.extensions == null) {
      this.extensions = new HashMap<>(16);
    }
    this.extensions.put(key, value);
    return this;
  }

  /**
   * 获取扩展字段。
   *
   * @param key 扩展键
   * @return 扩展值；不存在返回 null
   * @since 26.09.01
   */
  public Object getExtension(String key) {
    return this.extensions != null ? this.extensions.get(key) : null;
  }

  /**
   * 获取全部扩展字段（直接暴露内部 Map，供高级场景使用）。
   *
   * <p>调用方可直接操作返回的 Map 实现批量添加、移除等定制逻辑， 避免为核心响应类引入过多的封装方法。
   *
   * @return 扩展字段 Map（可能为 null 表示无扩展）
   * @since 26.09.01
   */
  public Map<String, Object> getExtensions() {
    return this.extensions != null
        ? Collections.unmodifiableMap(this.extensions)
        : Collections.emptyMap();
  }
}
