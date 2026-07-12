package com.njydsz.pmis.common.exception.enums;

/**
 * 异常级别
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public enum ExceptionLevel {

    /** 信息级（记录但不告警） */
    INFO,

    /** 警告级（记录并告警） */
    WARN,

    /** 错误级（记录、告警、影响业务） */
    ERROR,

    /** 致命级（记录、告警、阻断业务、需立即处理） */
    FATAL,

    /** 灾难级（基础设施不可用、熔断触发、需紧急处理） */
    CRITICAL
}
