package com.njydsz.pmis.workflow.domain.enums;

/**
 * 金丝雀（灰度）策略枚举
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum CanaryStrategy {

    /** 用户哈希（按用户 ID 哈希分流） */
    USER_HASH,

    /** 随机分流 */
    RANDOM,

    /** 白名单 */
    WHITELIST,
}