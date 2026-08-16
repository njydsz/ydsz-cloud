package com.njydsz.common.sentry.tracing.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Map;

/**
 * OTel 语义约定常量（对标 OTel Semantic Conventions 1.x）
 *
 * <p>定义 YDSZ 框架约定的标准 Span Attribute / Resource Attribute 名称， 避免业务方硬编码字符串，便于跨服务、跨语言统一查询。
 *
 * <p>参考：
 *
 * <ul>
 *   <li><a href="https://opentelemetry.io/docs/specs/semconv/">OTel Semantic Conventions</a>
 *   <li><a
 *       href="https://github.com/open-telemetry/semantic-conventions">open-telemetry/semantic-conventions</a>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class OtelSemConv {

  private OtelSemConv() {
    throw new UnsupportedOperationException("OtelSemConv is a utility class");
  }

  // ============================================================================
  // 通用属性
  // ============================================================================

  /** 服务名 */
  public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

  /** 服务版本 */
  public static final AttributeKey<String> SERVICE_VERSION =
      AttributeKey.stringKey("service.version");

  /** 服务命名空间（业务域） */
  public static final AttributeKey<String> SERVICE_NAMESPACE =
      AttributeKey.stringKey("service.namespace");

  /** 服务实例 ID */
  public static final AttributeKey<String> SERVICE_INSTANCE_ID =
      AttributeKey.stringKey("service.instance.id");

  /** 部署环境 */
  public static final AttributeKey<String> DEPLOYMENT_ENVIRONMENT =
      AttributeKey.stringKey("deployment.environment");

  // ============================================================================
  // HTTP 属性
  // ============================================================================

  /** HTTP 请求方法 */
  public static final AttributeKey<String> HTTP_REQUEST_METHOD =
      AttributeKey.stringKey("http.request.method");

  /** HTTP 路由模板 */
  public static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");

  /** HTTP 响应状态码 */
  public static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE =
      AttributeKey.longKey("http.response.status_code");

  /** HTTP 请求 URL */
  public static final AttributeKey<String> HTTP_URL = AttributeKey.stringKey("url.full");

  /** HTTP 用户代理 */
  public static final AttributeKey<String> USER_AGENT_ORIGINAL =
      AttributeKey.stringKey("user_agent.original");

  /** HTTP 客户端 IP */
  public static final AttributeKey<String> CLIENT_ADDRESS =
      AttributeKey.stringKey("client.address");

  // ============================================================================
  // 数据库属性
  // ============================================================================

  /** DB 系统 */
  public static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");

  /** SQL 语句 */
  public static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");

  /** 操作名 */
  public static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");

  /** 影响行数 */
  public static final AttributeKey<Long> DB_RESPONSE_ROWS =
      AttributeKey.longKey("db.response.rows");

  // ============================================================================
  // 消息队列属性
  // ============================================================================

  /** 消息系统 */
  public static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

  /** 目标主题 */
  public static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");

  // ============================================================================
  // YDSZ 自定义属性
  // ============================================================================

  /** 租户 ID */
  public static final AttributeKey<String> REMI_TENANT_ID =
      AttributeKey.stringKey("ydsz.tenant.id");

  /** 业务单号 */
  public static final AttributeKey<String> REMI_BUSINESS_NO =
      AttributeKey.stringKey("ydsz.business_no");

  /** 用户 ID */
  public static final AttributeKey<String> REMI_USER_ID = AttributeKey.stringKey("ydsz.user.id");

  /** 客户端类型（web/app/inner） */
  public static final AttributeKey<String> REMI_CLIENT_TYPE =
      AttributeKey.stringKey("ydsz.client.type");

  /** 错误码 */
  public static final AttributeKey<String> REMI_ERROR_CODE =
      AttributeKey.stringKey("ydsz.error.code");

  /** 业务模块 */
  public static final AttributeKey<String> REMI_MODULE = AttributeKey.stringKey("ydsz.module");

  /** 业务动作 */
  public static final AttributeKey<String> REMI_ACTION = AttributeKey.stringKey("ydsz.action");

  /** 灰度标签（用于按流量染色查询） */
  public static final AttributeKey<String> REMI_GRAY_TAG = AttributeKey.stringKey("ydsz.gray.tag");

  /** 压测标记 */
  public static final AttributeKey<String> REMI_PRESSURE_TAG =
      AttributeKey.stringKey("ydsz.pressure.tag");

  // ============================================================================
  // Span 名称生成
  // ============================================================================

  /**
   * 生成 Span 名称：{@code <namespace>.<operation>}
   *
   * @param namespace 命名空间（模块名/服务名）
   * @param operation 操作名
   * @return Span 名称
   */
  public static String spanName(String namespace, String operation) {
    if (namespace == null || namespace.isEmpty()) {
      return operation == null ? "unknown" : operation;
    }
    if (operation == null || operation.isEmpty()) {
      return namespace;
    }
    return namespace + "." + operation;
  }

  /**
   * 将 Map 转换为 OTel Attributes
   *
   * @param map 属性键值对
   * @return OTel Attributes
   */
  public static Attributes toAttributes(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      Object v = entry.getValue();
      if (v instanceof String s) {
        builder.put(entry.getKey(), s);
      } else if (v instanceof Long l) {
        builder.put(entry.getKey(), l);
      } else if (v instanceof Integer i) {
        builder.put(entry.getKey(), i.longValue());
      } else if (v instanceof Double d) {
        builder.put(entry.getKey(), d);
      } else if (v instanceof Float f) {
        builder.put(entry.getKey(), f.doubleValue());
      } else if (v instanceof Boolean b) {
        builder.put(entry.getKey(), b);
      } else {
        builder.put(entry.getKey(), v.toString());
      }
    }
    return builder.build();
  }
}
