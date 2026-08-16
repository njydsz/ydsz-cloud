package com.njydsz.agent.server.chat;

import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * 流式增量 PII 脱敏器
 *
 * <p>解决「先推 token、后脱敏」导致的流式输出 PII 泄漏问题：通过维护尾部缓冲，
 * 保证跨 chunk 断裂的 PII（如手机号 11 位、身份证 18 位）在缓冲内完整后再脱敏推送。
 * 脱敏为等长替换（星号占位），因此缓冲切分不会破坏已推送内容的长度语义。
 *
 * <p><b>线程安全</b>：单次流式调用单实例使用，不跨线程共享；实例不可复用，流结束后丢弃。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class StreamingPiiMasker {

    /** 尾部缓冲长度：需大于最长 PII（身份证 18 位）以覆盖跨 chunk 断裂 */
    private static final int TAIL_BUFFER_LENGTH = 24;

    /** 待脱敏尾部缓冲 */
    private final StringBuilder pending = new StringBuilder();

    /**
     * 对新增 delta 做增量脱敏。
     *
     * <p>缓冲不足时返回空字符串（内容暂留待后续拼接），流结束时需调用 {@link #flush()}
     * 冲刷剩余缓冲。
     *
     * @param delta 本次流式到达的增量内容
     * @return 可安全推送的脱敏头部内容；空字符串表示仍在缓冲中
     */
    public String mask(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        pending.append(delta);
        if (pending.length() <= TAIL_BUFFER_LENGTH) {
            return "";
        }
        int safeLength = pending.length() - TAIL_BUFFER_LENGTH;
        String safeHead = pending.substring(0, safeLength);
        pending.delete(0, safeLength);
        return SensitiveUtil.scanAndMask(safeHead);
    }

    /**
     * 冲刷剩余缓冲并脱敏，流结束时调用一次。
     *
     * @return 剩余缓冲的脱敏内容；无剩余时返回空字符串
     */
    public String flush() {
        if (pending.length() == 0) {
            return "";
        }
        String rest = pending.toString();
        pending.setLength(0);
        return SensitiveUtil.scanAndMask(rest);
    }
}
