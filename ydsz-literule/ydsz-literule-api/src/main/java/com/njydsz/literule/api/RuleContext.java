package com.njydsz.literule.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 规则评估上下文
 *
 * <p>封装规则评估所需的全部输入数据（事实快照），以 key-value 形式提供。 表达式引擎通过变量名从上下文中取值。不可变（防御性拷贝）。
 *
 * <p>1.5.0 起新增 {@code tenantId} 字段，用于运行时租户隔离： {@link
 * com.njydsz.literule.server.core.DefaultRuleEngine} 在评估前会比较 {@code rule.getTenantId()} 与 {@code
 * context.getTenantId()}，仅当两者匹配时才评估该规则。 默认 "1"（单租户部署），向后兼容。
 *
 * <p>1.6.0 起新增 {@code environment} 字段，用于运行时多环境隔离（P1-5）： 与 tenantId 维度正交，支持 dev/staging/prod 环境隔离。
 * 规则的 environment 为 {@link RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境（向后兼容）； 非 "default" 时必须与
 * {@link #getEnvironment()} 完全匹配。 默认 "default"，向后兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class RuleContext implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 默认租户 ID（单租户部署，委托 {@link SystemConstants#DEFAULT_TENANT_ID}） */
  private static final String DEFAULT_TENANT_ID = SystemConstants.DEFAULT_TENANT_ID;

  /** 默认环境（全环境生效，向后兼容） */
  private static final String DEFAULT_ENVIRONMENT = RuleEnvironment.DEFAULT;

  /** 事实数据快照 */
  private final Map<String, Object> facts;

  /** 业务场景标识（如 COCKPIT / BUDGET_CHECK / CLOSURE_ADMISSION） */
  private final String scenario;

  /** 触发来源（如定时任务/接口调用/事件监听，用于审计追踪） */
  private final String source;

  /** 追踪 ID（同一批次评估共享，用于链路追踪） */
  private final String traceId;

  /** 租户 ID（运行时隔离，1.5.0 起） */
  private final String tenantId;

  /** 环境标识（运行时多环境隔离，1.6.0 起） */
  private final String environment;

  /**
   * 表达式求值结果缓存（P2-9 条件冗余计算缓存）
   *
   * <p>{@code transient} 不随上下文序列化；仅在单次 {@code evaluate} 生命周期内有效， 随 {@link RuleContext} 一起被
   * GC，无需额外失效/清理逻辑。 key=表达式字符串，value=该表达式在当前 facts 下的求值结果。 跨规则、同规则内（条件/严重度/模板）重复表达式均可复用，避免冗余计算。
   */
  private transient Map<String, Object> expressionCache;

  private RuleContext(
      Map<String, Object> facts,
      String scenario,
      String source,
      String traceId,
      String tenantId,
      String environment) {
    this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    this.scenario = scenario;
    this.source = source;
    this.traceId = traceId;
    this.tenantId = tenantId;
    this.environment = environment;
  }

  /**
   * 从 Map 构建上下文（指定租户和环境）
   *
   * <p>1.6.0 起支持多环境运行时隔离（P1-5）：引擎评估时按 {@code rule.getEnvironment()} 与 {@code environment} 匹配过滤规则。
   * 规则 environment 为 {@link RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境； 非 "default" 时必须完全匹配。
   *
   * @param facts 事实数据
   * @param scenario 业务场景
   * @param source 触发来源
   * @param traceId 追踪 ID
   * @param tenantId 租户 ID
   * @param environment 环境标识（dev/staging/prod/default）
   * @return RuleContext 实例
   * @since 1.0.0
   */
  public static RuleContext of(
      Map<String, Object> facts,
      String scenario,
      String source,
      String traceId,
      String tenantId,
      String environment) {
    Objects.requireNonNull(facts, "facts 不能为 null");
    String env = (environment == null) ? DEFAULT_ENVIRONMENT : environment;
    return new RuleContext(facts, scenario, source, traceId, tenantId, env);
  }

  /**
   * 从 Map 构建上下文（指定租户）
   *
   * <p>1.5.0 起支持多租户运行时隔离：引擎仅评估 {@code rule.getTenantId() == tenantId} 的规则。 environment 默认 {@link
   * RuleEnvironment#DEFAULT "default"}（向后兼容）。
   *
   * @param facts 事实数据
   * @param scenario 业务场景
   * @param source 触发来源
   * @param traceId 追踪 ID
   * @param tenantId 租户 ID
   * @return RuleContext 实例
   * @since 1.0.0
   */
  public static RuleContext of(
      Map<String, Object> facts, String scenario, String source, String traceId, String tenantId) {
    return of(facts, scenario, source, traceId, tenantId, DEFAULT_ENVIRONMENT);
  }

  /**
   * 从 Map 构建上下文（默认租户 "1"）
   *
   * @param facts 事实数据
   * @param scenario 业务场景
   * @param source 触发来源
   * @param traceId 追踪 ID
   * @return RuleContext 实例
   */
  public static RuleContext of(
      Map<String, Object> facts, String scenario, String source, String traceId) {
    return of(facts, scenario, source, traceId, DEFAULT_TENANT_ID, DEFAULT_ENVIRONMENT);
  }

  /**
   * 从 Map 构建上下文（默认租户 "1"）
   *
   * @param facts 事实数据
   * @param scenario 业务场景
   * @param source 触发来源
   * @return RuleContext 实例
   */
  public static RuleContext of(Map<String, Object> facts, String scenario, String source) {
    return of(
        facts, scenario, source, IdGenerator.nextIdStr(), DEFAULT_TENANT_ID, DEFAULT_ENVIRONMENT);
  }

  /**
   * 从 Map 构建上下文（默认场景为 DEFAULT、租户 "1"）
   *
   * @param facts 事实数据
   * @return RuleContext 实例
   */
  public static RuleContext of(Map<String, Object> facts) {
    return of(
        facts,
        "DEFAULT",
        "UNKNOWN",
        IdGenerator.nextIdStr(),
        DEFAULT_TENANT_ID,
        DEFAULT_ENVIRONMENT);
  }

  /**
   * 获取指定 key 的事实值
   *
   * @param key 事实键
   * @return 事实值；不存在返回 null
   */
  public Object get(String key) {
    return facts.get(key);
  }

  /**
   * 获取全部事实数据（只读）
   *
   * @return 不可修改的 Map
   */
  public Map<String, Object> getFacts() {
    return facts;
  }

  public String getScenario() {
    return scenario;
  }

  public String getSource() {
    return source;
  }

  public String getTraceId() {
    return traceId;
  }

  /**
   * 获取租户 ID
   *
   * <p>引擎评估时仅放行 {@code rule.getTenantId() == this.tenantId} 的规则， 默认 "1"（单租户部署，向后兼容）。
   *
   * @return 租户 ID；默认 "1"
   * @since 1.0.0
   */
  public String getTenantId() {
    return tenantId;
  }

  /**
   * 获取环境标识
   *
   * <p>引擎评估时按 {@code rule.getEnvironment()} 与本字段匹配过滤： 规则 environment 为 {@link
   * RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境； 非 "default" 时必须与本字段完全匹配。默认 "default"（向后兼容）。
   *
   * @return 环境标识；默认 "default"
   * @since 1.0.0
   */
  public String getEnvironment() {
    return environment;
  }

  /**
   * 获取表达式求值结果缓存（P2-9）
   *
   * <p>懒初始化、线程封闭（同一 evaluate 调用链内共享）。用于冗余条件/表达式计算缓存。 仅读取不纳入序列化（{@code transient}）。
   *
   * @return 表达式缓存 Map（key=表达式，value=求值结果）
   * @since 1.0.0
   */
  public Map<String, Object> getExpressionCache() {
    // P0-4 修复：双重检查锁确保线程安全的懒初始化
    // 多线程场景（如 ParallelRuleEvaluator）下可能并发调用此方法
    Map<String, Object> cache = expressionCache;
    if (cache == null) {
      synchronized (this) {
        cache = expressionCache;
        if (cache == null) {
          expressionCache = cache = new ConcurrentHashMap<>();
        }
      }
    }
    return cache;
  }

  /**
   * 清空表达式求值缓存（P2-9）
   *
   * <p>在复用同一 {@link RuleContext} 进行多次独立评估前调用，避免跨批次污染。
   *
   * @since 1.0.0
   */
  public void clearExpressionCache() {
    if (expressionCache != null) {
      expressionCache.clear();
    }
  }

  @Override
  public String toString() {
    return "RuleContext{scenario='"
        + scenario
        + "', source='"
        + source
        + "', tenantId="
        + tenantId
        + ", environment="
        + environment
        + ", facts="
        + facts
        + "}";
  }
}
