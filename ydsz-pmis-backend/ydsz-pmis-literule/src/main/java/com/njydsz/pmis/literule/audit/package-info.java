/**
 * 规则审计日志服务
 *
 * <p>记录规则全生命周期操作的审计日志，支持 {@code who + when + what + before/after} 的完整审计链路。
 * 通过 {@link com.njydsz.pmis.literule.audit.RuleAuditLogService.AuditLogStore} SPI 接口
 * 支持消费方自定义持久化实现。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.literule.audit;
