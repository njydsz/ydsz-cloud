package com.njydsz.pmis.agent.hitl;

import lombok.Getter;

/**
 * HITL 暂停异常（P3-4 落地）
 *
 * <p>当 ReAct 推理循环遇到需人工审批的工具时抛出此异常，携带 {@link ReActSnapshot}
 * 快照。调用方捕获后持久化快照并返回 PAUSED 状态，等待人工审批后调用
 * {@link com.njydsz.pmis.agent.engine.react.ReActLoop#resume} 恢复执行。
 *
 * <p>设计为 {@link RuntimeException} 子类，避免侵入 ReAct 循环的 checked exception 声明。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Getter
public class HitlPauseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 循环快照（含恢复所需的全部状态） */
    private final ReActSnapshot snapshot;

    /**
     * 构造暂停异常。
     *
     * @param snapshot 循环快照
     */
    public HitlPauseException(ReActSnapshot snapshot) {
        super("ReAct 循环暂停：工具 [" + (snapshot == null ? null : snapshot.getPendingToolName())
                + "] 需要人工审批");
        this.snapshot = snapshot;
    }

    /**
     * 构造暂停异常（带 cause）。
     *
     * @param snapshot 循环快照
     * @param cause   原始异常
     */
    public HitlPauseException(ReActSnapshot snapshot, Throwable cause) {
        super("ReAct 循环暂停：工具 [" + (snapshot == null ? null : snapshot.getPendingToolName())
                + "] 需要人工审批", cause);
        this.snapshot = snapshot;
    }
}
