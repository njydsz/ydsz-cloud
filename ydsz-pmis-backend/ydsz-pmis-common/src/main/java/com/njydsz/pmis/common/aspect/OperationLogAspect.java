package com.njydsz.pmis.common.aspect;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.event.OperationLogEvent;
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
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 操作日志 AOP
 *
 * <p>拦截 {@code @OperationLog} 注解方法，构造事件并发布。
 * 持久化由 audit 模块的 {@code OperationLogListener} 异步落库。
 *
 * <p>同时保留本地日志（DEBUG 级别）便于排障。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /** Spring 事件发布器 */
    private final ApplicationEventPublisher publisher;

    /**
     * 切点：标注 {@code @OperationLog} 的方法
     */
    @Pointcut("@annotation(OperationLog)")
    public void pointcut() {
    }

    /**
     * 环绕增强：执行目标方法并记录耗时，无论成功失败均异步发布操作日志事件
     *
     * @param pjp          连接点
     * @param operationLog 操作日志注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("pointcut() && @annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                publishEvent(pjp, operationLog, result, error, cost);
            } catch (Exception e) {
                log.error("[OperationLog] 发布事件失败", e);
            }
        }
    }

    /**
     * 构造操作日志事件并同步发布。
     *
     * <p>注意：此方法不再标注 {@code @Async}。原因：
     * Spring AOP 代理对同类内部调用（{@code this.publishEvent()}）不生效，
     * {@code @Async} 形同虚设。真正的异步由 {@link OperationLogListener} 端的
     * {@code @Async + @EventListener} 实现。此方法仅负责构造事件 + 发布，
     * 由 Spring 事件机制将事件投递到异步监听器。</p>
     *
     * @param pjp          连接点
     * @param operationLog 操作日志注解
     * @param result       目标方法返回值（可为 null）
     * @param error        目标方法抛出的异常（可为 null）
     * @param cost         方法执行耗时（毫秒）
     */
    void publishEvent(ProceedingJoinPoint pjp, OperationLog operationLog,
                      Object result, Throwable error, long cost) {
        try {
            OperationLogEvent event = buildEvent(pjp, operationLog, result, error, cost);
            publisher.publishEvent(event);
        } catch (Exception e) {
            log.error("[OperationLog] 构造事件失败", e);
        }
    }

    /**
     * 构造操作日志事件。
     *
     * <p>采集请求 URL、HTTP 方法、方法签名、客户端 IP、UA、入参（脱敏）、
     * 响应结果、状态、耗时与链路 traceId，组装为 {@link OperationLogEvent}。</p>
     *
     * @param pjp    连接点
     * @param ann    操作日志注解
     * @param result 目标方法返回值（可为 null）
     * @param error  目标方法抛出的异常（可为 null）
     * @param cost   方法执行耗时（毫秒）
     * @return 已填充完成的操作日志事件
     */
    private OperationLogEvent buildEvent(ProceedingJoinPoint pjp, OperationLog ann,
                                         Object result, Throwable error, long cost) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        LoginUser user = SecurityContext.getCurrentOrNull();

        String params = "";
        if (ann.saveParams()) {
            try {
                params = JSON.toJSONString(pjp.getArgs());
                for (String field : ann.excludeFields()) {
                    params = params.replaceAll("\"" + field + "\"\\s*:\\s*\"[^\"]*\"",
                            "\"" + field + "\":\"******\"");
                }
            } catch (Exception e) {
                params = "[serialize-failed]";
            }
        }

        String responseData = "";
        if (ann.saveResult() && result != null) {
            try {
                responseData = JSON.toJSONString(result);
            } catch (Exception e) {
                responseData = "[serialize-failed]";
            }
        }

        return OperationLogEvent.builder()
                .module(ann.module())
                .action(ann.action())
                .bizType(ann.bizType())
                .bizId(extractBizId(pjp.getArgs()))
                .userId(user != null ? user.getUserId() : null)
                .username(user != null ? user.getUsername() : null)
                .requestUrl(request != null ? request.getRequestURI() : "")
                .httpMethod(request != null ? request.getMethod() : "")
                .methodSignature(method.getDeclaringClass().getName() + "#" + method.getName())
                .clientIp(request != null ? getIp(request) : "")
                .userAgent(request != null ? request.getHeader("User-Agent") : "")
                .paramsJson(params)
                .responseJson(responseData)
                .status(error == null ? "SUCCESS" : "FAILED")
                .errorMessage(error == null ? "" : error.getMessage())
                .costMs(cost)
                .traceId(TraceIdUtil.get())
                .tenantId(TenantContext.getTenantId())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 从方法参数中提取业务 ID（bizId）。
     *
     * <p>提取策略：遍历方法参数，优先取第一个 Long/Integer 类型且值 > 0 的参数，
     * 常见于 {@code create(Long id)}、{@code update(Long id, DTO)}、{@code delete(Long id)} 等。
     * 跳过分页对象（IPage）。</p>
     *
     * @param args 方法参数数组
     * @return bizId 字符串，未找到时返回 null
     */
    private String extractBizId(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof IPage) continue;
            if (arg instanceof Long) {
                long val = (Long) arg;
                if (val > 0) return String.valueOf(val);
            }
            if (arg instanceof Integer) {
                int val = (Integer) arg;
                if (val > 0) return String.valueOf(val);
            }
        }
        return null;
    }

    /**
     * 解析客户端真实 IP。
     *
     * <p>优先级：X-Forwarded-For（取第一个）&gt; X-Real-IP &gt; remoteAddr。
     * 兜底返回 "unknown" 以避免空值。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
    private String getIp(HttpServletRequest request) {
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
