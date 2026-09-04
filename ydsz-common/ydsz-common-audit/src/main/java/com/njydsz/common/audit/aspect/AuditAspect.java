package com.njydsz.common.audit.aspect;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.config.AuditProperties;
import com.njydsz.common.audit.context.AuditContext.AuditContextData;
import com.njydsz.common.audit.context.AuditContext;
import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.audit.enums.AuditStatus;
import com.njydsz.common.audit.mask.SensitiveFieldMask;
import com.njydsz.common.audit.template.AuditTemplateProcessor;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.string.StringUtils;

/**
 * 审计日志切面
 *
 * <p>通过 {@code @Around} 拦截带有 {@link Audit} 注解的方法，自动采集以下信息：
 *
 * <ul>
 *   <li>请求上下文：URL、URI、HTTP 方法、IP、UA、TraceId 等
 *   <li>方法签名：模块、类型、行为、内容（支持 SpEL）
 *   <li>执行结果：返回值（可选）、异常信息、执行耗时
 *   <li>操作人信息：透传自 {@code RequestContext}
 * </ul>
 *
 * <p><b>安全与性能要点：</b>
 *
 * <ul>
 *   <li>请求参数使用 {@link SensitiveFieldMask} 进行脱敏；默认敏感词列表见 {@link
 *       com.njydsz.common.audit.config.AuditProperties#getSensitiveParams()}
 *   <li>对超大参数（&gt;10KB）和深嵌套对象进行截断/占位，避免 OOM
 *   <li>审计记录通过 {@link AuditRecorder} 异步落盘，不阻塞业务主链路
 *   <li>支持 @Async 方法（自动透传 RequestAttributes）
 *   <li>审计本身异常被 try-catch 隔离，绝不污染业务主链路
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Aspect
public class AuditAspect {

  private static final Logger LOG = LoggerFactory.getLogger(AuditAspect.class);

  /** 默认参数序列化深度限制（3 层） */
  private static final int DEFAULT_MAX_SERIALIZE_DEPTH = 3;

  /** 默认参数序列化长度限制（10KB） */
  private static final int DEFAULT_MAX_SERIALIZE_LENGTH = 10 * 1024;

  /** 审计记录器，用于异步落盘审计日志 */
  private final AuditRecorder auditRecorder;

  /** 审计配置属性 */
  private final AuditProperties properties;

  /** SpEL 模板处理器 */
  private final AuditTemplateProcessor templateProcessor;

  /** 敏感参数名集合（构造时从配置初始化） */
  private final Set<String> sensitiveParams = new HashSet<>(16);

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 构造审计日志切面
   *
   * @param auditRecorder 审计记录器
   * @param properties 审计配置属性
   * @param templateProcessor SpEL 模板处理器
   * @param snowflakeIdGenerator 分布式 ID 生成器
   */
  public AuditAspect(
      AuditRecorder auditRecorder,
      AuditProperties properties,
      AuditTemplateProcessor templateProcessor,
      SnowflakeIdGenerator snowflakeIdGenerator) {
    this.auditRecorder = auditRecorder;
    this.properties = properties;
    this.templateProcessor = templateProcessor;
    this.snowflakeIdGenerator = snowflakeIdGenerator;

    if (properties.getSensitiveParams() != null) {
      this.sensitiveParams.addAll(Arrays.asList(properties.getSensitiveParams()));
    }
  }

  /** 审计注解切点：拦截所有标注 {@link Audit} 的方法 */
  @Pointcut("@annotation(com.njydsz.common.audit.annotation.Audit)")
  public void auditPointcut() {}

  /**
   * 环绕通知：执行业务方法、采集上下文、记录审计日志。
   *
   * <p>关闭审计（{@code properties.isEnabled() == false}）时直接放行，不采集。
   *
   * @param joinPoint 切点
   * @param audit 方法上的 {@link Audit} 注解
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
      servletRequestAttributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
        recordAuditLog(auditLog);
      } catch (Exception e) {
        LOG.error("【审计切面】记录审计日志失败: {}", e.getMessage(), e);
      } finally {
        AuditContext.clear();
      }
    }
  }

  /**
   * 在审计上下文中执行目标方法，自动恢复请求上下文以支持 @Async 方法。
   *
   * @param joinPoint 切点
   * @param requestAttributes 原线程的请求属性
   * @return 目标方法返回值
   * @throws Throwable 目标方法抛出的异常
   */
  private Object proceedWithContext(
      ProceedingJoinPoint joinPoint, ServletRequestAttributes requestAttributes) throws Throwable {
    final Object[] resultHolder = new Object[1];
    final Throwable[] errorHolder = new Throwable[1];

    Runnable task =
        () -> {
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
   * @param audit 审计注解
   * @return 审计上下文数据
   */
  private AuditContextData initAuditContext(JoinPoint joinPoint, Audit audit) {
    AuditContextData context = new AuditContextData();
    context.setStartTime(System.currentTimeMillis());

    HttpServletRequest request = RequestContextUtils.getRequest();
    if (request == null) {
      request = (HttpServletRequest) RequestContext.get(BizContextKeys.KEY_HTTP_REQUEST);
    }

    if (request != null) {
      context.setUrl(request.getRequestURL() != null ? request.getRequestURL().toString() : "");
      context.setUri(request.getRequestURI());
      context.setHttpMethod(request.getMethod());
      context.setIpAddress(ClientIpResolver.getClientIp(request));
      context.setToken(request.getHeader("X-Access-Token"));
      context.setBusinessNo(request.getHeader("X-Business-No"));

      // 记录 TraceId（如果存在）
      String traceId = request.getHeader(HeaderConstants.TRACE_ID_HEADER);
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
   * @param audit 审计注解
   * @param context 审计上下文
   * @param result 方法执行结果（异常时为 null）
   * @param exception 异常信息（成功时为 null）
   * @param startTime 开始时间（毫秒时间戳）
   * @return 审计日志实体
   */
  private AuditLog buildAuditLog(
      ProceedingJoinPoint joinPoint,
      Audit audit,
      AuditContextData context,
      Object result,
      Throwable exception,
      long startTime) {
    AuditLog auditLog = new AuditLog();

    auditLog.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    auditLog.setAuditType(audit.type().getCode());
    auditLog.setAction(audit.action().getCode());
    auditLog.setStatus(
        exception != null ? AuditStatus.FAILURE.getCode() : AuditStatus.SUCCESS.getCode());
    auditLog.setModule(audit.module());
    auditLog.setOperationTime(LocalDateTime.now());
    auditLog.setCostTime(System.currentTimeMillis() - startTime);

    if (StringUtils.isNotBlank(audit.content())) {
      Method method = getMethod(joinPoint);
      String content =
          templateProcessor.processTemplate(audit.content(), method, joinPoint.getArgs());
      auditLog.setContent(content);
    }

    if (context != null) {
      auditLog.setIpAddress(context.getIpAddress());
      auditLog.setBusinessNo(context.getBusinessNo());
      auditLog.setOperatorId(context.getOperatorId());
      auditLog.setOperatorName(context.getOperatorName());

      // 设置租户 ID（从 RequestContext 透传）
      auditLog.setTenantId(context.getExtra("tenantId", String.class));
      // 设置链路追踪 ID（已从 extraInfo 迁移到独立列）
      auditLog.setTraceId(context.getExtra("traceId", String.class));
    }

    if (audit.recordRequest() && properties.isRecordRequest()) {
      String requestParams = buildRequestParams(joinPoint, audit.excludeParams());
      auditLog.setRequestParams(requestParams);
    }

    if (audit.recordResponse() && properties.isRecordResponse() && result != null) {
      try {
        String responseJson = YdszJson.toJson(result);
        responseJson = truncateWithWarning(responseJson, DEFAULT_MAX_SERIALIZE_LENGTH, "响应结果");
        String maskedResponse = maskSensitiveJson(responseJson, sensitiveParams);
        auditLog.setResponseResult(maskedResponse);
      } catch (Exception e) {
        LOG.debug("【审计切面】序列化响应结果失败: {}", e.getMessage());
      }
    }

    if (exception != null) {
      auditLog.setErrorMessage(exception.getClass().getName() + ": " + exception.getMessage());
    }

    auditLog.setAppKey(properties.getAppKey());
    auditLog.setCreatedAt(LocalDateTime.now());

    return auditLog;
  }

  /**
   * 构建请求参数 JSON 字符串（已脱敏）
   *
   * @param joinPoint 切点
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
            String json = serializeSafely(arg);
            json = truncateWithWarning(json, DEFAULT_MAX_SERIALIZE_LENGTH, "请求参数");
            if (StringUtils.isNotBlank(json)) {
              String masked = maskSensitiveJson(json, excludes);
              sb.append(masked).append(" ");
            }
          } catch (Exception ignored) {
            LOG.trace("【审计切面】序列化参数失败: {}", ignored.getMessage());
          }
        }
      }

      return sb.toString().trim();
    } catch (Exception e) {
      LOG.debug("【审计切面】构建请求参数失败: {}", e.getMessage());
      return "";
    }
  }

  /**
   * 对 JSON 字符串中的敏感字段进行脱敏处理
   *
   * @param json JSON 字符串
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
   *
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
   * 安全 JSON 序列化（防 StackOverflow）
   *
   * <p>使用 YdszJson 序列化对象，当对象存在循环引用或嵌套过深导致 StackOverflowError 时， 返回截断占位 JSON 而非抛出异常。
   *
   * @param obj 待序列化对象
   * @return JSON 字符串；序列化失败时返回错误占位 JSON
   */
  private String serializeSafely(Object obj) {
    try {
      return YdszJson.toJson(obj);
    } catch (StackOverflowError e) {
      LOG.warn("【审计切面】参数序列化发生 StackOverflow，已返回占位 JSON（建议检查对象循环引用）");
      return "{\"_truncated\": true, \"_reason\": \"depth limit exceeded ("
          + DEFAULT_MAX_SERIALIZE_DEPTH
          + " levels)\"}";
    } catch (Exception e) {
      LOG.warn("【审计切面】参数序列化失败: {}", e.getMessage());
      return "{\"_error\": \"serialization failed\"}";
    }
  }

  /**
   * 对超长字符串进行截断，并记录 WARN 日志
   *
   * @param original 原始字符串
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
    LOG.warn(
        "【审计切面】{} 序列化结果超出长度限制({} bytes)，已截断: 原始长度={} bytes",
        fieldName,
        maxLength,
        original.length());
    return original.substring(0, maxLength) + "...[truncated]";
  }

  /**
   * 记录审计日志（委托给 AuditRecorder 异步落盘）
   *
   * @param auditLog 审计日志实体
   */
  private void recordAuditLog(AuditLog auditLog) {
    if (auditLog == null) {
      return;
    }

    try {
      auditRecorder.record(auditLog);
      LOG.debug(
          "【审计切面】审计日志已记录: id={}, module={}, action={}",
          auditLog.getId(),
          auditLog.getModule(),
          auditLog.getAction());
    } catch (Exception e) {
      LOG.error("【审计切面】记录审计日志失败: {}", e.getMessage(), e);
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
      LOG.debug("【审计切面】获取方法签名失败: {}", e.getMessage());
      return null;
    }
  }
}
