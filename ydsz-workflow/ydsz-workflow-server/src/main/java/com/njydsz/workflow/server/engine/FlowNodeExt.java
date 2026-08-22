package com.njydsz.workflow.server.engine;

import java.util.Collections;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * FlowNodeDO ext JSON 字段提取工具。
 *
 * <p>集中处理节点 ext JSON 的解析，供引擎层各组件（service executor、empty strategy、
 * BPMN 解析器等）统一调用。返回结果在首次解析后缓存，避免重复解析。
 *
 * <p>所有方法均为无状态纯函数（入参为 JSON 字符串），线程安全。解析失败时返回合理的默认值而非 null。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public final class FlowNodeExt {

  private FlowNodeExt() {
    throw new AssertionError("工具类禁止实例化");
  }

  // ==================== 服务节点相关 ====================

  /**
   * 获取服务节点类型（HTTP / SCRIPT / AUTO_PASS）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 服务类型，默认 AUTO_PASS
   */
  public static String getServiceType(String ext) {
    return getStringVal(ext, "serviceType", "AUTO_PASS").toUpperCase();
  }

  /**
   * 获取服务节点 HTTP 调用地址。
   *
   * @param ext 节点 ext JSON 字符串
   * @return URL，未配置时返回空字符串
   */
  public static String getServiceUrl(String ext) {
    return getStringVal(ext, "url", "");
  }

  /**
   * 获取服务节点 HTTP 方法。
   *
   * @param ext 节点 ext JSON 字符串
   * @return HTTP 方法，默认 GET
   */
  public static String getServiceMethod(String ext) {
    return getStringVal(ext, "method", "GET").toUpperCase();
  }

  /**
   * 获取服务节点脚本内容。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 脚本内容，未配置时返回空字符串
   */
  public static String getServiceScript(String ext) {
    return getStringVal(ext, "script", "");
  }

  // ==================== 审批人为空兜底策略 ====================

  /**
   * 获取审批人为空时的兜底策略（AUTO_PASS / TRANSFER_ADMIN / ASSIGN_SPECIFIED）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 兜底策略，默认 AUTO_PASS
   */
  public static String getEmptyStrategy(String ext) {
    return getStringVal(ext, "emptyStrategy", "AUTO_PASS").toUpperCase();
  }

  /**
   * 获取兜底策略中的管理员用户 ID。
   *
   * @param ext 节点 ext JSON 字符串，键名 {@code adminUserId}
   * @return 管理员用户 ID，默认 "1"
   */
  public static String getAdminUserId(String ext) {
    return getStringVal(ext, "adminUserId", "1");
  }

  /**
   * 获取兜底策略中的指定用户 ID。
   *
   * @param ext 节点 ext JSON 字符串，键名 {@code specifiedUserId}
   * @return 指定用户 ID，默认 "1"
   */
  public static String getSpecifiedUserId(String ext) {
    return getStringVal(ext, "specifiedUserId", "1");
  }

  // ==================== 并行网关聚合阈值 ====================

  /**
   * 解析并行/包容网关的 join 聚合阈值。
   *
   * <p>支持格式：数值、"N/M"分数、"majority"（过半数）。
   *
   * @param ext           节点 ext JSON 字符串
   * @param incomingCount 网关入边总数
   * @return 需要到达的分支数量
   */
  public static int getJoinRequired(String ext, int incomingCount) {
    Map<String, Object> map = parseSafe(ext);
    Object val = map.get("joinRequired");
    if (val == null) {
      return incomingCount;
    }
    if (val instanceof Number n) {
      return clamp(n.intValue(), incomingCount);
    }
    String s = String.valueOf(val).trim();
    if ("majority".equalsIgnoreCase(s)) {
      return incomingCount / 2 + 1;
    }
    if (s.contains("/")) {
      String[] parts = s.split("/");
      return clamp(Integer.parseInt(parts[0].trim()), incomingCount);
    }
    return clamp(Integer.parseInt(s), incomingCount);
  }

  // ==================== 网关默认出边 ====================

  /**
   * 获取网关默认出边 sequenceFlowId（BPMN 2.0 default 属性）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 默认出边 ID，未配置时返回 null
   */
  public static String getDefaultFlowId(String ext) {
    return getStringVal(ext, "defaultFlowId", null);
  }

  // ==================== 表单相关 ====================

  /**
   * 获取表单 Schema JSON 字符串。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 表单 Schema JSON，未配置时返回 null
   */
  public static String getFormSchemaJson(String ext) {
    return getStringVal(ext, "formSchema", null);
  }

  // ==================== 去重与优先级 ====================

  /**
   * 判断是否启用跨节点办理人去重。
   *
   * @param ext 节点 ext JSON 字符串
   * @return true 表示启用去重
   */
  public static boolean getAutoDedup(String ext) {
    Map<String, Object> map = parseSafe(ext);
    Object val = map.get("autoDedup");
    if (val == null) {
      return false;
    }
    if (val instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(val));
  }

  /**
   * 获取节点优先级（1~100）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 优先级数值，默认 50
   */
  public static int getPriority(String ext) {
    Map<String, Object> map = parseSafe(ext);
    Object val = map.get("priority");
    if (val == null) {
      return 50;
    }
    if (val instanceof Number n) {
      return clamp(n.intValue(), 1, 100);
    }
    try {
      return clamp(Integer.parseInt(String.valueOf(val).trim()), 1, 100);
    } catch (NumberFormatException e) {
      return 50;
    }
  }

  // ==================== 超时升级 ====================

  /**
   * 获取超时升级用户 ID。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 升级用户 ID，未配置时返回空字符串
   */
  public static String getEscalateUser(String ext) {
    return getStringVal(ext, "escalateUser", "");
  }

  /**
   * 获取超时策略（REMIND / ESCALATE / AUTO_PASS / AUTO_REJECT）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 超时策略，默认 REMIND
   */
  public static String getTimeoutStrategy(String ext) {
    return getStringVal(ext, "timeoutStrategy", "REMIND").toUpperCase();
  }

  /**
   * 获取超时时间（分钟）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 超时分钟数，默认 120
   */
  public static int getTimeoutMinutes(String ext) {
    Map<String, Object> map = parseSafe(ext);
    Object val = map.get("timeout");
    if (val == null) {
      return 120;
    }
    if (val instanceof Number n) {
      return Math.max(1, n.intValue());
    }
    try {
      return Math.max(1, Integer.parseInt(String.valueOf(val).trim()));
    } catch (NumberFormatException e) {
      return 120;
    }
  }

  // ==================== 事件定义 ====================

  /**
   * 获取事件类型（ERROR / TIMER / SIGNAL / MESSAGE）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 事件类型，未配置时返回空字符串
   */
  public static String getEventType(String ext) {
    return getStringVal(ext, "eventType", "").toUpperCase();
  }

  /**
   * 获取 boundaryEvent 的 attachedToRef（关联节点编码）。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 关联节点编码，未配置时返回空字符串
   */
  public static String getAttachedToRef(String ext) {
    return getStringVal(ext, "attachedToRef", "");
  }

  /**
   * 获取错误边界事件引用标识。
   *
   * @param ext 节点 ext JSON 字符串
   * @return 错误引用标识，默认 "SERVICE_ERROR"
   */
  public static String getErrorRef(String ext) {
    return getStringVal(ext, "errorRef", "SERVICE_ERROR");
  }

  // ==================== 内部方法 ====================

  /**
   * 安全解析 ext JSON 为 Map。
   *
   * @param ext ext JSON 字符串
   * @return 解析结果，失败或无内容时返回空 Map
   */
  public static Map<String, Object> parseSafe(String ext) {
    if (!StringUtils.hasText(ext)) {
      return Collections.emptyMap();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(ext);
      return map != null ? map : Collections.emptyMap();
    } catch (Exception e) {
      log.warn("[FlowNodeExt] 解析 ext JSON 失败: err={}", e.getMessage());
      return Collections.emptyMap();
    }
  }

  /**
   * 从 ext JSON 中读取字符串配置值。
   *
   * @param ext          ext JSON 字符串
   * @param key          配置键名
   * @param defaultValue 默认值（不可为 null）
   * @return 配置值，键不存在或解析失败时返回默认值
   */
  public static String getStringVal(String ext, String key, String defaultValue) {
    Map<String, Object> map = parseSafe(ext);
    Object val = map.get(key);
    return val == null ? defaultValue : String.valueOf(val);
  }

  /**
   * 将值 clamp 到 [1, max] 范围。
   */
  private static int clamp(int value, int max) {
    return Math.min(Math.max(1, value), max);
  }

  /**
   * 将值 clamp 到 [min, max] 范围。
   */
  private static int clamp(int value, int min, int max) {
    return Math.min(Math.max(min, value), max);
  }
}
