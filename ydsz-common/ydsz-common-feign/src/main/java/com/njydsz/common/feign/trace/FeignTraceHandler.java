package com.njydsz.common.feign.trace;

/**
 * Feign 链路追踪器接口。
 *
 * <p>提供微服务调用链的追踪能力，支持自定义实现。
 *
 * <p>实现类可以：
 *
 * <ul>
 *   <li>创建和传播 traceId/spanId
 *   <li>记录调用元数据（方法、URL、状态码等）
 *   <li>与各种追踪系统集成（SkyWalking、Zipkin、Jaeger 等）
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * 1. 实现此接口创建自定义追踪器
 * 2. 通过 Spring SPI 机制自动加载
 * 3. 在 application.yml 中配置使用
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FeignTraceHandler {

  /**
   * 获取追踪器名称。
   *
   * @return 追踪器名称
   */
  String getName();

  /**
   * 判断追踪器是否启用。
   *
   * @return true=启用
   */
  default boolean isEnabled() {
    return true;
  }

  /**
   * 记录 Feign 调用开始。
   *
   * @param context 追踪上下文
   */
  default void onRequestStart(TraceContext context) {}

  /**
   * 记录 Feign 调用成功。
   *
   * @param context 追踪上下文
   */
  default void onRequestSuccess(TraceContext context) {}

  /**
   * 记录 Feign 调用失败。
   *
   * @param context 追踪上下文
   * @param throwable 异常信息
   */
  default void onRequestFailure(TraceContext context, Throwable throwable) {}

  /**
   * 获取当前追踪上下文中的 traceId。
   *
   * @return traceId
   */
  default String getCurrentTraceId() {
    return null;
  }

  /**
   * 获取当前追踪上下文中的 spanId。
   *
   * @return spanId
   */
  default String getCurrentSpanId() {
    return null;
  }

  /** Feign 调用追踪上下文。 */
  class TraceContext {
    /** 服务名称 */
    private String serviceName;

    /** 方法名称 */
    private String methodName;

    /** 请求 URL */
    private String url;

    /** HTTP 方法 */
    private String httpMethod;

    /** HTTP 状态码 */
    private int statusCode;

    /** 调用开始时间戳 */
    private long startTime;

    /** 调用结束时间戳 */
    private long endTime;

    /** 追踪唯一标识 */
    private String traceId;

    /** Span 唯一标识 */
    private String spanId;

    /** 父 Span 标识 */
    private String parentSpanId;

    public TraceContext() {}

    public TraceContext(String serviceName, String methodName, String url, String httpMethod) {
      this.serviceName = serviceName;
      this.methodName = methodName;
      this.url = url;
      this.httpMethod = httpMethod;
      this.startTime = System.currentTimeMillis();
    }

    public String getServiceName() {
      return serviceName;
    }

    public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
    }

    public String getMethodName() {
      return methodName;
    }

    public void setMethodName(String methodName) {
      this.methodName = methodName;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getHttpMethod() {
      return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
      this.httpMethod = httpMethod;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public void setStatusCode(int statusCode) {
      this.statusCode = statusCode;
    }

    public long getStartTime() {
      return startTime;
    }

    public void setStartTime(long startTime) {
      this.startTime = startTime;
    }

    public long getEndTime() {
      return endTime;
    }

    public void setEndTime(long endTime) {
      this.endTime = endTime;
    }

    public String getTraceId() {
      return traceId;
    }

    public void setTraceId(String traceId) {
      this.traceId = traceId;
    }

    public String getSpanId() {
      return spanId;
    }

    public void setSpanId(String spanId) {
      this.spanId = spanId;
    }

    public String getParentSpanId() {
      return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
      this.parentSpanId = parentSpanId;
    }

    /**
     * 获取调用耗时。
     *
     * <p>{@code endTime} 已写入（调用已完成）时返回固定差值； 否则返回自 {@code startTime} 起的当前耗时，便于在调用进行中实时观测。
     *
     * @return 耗时（毫秒）
     */
    public long getElapsedTime() {
      return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
    }
  }
}
