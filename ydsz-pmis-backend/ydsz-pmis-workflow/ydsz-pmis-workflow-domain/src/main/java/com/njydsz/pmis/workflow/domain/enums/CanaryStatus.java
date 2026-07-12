package com.njydsz.pmis.workflow.domain.enums;

/**
 * 金丝雀（灰度）状态枚举
 *
 * @author ydsz-pmis-team
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