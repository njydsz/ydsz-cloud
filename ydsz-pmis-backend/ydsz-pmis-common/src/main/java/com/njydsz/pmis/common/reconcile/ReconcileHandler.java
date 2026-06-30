package com.njydsz.pmis.common.reconcile;

/**
 * 对账处理器接口
 *
 * <p>业务模块实现该接口，注册到 ReconcileEngine 中执行对账。
 * 对账逻辑通常是"读源 → 读目标 → 比对 → 修复"。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ReconcileHandler {

    /**
     * 对账项编码
     */
    String code();

    /**
     * 对账项名称
     */
    String name();

    /**
     * 是否支持自动修复
     */
    default boolean autoFixable() {
        return false;
    }

    /**
     * 执行对账
     *
     * @return 对账结果
     */
    ReconcileResult reconcile();
}
