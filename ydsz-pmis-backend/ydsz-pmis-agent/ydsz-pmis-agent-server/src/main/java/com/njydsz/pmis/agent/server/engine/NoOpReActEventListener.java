package com.njydsz.pmis.agent.server.engine.stream;

/**
 * 空实现的 ReAct 事件监听器（P2-1 落地）
 *
 * <p>用作 {@link com.njydsz.pmis.agent.server.engine.react.ReActLoop#runStream} 的默认参数，
 * 也可作为基类被部分覆盖。所有方法都是空实现，且 try-catch 住所有异常避免影响主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
public class NoOpReActEventListener implements ReActEventListener {

    /** 单例实例（无状态，可共享） */
    private static final NoOpReActEventListener INSTANCE = new NoOpReActEventListener();

    /** 获取单例 */
    public static NoOpReActEventListener getInstance() {
        return INSTANCE;
    }

    /** 私有构造 */
    private NoOpReActEventListener() {
    }
}
