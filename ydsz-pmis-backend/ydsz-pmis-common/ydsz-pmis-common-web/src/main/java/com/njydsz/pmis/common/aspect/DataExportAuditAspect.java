package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.DataExportAudit;
import com.njydsz.pmis.common.security.DataExportAuditEvent;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Objects;

/**
 * 数据导出审计 AOP
 *
 * <p>拦截 {@code @DataExportAudit} 方法，发布异步审计事件。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataExportAuditAspect {

    /** Spring 事件发布器 */
    private final ApplicationEventPublisher publisher;

    /**
     * 环绕增强：先执行目标方法，再异步发布数据导出审计事件
     *
     * @param pjp 连接点
     * @param ann 数据导出审计注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, DataExportAudit ann) throws Throwable {
        Object result = pjp.proceed();
        try {
            publish(pjp, ann, result);
        } catch (Exception e) {
            log.warn("[ExportAudit] 发布事件失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 异步发布数据导出审计事件。
     *
     * <p>根据返回结果类型推断导出行数：Collection 取 size，Number 取 intValue，其他记为 0。
     * 事件包含用户、模块、动作、行数、IP、traceId 与租户信息。</p>
     *
     * @param pjp    连接点
     * @param ann    数据导出审计注解
     * @param result 目标方法返回值
     */
    @Async("exportExecutor")
    void publish(ProceedingJoinPoint pjp, DataExportAudit ann, Object result) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;
        LoginUser user = SecurityContext.getCurrentOrNull();

        int rowCount = 0;
        if (result instanceof Collection<?> col) {
            rowCount = col.size();
        } else if (result instanceof Number n) {
            rowCount = n.intValue();
        }

        DataExportAuditEvent event = DataExportAuditEvent.builder()
                .userId(user != null ? user.getUserId() : null)
                .username(user != null ? user.getUsername() : null)
                .exportModule(ann.module())
                .exportAction(ann.action())
                .bizType(ann.bizType())
                .rowCount(rowCount)
                .traceId(TraceIdUtil.get())
                .clientIp(request != null ? clientIp(request) : "")
                .tenantId(TenantContext.getTenantId())
                .exportedAt(System.currentTimeMillis())
                .build();
        publisher.publishEvent(event);
    }

    /**
     * 解析客户端真实 IP。
     *
     * <p>优先级：X-Forwarded-For（取第一个）&gt; X-Real-IP &gt; remoteAddr，兜底 "unknown"。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > -1 ? ip.substring(0, idx) : ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }
}
