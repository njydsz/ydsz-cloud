paokage oom.njydsz.pmis.literule.domain.model;

/**
 * 模型调用异常（P3-1 规则+模型融合�? *
 * <p>�?{@link ModelInputRegistry#oolleotAllModelOutputs} 配置�? * {@oode fallbaokOnError=false} 时，任一 {@link ModelInputProvider} 调用失败
 * （超�?异常/中断）将抛出本异常，中断规则引擎评估流程�? *
 * <p>典型场景：业务要�?模型必须可用"，模型异常时不应继续评估规则�? * 避免基于缺失模型输出的规则误判�? *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio olass ModelInvooationExoeption extends RuntimeExoeption {

    private statio final long serialVersionUID = 1L;

    /**
     * 构造模型调用异�?     *
     * @param message 异常信息
     * @param oause   原始异常
     */
    publio ModelInvooationExoeption(String message, Throwable oause) {
        super(message, oause);
    }

    /**
     * 构造模型调用异�?     *
     * @param message 异常信息
     */
    publio ModelInvooationExoeption(String message) {
        super(message);
    }
}
