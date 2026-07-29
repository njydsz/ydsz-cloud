package com.njydsz.common.core.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Core 模块配置属性
 *
 * <p>仅包含分页和链路追踪相关的核心配置。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * # application.yml
 * ydsz:
 *   core:
 *     enabled: true
 *     max-page-size: 1000
 *     default-page-size: 20
 *     trace:
 *       enabled: true
 *       generate-if-missing: true
 *       id-type: snowflake   # uuid（默认）或 snowflake（有序）
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.core")
@Validated
public class CoreProperties {

    /** 最大每页记录数上限，防止一次性拉取过多数据导致性能问题 */
    @Min(1)
    @Max(5000)
    private int maxPageSize = 1000;

    /** 默认每页记录数，分页查询未指定 pageSize 时使用 */
    @Min(1)
    private int defaultPageSize = 20;

    /** 链路追踪配置 */
    private TraceConfig trace = new TraceConfig();

    /** 租户 MDC 过滤器优先级，默认高于业务过滤器 */
    private int tenantMdcFilterOrder = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * 链路追踪配置属性
     *
     * <p>TraceId 请求头名称由 {@link com.njydsz.common.core.constant.TraceConstants#TRACE_ID_HEADER}
     * 统一定义，不支持配置覆盖，确保全项目一致。
     */
    @Data
    public static class TraceConfig {

        /** 是否启用链路追踪，默认启用 */
        private boolean enabled = true;

        /** 请求头中缺失 TraceId 时是否自动生成 */
        private boolean generateIfMissing = true;

        /** TraceId 生成策略：uuid（无序，默认）或 snowflake（有序，可排序日志） */
        private String idType = "uuid";
    }
}
