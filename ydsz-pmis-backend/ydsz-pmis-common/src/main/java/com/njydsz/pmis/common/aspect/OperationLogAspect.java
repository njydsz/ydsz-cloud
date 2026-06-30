package com.njydsz.pmis.common.aspect;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 操作日志 AOP
 *
 * <p>拦截 {@code @OperationLog} 注解方法，自动记录：
 * <ul>
 *   <li>用户、IP、UA</li>
 *   <li>请求 URL、方法、入参</li>
 *   <li>响应结果、耗时、状态</li>
 * </ul>
 *
 * <p>实际日志落库需结合事件机制（异步）推送至日志服务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Pointcut("@annotation(com.njydsz.pmis.common.annotation.OperationLog)")
    public void pointcut() {
    }

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
                saveLog(pjp, operationLog, result, error, cost);
            } catch (Exception e) {
                log.error("[OperationLog] 保存操作日志失败", e);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint pjp, OperationLog log, Object result, Throwable error, long cost) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        LoginUser user = SecurityContext.getCurrentOrNull();

        String url = request != null ? request.getRequestURI() : "";
        String methodName = request != null ? request.getMethod() : "";
        String ip = request != null ? getIp(request) : "";
        String userAgent = request != null ? request.getHeader("User-Agent") : "";

        String params = "";
        if (log.saveParams()) {
            params = JSON.toJSONString(pjp.getArgs());
            // 脱敏
            for (String field : log.excludeFields()) {
                params = params.replaceAll("\"" + field + "\"\\s*:\\s*\"[^\"]*\"",
                        "\"" + field + "\":\"******\"");
            }
        }

        String responseData = "";
        if (log.saveResult() && result != null) {
            responseData = JSON.toJSONString(result);
        }

        String status = error == null ? "SUCCESS" : "FAILED";
        String errorMsg = error == null ? "" : error.getMessage();

        // TODO: 异步落库到 pmis_log.pmis_operation_log
        // 当前阶段仅控制台输出，待运营日志服务就绪后改为事件发布
        org.slf4j.Logger log4j = org.slf4j.LoggerFactory.getLogger("OPERATION_LOG");
        log4j.info("[{}] module={} action={} bizType={} userId={} username={} url={} {} params={} status={} cost={}ms error={}",
                status,
                log.module(),
                log.action(),
                log.bizType(),
                user != null ? user.getUserId() : "-",
                user != null ? user.getUsername() : "-",
                methodName,
                url,
                params,
                status,
                cost,
                errorMsg);
    }

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
        return request.getRemoteAddr();
    }
}
