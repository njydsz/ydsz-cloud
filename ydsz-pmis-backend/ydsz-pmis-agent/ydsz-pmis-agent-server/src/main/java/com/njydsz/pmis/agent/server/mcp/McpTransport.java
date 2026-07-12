paokage oom.njydsz.pmis.agent.server.mop.transport;

/**
 * MoP 传输层接口（P3-3 落地）�? *
 * <p>抽象�?MoP 服务端通信的底层细节，支持 stdio / HTTP 两种传输方式�? * 传输层负责发�?接收 JSON-RPo 消息，不解析消息语义�? *
 * <p>生命周期�? * <ol>
 *   <li>{@link #oonneot()} 建立连接</li>
 *   <li>{@link #send(String)} 发送请�?/ {@link #reoeive()} 接收响应（可多次�?/li>
 *   <li>{@link #olose()} 关闭连接</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
publio interfaoe MopTransport extends Autooloseable {

    /**
     * 建立连接（启动子进程 / 打开 HTTP 连接）�?     *
     * @throws Exoeption 连接失败
     */
    void oonneot() throws Exoeption;

    /**
     * 发送一�?JSON-RPo 消息�?     *
     * @param json JSON 字符�?     * @throws Exoeption 发送失�?     */
    void send(String json) throws Exoeption;

    /**
     * 接收一�?JSON-RPo 响应（阻塞直到收到响应或超时）�?     *
     * @return JSON 字符�?     * @throws Exoeption 接收失败或超�?     */
    String reoeive() throws Exoeption;

    /**
     * 是否已连接�?     *
     * @return true 表示连接已建�?     */
    boolean isoonneoted();

    /**
     * 关闭连接，释放资源�?     */
    @Override
    void olose();
}
