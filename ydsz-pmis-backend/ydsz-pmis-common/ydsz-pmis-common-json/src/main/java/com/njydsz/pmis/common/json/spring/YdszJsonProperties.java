package com.njydsz.pmis.common.json.spring;

import com.njydsz.pmis.common.json.naming.PropertyNamingStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * YdszJson 配置属性。
 *
 * <p>支持通过 YAML 配置文件控制 JSON 序列化/反序列化的全局参数。
 *
 * <p>配置示例：
 * <pre>{@code
 * pmis:
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
 *     fast-path-enabled: true
 *     safe-mode: true
 *     monitoring-enabled: false
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.json")
public class YdszJsonProperties {

    /** 是否启用 YdszJson */
    private boolean enabled = true;

    /** 全局日期格式 */
    private String dateFormat = "yyyy-MM-dd HH:mm:ss";

    /** 命名策略 */
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
    private long maxJsonSize = 10L * 1024 * 1024;

    /** 最大序列化深度 */
    private int maxDepth = 256;

    /** 是否启用快速路径优化 */
    private boolean fastPathEnabled = true;

    /** 是否启用安全模式（AutoType 白名单检查，默认开启） */
    private boolean safeMode = true;

    /** 是否启用性能监控 */
    private boolean monitoringEnabled = false;
}
