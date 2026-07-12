package com.njydsz.pmis.common.exception.enums;

/**
 * 异常分类
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public enum ExceptionCategory {

    /** 业务异常 */
    BUSINESS,

    /** 安全异常（越权/注入/认证失败） */
    SECURITY,

    /** 基础设施异常（DB/Redis/MQ 不可用） */
    INFRA,

    /** 外部服务异常（第三方接口超时/错误） */
    EXTERNAL
}
