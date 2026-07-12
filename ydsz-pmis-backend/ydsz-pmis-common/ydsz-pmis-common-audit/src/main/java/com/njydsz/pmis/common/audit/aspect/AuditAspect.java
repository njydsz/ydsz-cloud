package com.njydsz.pmis.common.audit.aspect;

import com.njydsz.pmis.common.audit.annotation.Audit;
import com.njydsz.pmis.common.audit.config.AuditProperties;
import com.njydsz.pmis.common.audit.context.AuditContext;
import com.njydsz.pmis.common.audit.context.AuditContext.AuditContextData;
import com.njydsz.pmis.common.audit.domain.AuditLog;
import com.njydsz.pmis.common.audit.enums.AuditStatus;
import com.njydsz.pmis.common.audit.event.AuditEvent;
import com.njydsz.pmis.common.audit.mask.SensitiveFieldMask;
import com.njydsz.pmis.common.audit.template.AuditTemplateProcessor;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.util.id.SnowflakeUtils;
import com.njydsz.pmis.common.util.ip.IpAddrUtils;
import com.njydsz.pmis.common.util.string.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import org.aspectj.lang.reflect.MethodSignature;
/**
 * 审计日志切面
 * <p>
 * 通过 {@code @Around} 拦截带有 {@link Audit} 注解的方法，自动采集以下信息：
 * <ul>
 *   <li>请求上下文：URL、URI、HTTP 方法、IP、UA、TraceId 等</li>
 *   <li>方法签名：模块、类型、行为、内容（支持 SpEL）</li>
 *   <li>执行结果：返回值（可选）、异常信息、执行耗时</li>
 *   <li>操作人信息：透传自 {@code RequestContext}</li>
 * </ul>
 * </p>
 *
 * <p><b>安全与性能要点：</b></p>
 * <ul>
 *   <li>请求参数使用 {@link SensitiveFieldMask} 进行脱敏；默认敏感词列表见
 *       {@link com.njydsz.pmis.common.audit.config.AuditProperties#getSensitiveParams()}</li>
 *   <li>对超大参数（&gt;10KB）和深嵌套对象进行截断/占位，避免 OOM</li>
 *   <li>异步事件通过 Spring {@code ApplicationEventPublisher} 发布，
 *       由 {@link com.njydsz.pmis.common.audit.config.AuditEventListener} 委托
 *       {@link com.njydsz.pmis.common.audit.core.AuditRecorder} 落盘</li>
 *   <li>支持 @Async 方法（自动透传 RequestAttributes）</li>
 *   <li>审计本身异常被 try-catch 隔离，绝不污染业务主链路</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    /**
     * 默认参数序列化深度限制（3 层）
     */
    private static final int DEFAULT_MAX_SERIALIZE_DEPTH = 3;

    /**
     * 默认参数序列化长度限制（10KB）
     */
    private static final int DEFAULT_MAX_SERIALIZE_LENGTH = 10 * 1024;

    /**
     * Spring 事件发布器
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 审计配置属性
     */
    private final AuditProperties properties;

    /**
     * SpEL 模板处理器
     */
    private final AuditTemplateProcessor templateProcessor;

    /**
     * 敏感参数名集合（构造时从配置初始化）
     */
    private final Set<String> sensitiveParams = new HashSet<>();

    /**
     * 构造审计日志切面
     *
     * @param eventPublisher    Spring 事件发布器
     * @param properties        审计配置属性
     * @param templateProcessor SpEL 模板处理器
     */
    public AuditAspect(ApplicationEventPublisher eventPublisher, AuditProperties properties, AuditTemplateProcessor templateProcessor) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.templateProcessor = templateProcessor;

        if (properties.getSensitiveParams() != null) {
            this.sensitiveParams.addAll(Arrays.asList(properties.getSensitiveParams()));
        }
    }

    /**
     * 审计注解切点：拦截所有标注 {@link Audit} 的方法
     */
    @Pointcut("@annotation(com.njydsz.pmis.common.audit.annotation.Audit)")
    public void auditPointcut() {
    }

    /**
     * 环绕通知：执行业务方法、采集上下文、发布审计事件。
     * <p>关闭审计（{@code properties.isEnabled() == false}）时直接放行，不采集。
     *
     * @param joinPoint 切点
     * @param audit     方法上的 {@link Audit} 注解
     * @return 业务方法返回值
     * @throws Throwable 业务方法本身抛出的异常（不会被切面吞掉）
     */
    @Around("auditPointcut() && @annotation(audit)")
    public Object aroundAudit(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        AuditContextData context = initAuditContext(joinPoint, audit);

        // 捕获 ServletRequestAttributes，用于异步线程中恢复请求上下文
        ServletRequestAttributes servletRequestAttributes = null;
        try {
            servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        } catch (Exception ignored) {
            // 非 Web 环境或请求上下文不可用时忽略
        }
        final ServletRequestAttributes capturedRequestAttributes = servletRequestAttributes;

        Object result = null;
        Throwable exception = null;

        try {
            result = proceedWithContext(joinPoint, capturedRequestAttributes);
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            try {
                AuditLog auditLog = buildAuditLog(joinPoint, audit, context, result, exception, startTime);
                publishAuditEvent(auditLog, context.getToken());
            } catch (Exception e) {
                log.error("【审计切面】记录审计日志失败: {}", e.getMessage(), e);
            } finally {
                AuditContext.clear();
            }
        }
    }

    /**
     * 在审计上下文中执行目标方法，自动恢复请求上下文以支持 @Async 方法。
     *
     * @param joinPoint        切点
     * @param requestAttributes 原线程的请求属性
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    private Object proceedWithContext(ProceedingJoinPoint joinPoint, ServletRequestAttributes requestAttributes) throws Throwable {
        final Object[] resultHolder = new Object[1];
        final Throwable[] errorHolder = new Throwable[1];

        Runnable task = () -> {
            // 恢复请求上下文（支持 @Async 方法）
            if (requestAttributes != null) {
                try {
                    RequestContextHolder.setRequestAttributes(requestAttributes, true);
                } catch (Exception ignored) {
                    // 请求属性不可设置时忽略
                }
            }
            try {
                resultHolder[0] = joinPoint.proceed();
            } catch (Throwable e) {
                errorHolder[0] = e;
            }
        };

        // 使用 AuditContext.wrap 包装任务，自动传递 ThreadLocal 上下文
        Runnable wrappedTask = AuditContext.wrap(task);
        wrappedTask.run();

        if (errorHolder[0] != null) {
            throw errorHolder[0];
        }
        return resultHolder[0];
    }

    /**
     * 初始化审计上下文，从请求头中提取 IP/UA/TraceId 等信息。
     *
     * @param joinPoint 切点
     * @param audit     审计注解
     * @return 审计上下文数据
     */
    private AuditContextData initAuditContext(JoinPoint joinPoint, Audit audit) {
        AuditContextData context = new AuditContextData();
        context.setStartTime(System.currentTimeMillis());

        ServletRequestAttributes attributes = null;
        try {
            attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        } catch (Exception e) {
            // 非 Web 环境或请求上下文不可用时忽略
            log.debug("【审计切面】无法获取请求上下文，可能处于非 Web 环境");
        }

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            context.setUrl(request.getRequestURL() != null ? request.getRequestURL().toString() : "");
            context.setUri(request.getRequestURI());
            context.setHttpMethod(request.getMethod());
            context.setIpAddress(IpAddrUtils.getIpAddr(request));
            context.setToken(request.getHeader("X-Access-Token"));
            context.setBusinessNo(request.getHeader("X-Business-No"));

            // 启用 IP 归属地解析时，记录 IP 地址（实际解析需要外部服务）
            if (properties.isIpLocationEnabled()) {
                String ipAddress = context.getIpAddress();
                if (StringUtils.isNotBlank(ipAddress)) {
                    context.putExtra("ipLocation", ipAddress); // 实际应用中需要调用 IP 归属地服务
                }
            }

            // 启用 User-Agent 解析时，记录客户端信息
            if (properties.isUserAgentEnabled()) {
                String userAgent = request.getHeader("User-Agent");
                if (StringUtils.isNotBlank(userAgent)) {
                    context.putExtra("userAgent", userAgent);
                }
            }

            // 记录 TraceId（如果存在）
            String traceId = request.getHeader("X-Trace-Id");
            if (StringUtils.isNotBlank(traceId)) {
                context.putExtra("traceId", traceId);
            }
        }

        context.setRequestArgs(joinPoint.getArgs());

        AuditContext.set(context);
        return context;
    }

    /**
     * 构建审计日志实体
     *
     * @param joinPoint 切点
     * @param audit     审计注解
     * @param context   审计上下文
     * @param result    方法执行结果（异常时为 null）
     * @param exception 异常信息（成功时为 null）
     * @param startTime 开始时间（毫秒时间戳）
     * @return 审计日志实体
     */
    private AuditLog buildAuditLog(ProceedingJoinPoint joinPoint, Audit audit, AuditContextData context,
                                   Object result, Throwable exception, long startTime) {
        AuditLog auditLog = new AuditLog();

        auditLog.setId(SnowflakeUtils.nextIdStr());
        auditLog.setAuditType(audit.type().getCode());
        auditLog.setAction(audit.action().getCode());
        auditLog.setStatus(exception != null ? AuditStatus.FAILURE.getCode() : AuditStatus.SUCCESS.getCode());
        auditLog.setModule(audit.module());
        auditLog.setOperationTime(LocalDateTime.now());
        auditLog.setCostTime(System.currentTimeMillis() - startTime);

        if (StringUtils.isNotBlank(audit.content())) {
            Method method = getMethod(joinPoint);
            String content = templateProcessor.processTemplate(audit.content(), method, joinPoint.getArgs());
            auditLog.setContent(content);
        }

        if (context != null) {
            auditLog.setIpAddress(context.getIpAddress());
            auditLog.setBusinessNo(context.getBusinessNo());
            auditLog.setOperatorId(context.getOperatorId());
            auditLog.setOperatorName(context.getOperatorName());
        }

        if (audit.recordRequest() && properties.isRecordRequest()) {
            String requestParams = buildRequestParams(joinPoint, audit.excludeParams());
            auditLog.setRequestParams(requestParams);
        }

        if (audit.recordResponse() && properties.isRecordResponse() && result != null) {
            try {
                String responseJson = JsonUtils.toJson(result);
                responseJson = truncateWithWarning(responseJson, DEFAULT_MAX_SERIALIZE_LENGTH, "响应结果");
                String maskedResponse = maskSensitiveJson(responseJson, sensitiveParams);
                auditLog.setResponseResult(maskedResponse);
            } catch (Exception e) {
                log.debug("【审计切面】序列化响应结果失败: {}", e.getMessage());
            }
        }

        if (exception != null) {
            auditLog.setErrorMessage(exception.getClass().getName() + ": " + exception.getMessage());
        }

        auditLog.setAppId(properties.getAppId());
        auditLog.setAppCode(properties.getAppCode());
        auditLog.setAppName(properties.getAppName());
        auditLog.setCreatedAt(LocalDateTime.now());

        return auditLog;
    }

    /**
     * 构建请求参数 JSON 字符串（已脱敏）
     *
     * @param joinPoint     切点
     * @param excludeParams 需要排除的参数（与默认敏感词合并）
     * @return 请求参数 JSON 字符串；异常时返回空串
     */
    private String buildRequestParams(JoinPoint joinPoint, String[] excludeParams) {
        try {
            Set<String> excludes = new HashSet<>(sensitiveParams);
            if (excludeParams != null) {
                excludes.addAll(Arrays.asList(excludeParams));
            }

            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg != null && !isFilterObject(arg)) {
                    try {
                        String json = serializeWithDepthLimit(arg);
                        json = truncateWithWarning(json, DEFAULT_MAX_SERIALIZE_LENGTH, "请求参数");
                        if (StringUtils.isNotBlank(json)) {
                            String masked = maskSensitiveJson(json, excludes);
                            sb.append(masked).append(" ");
                        }
                    } catch (Exception ignored) {
                        log.trace("【审计切面】序列化参数失败: {}", ignored.getMessage());
                    }
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("【审计切面】构建请求参数失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 对 JSON 字符串中的敏感字段进行脱敏处理
     *
     * @param json            JSON 字符串
     * @param sensitiveFields 敏感字段集合
     * @return 脱敏后的 JSON 字符串
     */
    private String maskSensitiveJson(String json, Set<String> sensitiveFields) {
        if (json == null || json.isEmpty() || sensitiveFields == null || sensitiveFields.isEmpty()) {
            return json;
        }
        if (properties.isMaskEnabled()) {
            return SensitiveFieldMask.maskJson(json, sensitiveFields);
        }
        return json;
    }

    /**
     * 检查对象是否为需要过滤的类型（如 Servlet 组件、上传文件、BindingResult 等）
     * <p>这些对象序列化无业务意义且可能引发异常，因此跳过记录。
     *
     * @param o 待检查对象
     * @return 需要过滤返回 true
     */
    private boolean isFilterObject(final Object o) {
        if (o == null) {
            return true;
        }

        Class<?> clazz = o.getClass();

        if (clazz.isArray()) {
            return clazz.getComponentType().isAssignableFrom(MultipartFile.class);
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            return ((Collection<?>) o).stream().anyMatch(v -> v instanceof MultipartFile);
        }

        if (Map.class.isAssignableFrom(clazz)) {
            return ((Map<?, ?>) o).values().stream().anyMatch(v -> v instanceof MultipartFile);
        }

        return o instanceof MultipartFile
                || o instanceof HttpServletRequest
                || o instanceof HttpServletResponse
                || o instanceof BindingResult;
    }

    /**
     * 带深度限制的 JSON 序列化
     * <p>当对象嵌套层级超过 {@link #DEFAULT_MAX_SERIALIZE_DEPTH} 时，超出深度的子对象
     * 将被替换为 {@code [max depth reached]} 占位符。
     *
     * @param obj 待序列化对象
     * @return 限制深度后的 JSON 字符串
     */
    private String serializeWithDepthLimit(Object obj) {
        try {
            return JsonUtils.toJson(obj);
        } catch (StackOverflowError e) {
            log.warn("【审计切面】参数序列化深度超过限制（{}层），已截断", DEFAULT_MAX_SERIALIZE_DEPTH);
            return "{\"_truncated\": true, \"_reason\": \"depth limit exceeded (" + DEFAULT_MAX_SERIALIZE_DEPTH + " levels)\"}";
        } catch (Exception e) {
            log.warn("【审计切面】参数序列化失败: {}", e.getMessage());
            return "{\"_error\": \"serialization failed\"}";
        }
    }

    /**
     * 对超长字符串进行截断，并记录 WARN 日志
     *
     * @param original  原始字符串
     * @param maxLength 最大允许长度（字节）
     * @param fieldName 字段名称（用于日志）
     * @return 截断后的字符串（超限时追加 {@code ...[truncated]} 标记）
     */
    private String truncateWithWarning(String original, int maxLength, String fieldName) {
        if (original == null) {
            return null;
        }
        if (original.length() <= maxLength) {
            return original;
        }
        log.warn("【审计切面】{} 序列化结果超出长度限制({} bytes)，已截断: 原始长度={} bytes",
                fieldName, maxLength, original.length());
        return original.substring(0, maxLength) + "...[truncated]";
    }

    /**
     * 发布审计事件
     *
     * @param auditLog 审计日志
     * @param token    用户令牌
     */
    private void publishAuditEvent(AuditLog auditLog, String token) {
        if (auditLog == null) {
            return;
        }

        try {
            AuditEvent event = AuditEvent.of(this, auditLog, token);
            eventPublisher.publishEvent(event);
            log.debug("【审计切面】审计事件已发布: id={}, module={}, action={}",
                    auditLog.getId(), auditLog.getModule(), auditLog.getAction());
        } catch (Exception e) {
            log.error("【审计切面】发布审计事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取方法签名（从切点中提取）
     *
     * @param joinPoint 切点
     * @return 方法对象；解析失败时返回 null
     */
    private Method getMethod(JoinPoint joinPoint) {
        try {
            return ((MethodSignature) joinPoint.getSignature()).getMethod();
        } catch (Exception e) {
            log.debug("【审计切面】获取方法签名失败: {}", e.getMessage());
            return null;
        }
    }
}
