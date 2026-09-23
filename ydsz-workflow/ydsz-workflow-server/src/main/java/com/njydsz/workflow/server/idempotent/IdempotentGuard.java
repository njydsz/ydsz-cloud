package com.njydsz.workflow.server.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工作流幂等守卫注解。
 *
 * <p>标记于需要幂等保障的方法上（如 start / advance / reject），切面通过 {@code scope + keyExpression}
 * 构造幂等键，在方法执行前检查是否已处理过：
 *
 * <ul>
 *   <li>已成功（SUCCESS）→ 直接返回缓存结果（通过 {@link IdempotentResult} 写入方法参数）</li>
 *   <li>处理中（PROCESSING）→ 抛出 {@link IdempotentProcessingException}，调用方应等待/重试</li>
 *   <li>无记录或 FAILED → 正常执行，切面在方法成功后更新为 SUCCESS</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @IdempotentGuard(scope = "workflow.advance", keyExpression = "#instanceId + ':' + #skipType")
 * public List<FlowNodeVO> advance(String instanceId, String skipType, ...) {
 *     // 正常业务逻辑
 * }
 * }</pre>
 *
 * <p><b>约束：</b>
 *
 * <ul>
 *   <li>被标记方法必须是 {@code public} 且位于 Spring Bean 中（AOP 代理限制）</li>
 *   <li>{@code keyExpression} 为 SpEL 表达式，{@code #paramName} 引用方法参数</li>
 *   <li>方法返回类型应可序列化 JSON（idempotency 缓存）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.23
 * @see IdempotentGuardAspect
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentGuard {

  /**
   * 幂等作用域（命名空间），用于区分不同业务操作。
   *
   * <p>建议使用 {@code workflow.xxx} 格式，如 {@code workflow.advance}、{@code workflow.start}。
   *
   * @return 作用域字符串
   */
  String scope();

  /**
   * 幂等键 SpEL 表达式，参数引用格式 {@code #paramName}。
   *
   * <p>表达式求值结果应能唯一标识一次操作。实例级操作推荐 {@code #instanceId}；
   * 任务级操作推荐 {@code #taskId}；复合操作推荐 {@code #instanceId + ':' + #actionType}。
   *
   * @return SpEL 表达式
   */
  String keyExpression();

  /**
   * 幂等记录 TTL（分钟），默认 10080 分钟（7 天）。
   *
   * @return TTL 分钟数
   */
  int ttlMinutes() default 10080;

  /**
   * 是否在方法执行前阻塞等待（处理中状态）。默认 false，处理中状态直接抛异常。
   *
   * <p>设为 true 时切面会等待 {@code waitTimeoutMs} 直到状态变为非 PROCESSING。
   *
   * @return 是否阻塞等待
   */
  boolean waitForProcessing() default false;

  /**
   * 等待处理完成超时（毫秒），仅在 {@code waitForProcessing=true} 时生效。默认 5000ms。
   *
   * @return 等待超时毫秒
   */
  long waitTimeoutMs() default 5000L;
}
