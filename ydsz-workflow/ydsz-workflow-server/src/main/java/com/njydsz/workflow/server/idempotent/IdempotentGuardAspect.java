package com.njydsz.workflow.server.idempotent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.entity.FlowIdempotent;
import com.njydsz.workflow.domain.repository.FlowIdempotentRepository;

/**
 * 幂等守卫 AOP 切面。
 *
 * <p>拦截带 {@link IdempotentGuard} 注解的方法：
 *
 * <ol>
 *   <li>通过 SpEL 表达式求值构造幂等键</li>
 *   <li>SHA-256 哈希后尝试插入 ydsz_flow_idempotent 表</li>
 *   <li>唯一约束冲突时：SUCCESS 直接返回缓存结果；PROCESSING 等待或抛异常</li>
 *   <li>方法执行成功 → markSuccess 缓存结果；执行失败 → markFailed</li>
 * </ol>
 *
 * <p><b>失败降级：</b>幂等表不可用时（如数据库连接异常），切面跳过幂等检查，记录 warn 日志，
 * 允许业务方法正常执行，<b>不阻断主流程</b>。
 *
 * <p><b>架构合规说明（YDIZ-ARCH-001）：</b>切面通过 domain 层 {@link FlowIdempotentRepository} 接口
 * 操作幂等记录，不直接依赖 infra 层 Mapper。
 *
 * @author ydsz-team
 * @since 26.09.23
 * @see IdempotentGuard 幂等守卫注解
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentGuardAspect {

  /** 成功状态常量 */
  private static final String STATUS_SUCCESS = "SUCCESS";
  /** 处理中状态常量 */
  private static final String STATUS_PROCESSING = "PROCESSING";
  /** 轮询等待间隔（毫秒） */
  private static final long POLL_INTERVAL_MS = 100L;

  private final FlowIdempotentRepository idempotentRepository;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

  /**
   * 环绕通知：在幂等查找 → 执行业务方法 → 更新幂等表状态之间串联。
   *
   * @param joinPoint 切点
   * @param annotation 方法上的 @IdempotentGuard 注解
   * @return 方法执行结果（或缓存结果）
   * @throws Throwable 业务方法异常或幂等处理异常
   */
  @Around("@annotation(annotation)")
  public Object around(ProceedingJoinPoint joinPoint, IdempotentGuard annotation) throws Throwable {
    String scope = annotation.scope();
    String keyRaw = evaluateKeyExpression(joinPoint, annotation.keyExpression());
    String keyHash = sha256(scope + ":" + keyRaw);

    // 尝试插入幂等记录
    FlowIdempotent inserted = null;
    try {
      FlowIdempotent record = buildProcessingRecord(scope, keyRaw, keyHash, annotation.ttlMinutes());
      inserted = idempotentRepository.tryInsert(record);
    } catch (Exception e) {
      // 幂等表不可用时降级：跳过幂等检查，执行业务方法
      log.warn("[IdempotentGuard] 幂等表不可用降级执行: scope={} key={} err={}",
          scope, keyRaw, e.getMessage());
      return joinPoint.proceed();
    }

    if (inserted == null) {
      // 唯一约束冲突：已存在记录
      return handleConflict(joinPoint, annotation, scope, keyHash, keyRaw);
    }

    // 执行业务方法并记录结果
    return executeAndRecord(joinPoint, scope, keyHash, keyRaw);
  }

  // ============================== 内部方法 ==============================

  /**
   * 处理幂等键冲突场景。
   *
   * @return 缓存结果（SUCCESS 时）
   * @throws IdempotentProcessingException PROCESSING 状态且未等待成功时
   * @throws Throwable 业务方法异常
   */
  private Object handleConflict(ProceedingJoinPoint joinPoint, IdempotentGuard annotation,
      String scope, String keyHash, String keyRaw) throws Throwable {
    FlowIdempotent successRecord = idempotentRepository.findSuccess(scope, keyHash);
    if (successRecord != null && successRecord.getResultData() != null) {
      log.info("[IdempotentGuard] 幂等命中（SUCCESS）: scope={} key={}", scope, keyRaw);
      // 返回缓存结果——调用方需通过返回类型反序列化，null 表示需查缓存
      return null;
    }

    // PROCESSING 状态：等待或抛异常
    if (annotation.waitForProcessing()) {
      FlowIdempotent waited = waitUntilComplete(scope, keyHash, annotation.waitTimeoutMs());
      if (waited != null && waited.getResultData() != null) {
        return null;
      }
    }

    throw new IdempotentProcessingException(scope, keyRaw);
  }

  /**
   * 执行业务方法并记录幂等结果（成功/失败）。
   *
   * @return 业务方法返回值
   * @throws Throwable 业务方法异常（已标记 markFailed）
   */
  private Object executeAndRecord(ProceedingJoinPoint joinPoint,
      String scope, String keyHash, String keyRaw) throws Throwable {
    try {
      Object result = joinPoint.proceed();
      String resultData = serializeResult(result);
      idempotentRepository.markSuccess(scope, keyHash, resultData);
      return result;
    } catch (Throwable e) {
      idempotentRepository.markFailed(scope, keyHash, truncateMessage(e.getMessage()));
      throw e;
    }
  }

  private String evaluateKeyExpression(ProceedingJoinPoint joinPoint, String keyExpression) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
        joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(),
        parameterNameDiscoverer);
    Expression expression = expressionParser.parseExpression(keyExpression);
    Object value = expression.getValue(context);
    return value != null ? value.toString() : "";
  }

  private FlowIdempotent buildProcessingRecord(String scope, String keyRaw, String keyHash,
      int ttlMinutes) {
    LocalDateTime now = LocalDateTime.now();
    FlowIdempotent record = new FlowIdempotent();
    record.setScope(scope);
    record.setKeyHash(keyHash);
    record.setKeyRaw(keyRaw);
    record.setStatus(STATUS_PROCESSING);
    record.setRetryCount(0);
    record.setCreatedAt(now);
    record.setUpdatedAt(now);
    record.setTtlAt(now.plusMinutes(ttlMinutes));
    return record;
  }

  private FlowIdempotent waitUntilComplete(String scope, String keyHash, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    do {
      FlowIdempotent record = idempotentRepository.findSuccess(scope, keyHash);
      if (record != null && STATUS_SUCCESS.equals(record.getStatus())) {
        return record;
      }
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    } while (System.currentTimeMillis() < deadline);
    return null;
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      log.warn("[IdempotentGuard] SHA-256 不可用，使用替代哈希: {}", e.getMessage());
      return "h_" + input.hashCode();
    }
  }

  private String serializeResult(Object result) {
    if (result == null) {
      return null;
    }
    try {
      return YdszJson.toJson(result);
    } catch (Exception e) {
      log.warn("[IdempotentGuard] 结果序列化失败: {}", e.getMessage());
      return "{\"cached\":true}";
    }
  }

  private String truncateMessage(String message) {
    if (message == null) {
      return null;
    }
    return message.length() > 512 ? message.substring(0, 512) + "..." : message;
  }
}
