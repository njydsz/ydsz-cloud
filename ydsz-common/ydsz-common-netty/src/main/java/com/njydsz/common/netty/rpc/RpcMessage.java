package com.njydsz.common.netty.rpc;

import lombok.Data;

/**
 * RPC 协议消息封装 — 为 Request/Response 添加协议头（requestId + messageType）。
 *
 * <p>协议设计：
 *
 * <ul>
 *   <li>requestId：{@code long} 类型，唯一标识一次 RPC 调用，Response 必须回带
 *   <li>messageType：{@code byte} 类型，区分 REQUEST(0x01) / RESPONSE(0x02) / EXCEPTION(0x03)
 *   <li>body：序列化的业务对象（JSON / Protobuf / 自定义）
 * </ul>
 *
 * <p><b>此对象设计为可池化的</b>，在 NettyRpcClient 内部复用，减少 GC 压力。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RpcResponseHandler
 * @see NettyRpcClient
 */
@Data
public class RpcMessage {

  /** 消息类型：请求 */
  public static final byte TYPE_REQUEST = 0x01;

  /** 消息类型：正常响应 */
  public static final byte TYPE_RESPONSE = 0x02;

  /** 消息类型：异常响应 */
  public static final byte TYPE_EXCEPTION = 0x03;

  /** 请求 ID（request/request 配对标识） */
  private long requestId;

  /** 消息类型（TYPE_REQUEST / TYPE_RESPONSE / TYPE_EXCEPTION） */
  private byte messageType;

  /** 业务载荷（序列化后的字节数组或业务对象引用，取决于 Pipeline 中的编解码器实现） */
  private Object body;

  /** 错误码（TYPE_EXCEPTION 时有效） */
  private String errorCode;

  /** 错误消息（TYPE_EXCEPTION 时有效） */
  private String errorMessage;

  /**
   * 创建请求消息。
   *
   * @param requestId 请求 ID
   * @param body 业务对象
   * @return RpcMessage 实例
   */
  public static RpcMessage request(long requestId, Object body) {
    RpcMessage msg = new RpcMessage();
    msg.setRequestId(requestId);
    msg.setMessageType(TYPE_REQUEST);
    msg.setBody(body);
    return msg;
  }

  /**
   * 创建正常响应消息。
   *
   * @param requestId 对应的请求 ID
   * @param body 响应业务对象
   * @return RpcMessage 实例
   */
  public static RpcMessage response(long requestId, Object body) {
    RpcMessage msg = new RpcMessage();
    msg.setRequestId(requestId);
    msg.setMessageType(TYPE_RESPONSE);
    msg.setBody(body);
    return msg;
  }

  /**
   * 创建异常响应消息。
   *
   * @param requestId 对应的请求 ID
   * @param errorCode 错误码
   * @param errorMessage 错误描述
   * @return RpcMessage 实例
   */
  public static RpcMessage exception(long requestId, String errorCode, String errorMessage) {
    RpcMessage msg = new RpcMessage();
    msg.setRequestId(requestId);
    msg.setMessageType(TYPE_EXCEPTION);
    msg.setErrorCode(errorCode);
    msg.setErrorMessage(errorMessage);
    return msg;
  }

  /**
   * 判断是否为请求消息。
   *
   * @return true 表示请求
   */
  public boolean isRequest() {
    return messageType == TYPE_REQUEST;
  }

  /**
   * 判断是否为响应消息。
   *
   * @return true 表示正常响应
   */
  public boolean isResponse() {
    return messageType == TYPE_RESPONSE;
  }

  /**
   * 判断是否为异常响应。
   *
   * @return true 表示异常响应
   */
  public boolean isException() {
    return messageType == TYPE_EXCEPTION;
  }
}
