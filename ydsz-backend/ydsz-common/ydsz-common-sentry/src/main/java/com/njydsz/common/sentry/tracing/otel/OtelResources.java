package com.njydsz.common.sentry.tracing.otel;

import java.util.HashMap;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * YDSZ Resource 工厂
 *
 * <p>Resource 是 OTel 中描述"产生遥测数据主体"的元信息（服务名、版本、命名空间等）。
 * 本工厂封装 YDSZ 标准的 Resource 创建逻辑。
 *
 * <p>生成的 Resource 包含：
 * <ul>
 *   <li>{@code service.name}：服务名</li>
 *   <li>{@code service.version}：服务版本（与 Maven 一致）</li>
 *   <li>{@code service.namespace}：业务域（默认 ydsz）</li>
 *   <li>{@code service.instance.id}：实例 ID（雪花算法）</li>
 *   <li>{@code deployment.environment}：环境</li>
 *   <li>{@code host.name} / {@code host.arch}：主机信息</li>
 *   <li>{@code process.runtime.*}：运行时信息</li>
 *   <li>{@code ydsz.*}：YDSZ 自定义属性</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class OtelResources {

    private OtelResources() {
        throw new UnsupportedOperationException("OtelResources is a utility class");
    }

    /**
     * 创建 YDSZ 标准 Resource
     */
    public static Resource create(YdszResourceConfig config) {
        try {
            AttributesBuilder attrs = Attributes.builder();

            // OTel 语义约定
            putIfNotNull(attrs, OtelSemConv.SERVICE_NAME, config.getServiceName());
            putIfNotNull(attrs, OtelSemConv.SERVICE_VERSION, config.getServiceVersion());
            putIfNotNull(attrs, OtelSemConv.SERVICE_NAMESPACE, config.getServiceNamespace());
            putIfNotNull(attrs, OtelSemConv.SERVICE_INSTANCE_ID, config.getServiceInstanceId());
            putIfNotNull(attrs, OtelSemConv.DEPLOYMENT_ENVIRONMENT, config.getEnvironment());

            // 主机信息
            if (config.isIncludeHostInfo()) {
                try {
                    String hostName = java.net.InetAddress.getLocalHost().getHostName();
                    putIfNotNull(attrs, AttributeKey.stringKey("host.name"), hostName);
                } catch (Exception ignored) {
                    // 主机名获取失败不影响主流程
                }
                attrs.put(AttributeKey.stringKey("host.arch"), System.getProperty("os.arch", "unknown"));
            }

            // 进程信息
            if (config.isIncludeProcessInfo()) {
                attrs.put(AttributeKey.stringKey("process.runtime.name"),
                        System.getProperty("java.runtime.name", "unknown"));
                attrs.put(AttributeKey.stringKey("process.runtime.version"),
                        System.getProperty("java.runtime.version", "unknown"));
                attrs.put(AttributeKey.stringKey("process.pid"), Long.toString(ProcessHandle.current().pid()));
            }

            // YDSZ 自定义属性
            if (config.getCustomAttributes() != null) {
                for (Map.Entry<String, String> entry : config.getCustomAttributes().entrySet()) {
                    putIfNotNull(attrs, entry.getKey(), entry.getValue());
                }
            }

            log.info("[Sentry] OtelResource 创建完成：service={}, version={}, env={}, instance={}",
                    config.getServiceName(), config.getServiceVersion(),
                    config.getEnvironment(), config.getServiceInstanceId());

            return Resource.getDefault().merge(Resource.create(attrs.build()));
        } catch (Exception e) {
            log.error("[Sentry] OtelResource 创建失败，回退到默认", e);
            return Resource.getDefault();
        }
    }

    private static void putIfNotNull(AttributesBuilder builder, AttributeKey<String> key, String value) {
        if (value != null) {
            builder.put(key, value);
        }
    }

    private static void putIfNotNull(AttributesBuilder builder, String key, String value) {
        if (value != null) {
            builder.put(key, value);
        }
    }

    /**
     * 使用默认配置创建
     */
    public static Resource createDefault(String serviceName) {
        return create(YdszResourceConfig.builder().serviceName(serviceName).build());
    }

    // ============================================================================
    // 配置
    // ============================================================================

    @Data
    @Builder
    public static class YdszResourceConfig {
        /** 服务名 */
        @Builder.Default
        private String serviceName = "ydsz-unknown";
        /** 服务版本 */
        @Builder.Default
        private String serviceVersion = "1.0.0";
        /** 服务命名空间 */
        @Builder.Default
        private String serviceNamespace = "ydsz";
        /** 实例 ID（建议使用雪花 ID） */
        @Builder.Default
        private String serviceInstanceId = null;
        /** 部署环境 */
        @Builder.Default
        private String environment = "dev";
        /** 是否包含主机信息 */
        @Builder.Default
        private boolean includeHostInfo = true;
        /** 是否包含进程信息 */
        @Builder.Default
        private boolean includeProcessInfo = true;
        /** 自定义属性 */
        @Builder.Default
        private Map<String, String> customAttributes = new HashMap<>();
    }

}
