package com.njydsz.pmis.workflow.enums;

/**
 * 灰度发布状态
 *
 * <p>P3-1：与 pmis_flow_definition.canary_status 字段对应。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public enum CanaryStatus {

    /** 未启用灰度（默认） */
    NONE,

    /** 灰度中（canary_percent 在 1-99） */
    CANARYING,

    /** 已全量发布（canary_percent = 100，灰度版晋升为稳定版） */
    PROMOTED,

    /** 已回滚（canary_percent = 0，灰度版失效） */
    ROLLED_BACK
}
