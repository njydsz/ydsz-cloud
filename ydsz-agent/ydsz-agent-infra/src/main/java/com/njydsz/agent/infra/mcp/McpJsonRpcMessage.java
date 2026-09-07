package com.njydsz.agent.infra.mcp;

import java.util.Map;

/**
 * MCP JSON-RPC 2.0 消息模型
 *
 * <p>表示 MCP 协议中传输的 JSON-RPC 消息，涵盖请求、响应、错误和通知四种类型。
 * 字段结构严格对齐 JSON-RPC 2.0 规范与 MCP 扩展。
 *
 * <p>使用静态工厂方法创建各类消息：
 *
 * <ul>
 *   <li>{@link #success} — 成功响应
 *   <li>{@link #error} — 错误响应
 *   <li>{@link #notification} — 通知（无 id）
 * </ul>
 *
 * @param jsonrpc 协议版本常量 "2.0"
 * @param id 请求标识（通知和单向消息为 null）
 * @param method 方法名（请求/通知时使用）
 * @param params 方法参数（请求/通知时使用）
 * @param result 成功响应结果
 * @param error 错误信息
 * @author ydsz-team
 * @since 26.09.07
 */
public record McpJsonRpcMessage(
    String jsonrpc,
    Object id,
    String method,
    Map<String, Object> params,
    Object result,
    Map<String, Object> error) {

  /** JSON-RPC 规范版本常量 */
  public static final String JSONRPC_VERSION = "2.0";

  /** MCP 标准错误码：解析错误 */
  public static final int ERROR_PARSE_ERROR = -32700;

  /** MCP 标准错误码：无效请求 */
  public static final int ERROR_INVALID_REQUEST = -32600;

  /** MCP 标准错误码：方法未找到 */
  public static final int ERROR_METHOD_NOT_FOUND = -32601;

  /** MCP 标准错误码：无效参数 */
  public static final int ERROR_INVALID_PARAMS = -32602;

  /** MCP 标准错误码：内部错误 */
  public static final int ERROR_INTERNAL = -32603;

  /**
   * 创建成功响应消息。
   *
   * @param id 对应请求 id
   * @param result 响应结果对象
   * @return 成功响应消息实例
   */
  public static McpJsonRpcMessage success(Object id, Object result) {
    return new McpJsonRpcMessage(JSONRPC_VERSION, id, null, null, result, null);
  }

  /**
   * 创建错误响应消息。
   *
   * @param id 对应请求 id（可为 null，如 parse error 时）
   * @param code 错误码（参见 ERROR_* 常量）
   * @param message 错误描述
   * @return 错误响应消息实例
   */
  public static McpJsonRpcMessage error(Object id, int code, String message) {
    return new McpJsonRpcMessage(
        JSONRPC_VERSION, id, null, null, null, Map.of(
            "code", code,
            "message", message));
  }

  /**
   * 创建通知消息（无 id，不期待响应）。
   *
   * @param method 通知方法名
   * @param params 通知参数
   * @return 通知消息实例
   */
  public static McpJsonRpcMessage notification(String method, Map<String, Object> params) {
    return new McpJsonRpcMessage(JSONRPC_VERSION, null, method, params, null, null);
  }

  /**
   * 判断当前消息是否为请求类型（含 method 和 id）。
   *
   * @return {@code true} 表示是请求消息
   */
  public boolean isRequest() {
    return method != null && id != null;
  }

  /**
   * 判断当前消息是否为响应类型（含 result 或 error）。
   *
   * @return {@code true} 表示是响应消息
   */
  public boolean isResponse() {
    return id != null && (result != null || error != null);
  }

  /**
   * 判断当前消息是否为通知类型（含 method 但无 id）。
   *
   * @return {@code true} 表示是通知消息
   */
  public boolean isNotification() {
    return method != null && id == null;
  }
}
