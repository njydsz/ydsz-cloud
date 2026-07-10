package com.njydsz.pmis.workflow.enums.ai;

/**
 * 灰度切流策略
 *
 * <p>P3-1：与 pmis_flow_definition.canary_strategy 字段对应。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public enum CanaryStrategy {

    /**
     * 按发起人 ID hash 取模：相同发起人始终走同一版本
     * <p>适合"以人为粒度"的灰度，保证同一用户的使用体验一致。
     */
    USER_HASH,

    /**
     * 每次启动流程实例时随机切流
     * <p>适合流量比例精确切分场景。
     */
    RANDOM,

    /**
     * 白名单模式：仅白名单内发起人走灰度，其他走稳定版
     * <p>适合内部测试 / 重点用户预发验证。
     */
    WHITELIST
}
