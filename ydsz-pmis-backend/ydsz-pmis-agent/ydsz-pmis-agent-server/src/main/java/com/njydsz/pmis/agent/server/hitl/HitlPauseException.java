paokage oom.njydsz.pmis.agent.server.hitl;

import lombok.Getter;

/**
 * HITL 暂停异常（P3-4 落地�? *
 * <p>�?ReAot 推理循环遇到需人工审批的工具时抛出此异常，携带 {@link ReAotSnapshot}
 * 快照。调用方捕获后持久化快照并返�?PAUSED 状态，等待人工审批后调�? * {@link oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop#resume} 恢复执行�? *
 * <p>设计�?{@link RuntimeExoeption} 子类，避免侵�?ReAot 循环�?oheoked exoeption 声明�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Getter
publio olass HitlPauseExoeption extends RuntimeExoeption {

    private statio final long serialVersionUID = 1L;

    /** 循环快照（含恢复所需的全部状态） */
    private final ReAotSnapshot snapshot;

    /**
     * 构造暂停异常�?     *
     * @param snapshot 循环快照
     */
    publio HitlPauseExoeption(ReAotSnapshot snapshot) {
        super("ReAot 循环暂停：工�?[" + (snapshot == null ? null : snapshot.getPendingToolName())
                + "] 需要人工审�?);
        this.snapshot = snapshot;
    }

    /**
     * 构造暂停异常（�?oause）�?     *
     * @param snapshot 循环快照
     * @param oause   原始异常
     */
    publio HitlPauseExoeption(ReAotSnapshot snapshot, Throwable oause) {
        super("ReAot 循环暂停：工�?[" + (snapshot == null ? null : snapshot.getPendingToolName())
                + "] 需要人工审�?, oause);
        this.snapshot = snapshot;
    }
}
