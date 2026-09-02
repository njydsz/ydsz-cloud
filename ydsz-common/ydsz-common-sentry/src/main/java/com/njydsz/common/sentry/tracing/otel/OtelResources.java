package com.njydsz.common.sentry.tracing.otel;

import java.net.InetAddress;
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
 * <p>Resource 是 OTel 中描述"产生遥测数据主体"的元信息（服务名、版本、命名空间等）。 本工厂封装 YDSZ 标准的 Resource 创建逻辑。
 *
 * <p>生成的 Resource 包含：
 *
 * <ul>
 *   <li>{@code service.name}：服务名
 *   <li>{@code service.version}：服务版本（与 Maven 一致）
 *   <li>{@code service.namespace}：业务域（默认 ydsz）
 *   <li>{@code service.instance.id}：实例 ID（雪花算法）
 *   <li>{@code deployment.environment}：环境
 *   <li>{@code host.name} / {@code host.arch}：主机信息
 *   <li>{@code process.runtime.*}：运行时信息
 *   <li>{@code ydsz.*}：YDSZ 自定义属性
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public final class OtelResources {

  private OtelResources() {
    throw new UnsupportedOperationException("OtelResources is a utility class");
  }

  /**
   * 按配置组装 YDSZ 标准的 OTel {@link Resource}。
   *
   * <p>依次写入 OTel 语义约定属性（服务名 / 版本 / 命名空间 / 实例 ID / 部署环境）、可选的主机与进程信息，
   * 最后追加 {@code ydsz.*} 自定义属性；为 {@code null} 的字段一律跳过，不会写入空值属性。
   *
   * <p>采集主机信息需要读取主机名与 {@code os.arch}，失败时只记录 debug 日志并跳过，不影响 Resource 生成。
   *
   * @param config 资源描述配置，不允许为 {@code null}；未显式设置的字段走 {@code @Builder.Default} 默认值
   * @return 组装好的 OTel {@link Resource}，不会为 {@code null}
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
          String hostName = InetAddress.getLocalHost().getHostName();
          putIfNotNull(attrs, AttributeKey.stringKey("host.name"), hostName);
        } catch (Exception e) {
          // 主机名获取失败不影响主流程
          log.debug("[OtelResources] 主机名获取失败: {}", e.getMessage());
        }
        attrs.put(AttributeKey.stringKey("host.arch"), System.getProperty("os.arch", "unknown"));
      }

      // 进程信息
      if (config.isIncludeProcessInfo()) {
        attrs.put(
            AttributeKey.stringKey("process.runtime.name"),
            System.getProperty("java.runtime.name", "unknown"));
        attrs.put(
            AttributeKey.stringKey("process.runtime.version"),
            System.getProperty("java.runtime.version", "unknown"));
        attrs.put(
            AttributeKey.stringKey("process.pid"), Long.toString(ProcessHandle.current().pid()));
      }

      // YDSZ 自定义属性
      if (config.getCustomAttributes() != null) {
        for (Map.Entry<String, String> entry : config.getCustomAttributes().entrySet()) {
          putIfNotNull(attrs, entry.getKey(), entry.getValue());
        }
      }

      log.info(
          "[Sentry] OtelResource 创建完成：service={}, version={}, env={}, instance={}",
          config.getServiceName(),
          config.getServiceVersion(),
          config.getEnvironment(),
          config.getServiceInstanceId());

      return Resource.getDefault().merge(Resource.create(attrs.build()));
    } catch (Exception e) {
      log.error("[Sentry] OtelResource 创建失败，回退到默认", e);
      return Resource.getDefault();
    }
  }

  private static void putIfNotNull(
      AttributesBuilder builder, AttributeKey<String> key, String value) {
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
   * 仅指定服务名、其余字段取默认值的快捷构造入口。
   *
   * <p>等价于 {@code create(YdszResourceConfig.builder().serviceName(serviceName).build())}，
   * 适用于只关心服务名归属、不在意版本/命名空间/实例 ID 的轻量接入场景。
   *
   * @param serviceName 服务名，将写入 {@code service.name} 资源属性；为 {@code null} 时会落到默认的
   *     {@code ydsz-unknown}
   * @return 组装好的 OTel {@link Resource}，不会为 {@code null}
   */
  public static Resource createDefault(String serviceName) {
    return create(YdszResourceConfig.builder().serviceName(serviceName).build());
  }

  // ============================================================================
  // 配置
  // ============================================================================

  /**
   * 构造 OTel {@link Resource} 所需的资源描述，采用 Builder 模式，未设置的字段走 {@code @Builder.Default}。
   *
   * <p>字段与 OTel 语义约定属性一一对应：{@code serviceName → service.name}、{@code serviceVersion →
   * service.version}、{@code serviceNamespace → service.namespace}、{@code serviceInstanceId →
   * service.instance.id}、{@code environment → deployment.environment}。
   *
   * <p><b>运维约定：</b>{@code serviceInstanceId} 建议填雪花 ID，用于在同一服务多副本之间区分遥测来源；
   * 留空时该属性不会被写入，后端将无法按实例聚合。
   *
   * <p>本类由 Lombok 生成 Builder，非线程安全，应在装配期构造一次后复用。
   */
  @Data
  @Builder
  public static class YdszResourceConfig {

    /** 服务名 */
    @Builder.Default private String serviceName = "ydsz-unknown";

    /** 服务版本 */
    @Builder.Default private String serviceVersion = "26.09.01";

    /** 服务命名空间 */
    @Builder.Default private String serviceNamespace = "ydsz";

    /** 实例 ID（建议使用雪花 ID） */
    @Builder.Default private String serviceInstanceId = null;

    /** 部署环境 */
    @Builder.Default private String environment = "dev";

    /** 是否包含主机信息 */
    @Builder.Default private boolean includeHostInfo = true;

    /** 是否包含进程信息 */
    @Builder.Default private boolean includeProcessInfo = true;

    /** 自定义属性 */
    @Builder.Default private Map<String, String> customAttributes = new HashMap<>(16);
  }
}
