package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;

/**
 * 服务节点配置值对象。
 *
 * <p>封装节点 ext JSON 中服务节点（{@link com.njydsz.workflow.domain.enums.FlowNodeType#SERVICE}）
 * 的执行配置，提供类型安全的访问方式。替代 {@link FlowNodeVO#getServiceType()}、
 * {@link FlowNodeVO#getServiceUrl()}、{@link FlowNodeVO#getServiceMethod()}、
 * {@link FlowNodeVO#getServiceScript()} 等弱类型 getter。
 *
 * <p><b>服务类型：</b>
 *
 * <ul>
 *   <li>{@link ServiceType#HTTP} — HTTP 调用（需配置 url / method）
 *   <li>{@link ServiceType#SCRIPT} — 脚本执行（需配置 script）
 *   <li>{@link ServiceType#AUTO_PASS} — 自动通过（默认，无需额外配置）
 * </ul>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象（所有字段 final）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class ServiceNodeConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认服务类型（AUTO_PASS 自动通过） */
  public static final ServiceType DEFAULT_TYPE = ServiceType.AUTO_PASS;

  /** 默认 HTTP 方法（GET） */
  public static final String DEFAULT_METHOD = "GET";

  /** 服务类型 */
  private final ServiceType serviceType;

  /** HTTP 调用地址（HTTP 类型时有效） */
  private final String url;

  /** HTTP 方法（HTTP 类型时有效） */
  private final String method;

  /** 脚本内容（SCRIPT 类型时有效） */
  private final String script;

  private ServiceNodeConfig(ServiceType serviceType, String url, String method, String script) {
    this.serviceType = serviceType != null ? serviceType : DEFAULT_TYPE;
    this.url = url != null ? url : "";
    this.method = method != null ? method.toUpperCase() : DEFAULT_METHOD;
    this.script = script != null ? script : "";
  }

  /**
   * 从 ext JSON Map 解析服务节点配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return 服务节点配置值对象（不可变）
   */
  public static ServiceNodeConfig fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new ServiceNodeConfig(DEFAULT_TYPE, "", DEFAULT_METHOD, "");
    }
    ServiceType type = parseServiceType(extMap.get("serviceType"));
    String url = parseStringSafe(extMap.get("url"));
    String method = parseStringSafe(extMap.get("method"));
    String script = parseStringSafe(extMap.get("script"));
    return new ServiceNodeConfig(type, url, method, script);
  }

  /**
   * 从 ext JSON 字符串解析服务节点配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return 服务节点配置值对象（不可变）
   */
  public static ServiceNodeConfig fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new ServiceNodeConfig(DEFAULT_TYPE, "", DEFAULT_METHOD, "");
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new ServiceNodeConfig(DEFAULT_TYPE, "", DEFAULT_METHOD, "");
    }
  }

  /**
   * 是否为 HTTP 调用类型。
   *
   * @return true-HTTP 调用；false-其他类型
   */
  public boolean isHttpType() {
    return serviceType == ServiceType.HTTP;
  }

  /**
   * 是否为脚本执行类型。
   *
   * @return true-脚本执行；false-其他类型
   */
  public boolean isScriptType() {
    return serviceType == ServiceType.SCRIPT;
  }

  /**
   * 是否为自动通过类型。
   *
   * @return true-自动通过；false-其他类型
   */
  public boolean isAutoPassType() {
    return serviceType == ServiceType.AUTO_PASS;
  }

  /**
   * 是否需要调用外部服务（HTTP 或 SCRIPT 类型）。
   *
   * @return true-需要调用外部服务；false-自动通过
   */
  public boolean requiresExternalCall() {
    return serviceType == ServiceType.HTTP || serviceType == ServiceType.SCRIPT;
  }

  /**
   * 服务类型枚举。
   *
   * <p>定义服务节点的执行方式。
   */
  public enum ServiceType {
    /** HTTP 调用 */
    HTTP,
    /** 脚本执行 */
    SCRIPT,
    /** 自动通过 */
    AUTO_PASS
  }

  // ==================== 内部工具方法 ====================

  private static String parseStringSafe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static ServiceType parseServiceType(Object value) {
    if (value == null) {
      return DEFAULT_TYPE;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return ServiceType.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_TYPE;
    }
  }
}
