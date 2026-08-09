package com.njydsz.workflow.domain.enums;

/**
 * 金丝雀（灰度）状态枚举
 *
 * <p>定义流程定义灰度发布生命周期中的状态流转，对标 Argo Rollouts 的 Rollout 状态机。
 * 状态在 {@code ydsz_flow_canary.status} 字段中持久化，由 {@code FlowCanaryService} 管理。
 *
 * <p><b>状态流转图：</b>
 * <pre>
 * NONE ──(publish)──→ CANARYING ──(promote)──→ PROMOTED
 *                         │
 *                      (rollback)
 *                         ↓
 *                     ROLLED_BACK
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum CanaryStatus {

    /** 未开启 */
    NONE,

    /** 灰度中 */
    CANARYING,

    /** 已全量 */
    PROMOTED,

    /** 已回滚 */
    ROLLED_BACK,
}
