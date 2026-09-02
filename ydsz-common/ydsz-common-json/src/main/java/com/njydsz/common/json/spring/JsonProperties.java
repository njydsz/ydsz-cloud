package com.njydsz.common.json.spring;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * YdszJson 配置属性。
 *
 * <p>支持通过 YAML 配置文件控制 JSON 序列化/反序列化的全局参数。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   json:
 *     enabled: true
 *     date-format: yyyy-MM-dd HH:mm:ss
 *     naming-strategy: LOWER_CAMEL_CASE
 *     write-nulls: false
 *     pretty-print: false
 *     circular-reference-strategy: REF
 *     serialize-enum-using-ordinal: false
 *     max-json-size: 10485760
 *     max-depth: 256
 *     max-generic-depth: 64
 *     monitoring-enabled: false
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.json")
@Validated
public class JsonProperties {

  /** 是否启用 YdszJson */
  private boolean enabled = true;

  /** 全局日期格式 */
  private String dateFormat = "yyyy-MM-dd HH:mm:ss";

  /** 命名策略 */
  @NotNull private PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;

  /** 是否输出 null 值 */
  private boolean writeNulls = false;

  /** 是否格式化输出 */
  private boolean prettyPrint = false;

  /** 循环引用处理策略（REF / IGNORE / ERROR） */
  private String circularReferenceStrategy = "REF";

  /** 枚举是否使用序号序列化 */
  private boolean serializeEnumUsingOrdinal = false;

  /** 最大 JSON 大小（字节，默认 10MB） */
  @Min(1)
  private long maxJsonSize = 10L * 1024 * 1024;

  /** 最大序列化深度 */
  @Min(1)
  private int maxDepth = 256;

  /** 泛型递归深度上限（防止嵌套泛型 StackOverflow，默认 64） */
  @Min(1)
  private int maxGenericDepth = 64;

  /**
   * 是否启用性能监控（Micrometer 指标采集）。
   *
   * <p><b>预留配置（P1 文档纠偏）：当前版本无消费方，设置后不产生任何监控指标。</b>
   * Micrometer 集成规划中，落地前请勿依赖此开关。默认 true。
   */
  private boolean monitoringEnabled = true;

  /** 是否使用 BigDecimal 解析浮点数（金融场景精度保护） */
  private boolean useBigDecimal = false;

  /** 是否包裹根对象 */
  private boolean wrapRootValue = false;

  /** 反序列化失败时是否抛出异常 */
  private boolean failOnError = false;

  /**
   * 反序列化遇到未知字段时是否抛出异常（P1.5 新增，对标 Jackson FAIL_ON_UNKNOWN_PROPERTIES）。
   *
   * <p>默认 {@code false}（容错跳过，与本模块历史行为一致）；{@code true} 用于接口契约
   * 严格场景（如拼写错误字段的显式暴露）。
   */
  private boolean failOnUnknownProperties = false;

  /** HTTP 请求体最大大小（字节，默认 10MB） */
  private long maxRequestBodySize = 10L * 1024 * 1024;

  /** 是否启用 预热（默认 false，需显式开启） */
  private boolean warmupEnabled = false;

  /**
   * 是否禁用 Spring Boot Jackson 自动配置。
   *
   * <p>默认 false（共存优先，A-3 修复）：不触碰全局 Jackson 自动配置， springdoc-openapi / actuator 等依赖 {@code
   * ObjectMapper} Bean 的组件可正常工作； MVC 层通过 {@code JsonHttpMessageConverter} 的注册顺序已足以让业务接口走 YdszJson。
   *
   * <p>设置为 true 可将 {@code JacksonAutoConfiguration} 加入 {@code
   * spring.autoconfigure.exclude}，适用于强隔离或排查序列化兼容问题的场景。
   *
   * @since 26.09.01
   */
  private boolean disableJacksonAutoConfiguration = false;

  // --- enabled ---

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  // --- dateFormat ---

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  // --- namingStrategy ---

  public PropertyNamingStrategy getNamingStrategy() {
    return namingStrategy;
  }

  public void setNamingStrategy(PropertyNamingStrategy namingStrategy) {
    this.namingStrategy = namingStrategy;
  }

  // --- writeNulls ---

  public boolean isWriteNulls() {
    return writeNulls;
  }

  public void setWriteNulls(boolean writeNulls) {
    this.writeNulls = writeNulls;
  }

  // --- prettyPrint ---

  public boolean isPrettyPrint() {
    return prettyPrint;
  }

  public void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  // --- circularReferenceStrategy ---

  public String getCircularReferenceStrategy() {
    return circularReferenceStrategy;
  }

  public void setCircularReferenceStrategy(String circularReferenceStrategy) {
    this.circularReferenceStrategy = circularReferenceStrategy;
  }

  // --- serializeEnumUsingOrdinal ---

  public boolean isSerializeEnumUsingOrdinal() {
    return serializeEnumUsingOrdinal;
  }

  public void setSerializeEnumUsingOrdinal(boolean serializeEnumUsingOrdinal) {
    this.serializeEnumUsingOrdinal = serializeEnumUsingOrdinal;
  }

  // --- maxJsonSize ---

  public long getMaxJsonSize() {
    return maxJsonSize;
  }

  public void setMaxJsonSize(long maxJsonSize) {
    this.maxJsonSize = maxJsonSize;
  }

  // --- maxDepth ---

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  // --- maxGenericDepth ---

  public int getMaxGenericDepth() {
    return maxGenericDepth;
  }

  public void setMaxGenericDepth(int maxGenericDepth) {
    this.maxGenericDepth = maxGenericDepth;
  }

  // --- monitoringEnabled ---

  public boolean isMonitoringEnabled() {
    return monitoringEnabled;
  }

  public void setMonitoringEnabled(boolean monitoringEnabled) {
    this.monitoringEnabled = monitoringEnabled;
  }

  // --- useBigDecimal ---

  public boolean isUseBigDecimal() {
    return useBigDecimal;
  }

  public void setUseBigDecimal(boolean useBigDecimal) {
    this.useBigDecimal = useBigDecimal;
  }

  // --- wrapRootValue ---

  public boolean isWrapRootValue() {
    return wrapRootValue;
  }

  public void setWrapRootValue(boolean wrapRootValue) {
    this.wrapRootValue = wrapRootValue;
  }

  // --- failOnError ---

  public boolean isFailOnError() {
    return failOnError;
  }

  public void setFailOnError(boolean failOnError) {
    this.failOnError = failOnError;
  }

  // --- failOnUnknownProperties ---

  public boolean isFailOnUnknownProperties() {
    return failOnUnknownProperties;
  }

  public void setFailOnUnknownProperties(boolean failOnUnknownProperties) {
    this.failOnUnknownProperties = failOnUnknownProperties;
  }

  // --- maxRequestBodySize ---

  public long getMaxRequestBodySize() {
    return maxRequestBodySize;
  }

  public void setMaxRequestBodySize(long maxRequestBodySize) {
    this.maxRequestBodySize = maxRequestBodySize;
  }

  // --- warmupEnabled ---

  public boolean isWarmupEnabled() {
    return warmupEnabled;
  }

  public void setWarmupEnabled(boolean warmupEnabled) {
    this.warmupEnabled = warmupEnabled;
  }

  // --- disableJacksonAutoConfiguration ---

  public boolean isDisableJacksonAutoConfiguration() {
    return disableJacksonAutoConfiguration;
  }

  public void setDisableJacksonAutoConfiguration(boolean disableJacksonAutoConfiguration) {
    this.disableJacksonAutoConfiguration = disableJacksonAutoConfiguration;
  }
}
