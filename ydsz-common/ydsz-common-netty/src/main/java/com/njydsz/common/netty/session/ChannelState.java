package com.njydsz.common.netty.session;

/**
 * Channel 生命周期状态枚举。
 *
 * <p>定义连接从建立到关闭的完整状态流转：
 *
 * <pre>
 * CONNECTING → CONNECTED → AUTHENTICATING → AUTHENTICATED → DRAINING → CLOSED
 *                      ↘ CLOSED（认证失败/异常断开）
 * </pre>
 *
 * <p>各状态含义：
 *
 * <ul>
 *   <li>{@link #CONNECTING} — TCP 三次握手完成，Channel 已注册到 EventLoop，等待初始交互
 *   <li>{@link #CONNECTED} — Channel 已建立，等待认证请求
 *   <li>{@link #AUTHENTICATING} — 正在执行认证逻辑（如：已收到认证消息，正在校验）
 *   <li>{@link #AUTHENTICATED} — 认证通过，可正常收发业务消息
 *   <li>{@link #DRAINING} — 引流关闭中，不再接受新请求，等待在途消息处理完成
 *   <li>{@link #CLOSED} — 连接已关闭，底层 TCP 连接断开
 * </ul>
 *
 * <p>状态流转是单向的（CLOSED 除外，任何状态都可能直接流转到 CLOSED）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum ChannelState {

  /** TCP 连接建立，等待初始交互 */
  CONNECTING,

  /** 连接就绪，等待认证 */
  CONNECTED,

  /** 正在执行认证 */
  AUTHENTICATING,

  /** 认证通过，可正常收发业务消息 */
  AUTHENTICATED,

  /** 引流关闭中，不再接受新请求 */
  DRAINING,

  /** 连接已关闭 */
  CLOSED
}
