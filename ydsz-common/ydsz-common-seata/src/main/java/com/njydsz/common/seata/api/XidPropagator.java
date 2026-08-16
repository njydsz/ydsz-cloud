package com.njydsz.common.seata.api;

/**
 * 分布式事务 XID 传播器接口
 *
 * <p>用于跨进程/跨服务传递全局事务 XID，使下游服务自动加入全局事务。
 *
 * <p><b>P0-6 修复</b>：此前 XID 仅通过 ThreadLocal 存储，无法跨 RPC/MQ 传递。 现在通过此接口统一管理 XID 在 HTTP Header / MQ
 * Header 中的序列化和反序列化。
 *
 * <p>使用方式：
 *
 * <ul>
 *   <li><b>上游服务</b>（Feign 拦截器）：{@link #serialize(String)} 将 XID 写入请求头
 *   <li><b>下游服务</b>（Servlet 过滤器）：{@link #deserialize(String)} 从请求头解析 XID， {@link #bind(String)}
 *       绑定到当前线程，{@link #unbind()} 在请求结束时清除
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface XidPropagator {

  /** HTTP 请求头中的 XID 键名 */
  String XID_HEADER = "Seata-XID";

  /** MQ 消息属性中的 XID 键名 */
  String XID_MQ_PROPERTY = "seata-xid";

  /**
   * 将 XID 序列化为传输格式
   *
   * @param xid 全局事务 ID
   * @return 序列化后的字符串，若 xid 为 null 返回 null
   */
  String serialize(String xid);

  /**
   * 从传输格式反序列化 XID
   *
   * @param header 传输内容
   * @return 解析出的 XID，无效时返回 null
   */
  String deserialize(String header);

  /**
   * 将 XID 绑定到当前线程
   *
   * @param xid 全局事务 ID
   */
  void bind(String xid);

  /**
   * 从当前线程获取 XID
   *
   * @return 当前线程的 XID，无事务上下文时返回 null
   */
  String currentXid();

  /** 清除当前线程的 XID 绑定 */
  void unbind();
}
