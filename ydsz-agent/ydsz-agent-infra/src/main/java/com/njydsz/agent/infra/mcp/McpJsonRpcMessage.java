package com.njydsz.agent.infra.mcp;

/**
 * MCP JSON-RPC 2.0 消息模型（已废弃，兼容性别名）。
 *
 * <p><b>已废弃（26.11.06）</b>：协议契约已下沉至
 * {@link com.njydsz.agent.domain.mcp.McpJsonRpcMessage}（domain 层），
 * 原因是 web 层 Controller 直接引用 infra 包类型，违反 DDD 分层
 * （web → server → domain，不应直接依赖 infra 实现包）。
 *
 * <p>新代码请直接使用 domain 版本；本类保留仅为兼容存量引用，将在下个主版本移除。
 *
 * @author ydsz-team
 * @since 26.09.07
 * @deprecated 使用 {@link com.njydsz.agent.domain.mcp.McpJsonRpcMessage}
 */
@Deprecated(since = "26.11.06", forRemoval = true)
public record McpJsonRpcMessage(
    String jsonrpc,
    Object id,
    String method,
    java.util.Map<String, Object> params,
    Object result,
    java.util.Map<String, Object> error) {

  /** JSON-RPC 规范版本常量 */
  public static final String JSONRPC_VERSION =
      com.njydsz.agent.domain.mcp.McpJsonRpcMessage.JSONRPC_VERSION;

  /** MCP 标准错误码：解析错误 */
  public static final int ERROR_PARSE_ERROR =
      com.njydsz.agent.domain.mcp.McpJsonRpcMessage.ERROR_PARSE_ERROR;

  /** MCP 标准错误码：无效请求 */
  public static final int ERROR_INVALID_REQUEST =
      com.njydsz.agent.domain.mcp.McpJsonRpcMessage.ERROR_INVALID_REQUEST;

  /** MCP 标准错误码：方法未找到 */
  public static final int ERROR_METHOD_NOT_FOUND =
      com.njydsz.agent.domain.mcp.McpJsonRpcMessage.ERROR_METHOD_NOT_FOUND;

  /** MCP 标准错误码：无效参数 */
  public static final int ERROR_INVALID_PARAMS =
      com.njydsz.agent.domain.mcp.McpJsonRpcMessage.ERROR_INVALID_PARAMS;

  /** MCP 标准错误码：内部错误 */
  public static final int ERROR_INTERNAL =
      com.njydsz.agent.domain.mcp.McpJsonRpcMessage.ERROR_INTERNAL;

  /**
   * 创建成功响应消息。
   *
   * @param id 对应请求 id
   * @param result 响应结果对象
   * @return 成功响应消息实例
   */
  public static McpJsonRpcMessage success(Object id, Object result) {
    return new McpJsonRpcMessage(
        JSONRPC_VERSION, id, null, null, result, null);
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
        JSONRPC_VERSION, id, null, null, null, java.util.Map.of("code", code, "message", message));
  }

  /**
   * 创建通知消息（无 id，不期待响应）。
   *
   * @param method 通知方法名
   * @param params 通知参数
   * @return 通知消息实例
   */
  public static McpJsonRpcMessage notification(String method, java.util.Map<String, Object> params) {
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
