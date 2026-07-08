package com.njydsz.pmis.agent.mcp.transport;

/**
 * MCP 传输层接口（P3-3 落地）。
 *
 * <p>抽象与 MCP 服务端通信的底层细节，支持 stdio / HTTP 两种传输方式。
 * 传输层负责发送/接收 JSON-RPC 消息，不解析消息语义。
 *
 * <p>生命周期：
 * <ol>
 *   <li>{@link #connect()} 建立连接</li>
 *   <li>{@link #send(String)} 发送请求 / {@link #receive()} 接收响应（可多次）</li>
 *   <li>{@link #close()} 关闭连接</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
public interface McpTransport extends AutoCloseable {

    /**
     * 建立连接（启动子进程 / 打开 HTTP 连接）。
     *
     * @throws Exception 连接失败
     */
    void connect() throws Exception;

    /**
     * 发送一行 JSON-RPC 消息。
     *
     * @param json JSON 字符串
     * @throws Exception 发送失败
     */
    void send(String json) throws Exception;

    /**
     * 接收一行 JSON-RPC 响应（阻塞直到收到响应或超时）。
     *
     * @return JSON 字符串
     * @throws Exception 接收失败或超时
     */
    String receive() throws Exception;

    /**
     * 是否已连接。
     *
     * @return true 表示连接已建立
     */
    boolean isConnected();

    /**
     * 关闭连接，释放资源。
     */
    @Override
    void close();
}
