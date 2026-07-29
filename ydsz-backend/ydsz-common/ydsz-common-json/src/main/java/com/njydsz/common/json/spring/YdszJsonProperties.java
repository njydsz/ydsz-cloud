package com.njydsz.common.json.spring;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.json.naming.PropertyNamingStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * YdszJson 配置属性。
 *
 * <p>支持通过 YAML 配置文件控制 JSON 序列化/反序列化的全局参数。
 *
 * <p>配置示例：
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
 *     safe-mode: true
 *     monitoring-enabled: false
 *     whitelist-packages:
 *       - com.njydsz
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.json")
@Validated
public class YdszJsonProperties {

    /** 是否启用 YdszJson */
    private boolean enabled = true;

    /** 全局日期格式 */
    private String dateFormat = "yyyy-MM-dd HH:mm:ss";

    /** 命名策略 */
    @NotNull
    private PropertyNamingStrategy namingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE;

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

    /** 是否启用安全模式（AutoType 白名单检查，默认开启） */
    private boolean safeMode = true;

    /** 是否启用性能监控 */
    private boolean monitoringEnabled = false;

    /** 是否使用 BigDecimal 解析浮点数（金融场景精度保护） */
    private boolean useBigDecimal = false;

    /** 是否包裹根对象（对标 Jackson @JsonRootValue / ObjectMapper.wrapRootValue） */
    private boolean wrapRootValue = false;

    /** 反序列化失败时是否抛出异常（true）或返回 null（false） */
    private boolean failOnError = true;

    /**
     * 启动时扫描 @YdszJsonClass 注解类的基础包列表。
     *
     * <p>扫描结果会注册到 {@code AutoTypeChecker} 白名单，避免运行时反射加载类的副作用。
     * 支持通配符模式，例如 {@code com.njydsz.*.entity} 匹配所有子包下的 entity 包。
     * 默认扫描 {@code com.njydsz} 包，覆盖所有项目业务代码。</p>
     */
    private List<String> whitelistPackages = Arrays.asList("com.njydsz");

    /** 是否启用流式输出（HTTP 响应使用 chunked transfer encoding） */
    private boolean streamingEnabled = false;

    /** HTTP 请求体最大大小（字节，默认 10MB） */
    private long maxRequestBodySize = 10L * 1024 * 1024;

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

    // --- safeMode ---

    public boolean isSafeMode() {
        return safeMode;
    }

    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
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

    // --- whitelistPackages ---

    public List<String> getWhitelistPackages() {
        return whitelistPackages;
    }

    public void setWhitelistPackages(List<String> whitelistPackages) {
        this.whitelistPackages = whitelistPackages;
    }

    // --- streamingEnabled ---

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    // --- maxRequestBodySize ---

    public long getMaxRequestBodySize() {
        return maxRequestBodySize;
    }

    public void setMaxRequestBodySize(long maxRequestBodySize) {
        this.maxRequestBodySize = maxRequestBodySize;
    }
}
