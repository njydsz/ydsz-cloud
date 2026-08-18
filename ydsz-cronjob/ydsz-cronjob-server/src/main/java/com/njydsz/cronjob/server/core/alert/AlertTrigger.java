package com.njydsz.cronjob.server.core.alert;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.domain.repository.JobAlertRuleRepository;
import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * 告警触发器（P5 告警 + 监控）。
 *
 * <p>由触发点（Dispatcher、TimeoutMonitor、SlowTaskDetector）调用，封装"规则匹配 + 事件发布"逻辑。 触发点只需构造 {@link
 * AlertContext} 并调用 {@link #trigger(AlertContext)}，无需关心规则查询、 去重、通道派发等细节。
 *
 * <p>P3-1: 新增 {@link #triggerRecovery(AlertContext)} 方法，用于在告警条件解除时 发布 recovery=true 的恢复通知事件。
 *
 * <h3>规则匹配</h3>
 *
 * <ul>
 *   <li>查询 jobId 专属规则（{@code job_id = ?}）
 *   <li>叠加全局规则（{@code job_id IS NULL}）
 *   <li>按 {@code alert_type} 过滤
 * </ul>
 *
 * <p>对每条匹配规则发布一个 {@link AlertEvent}，由 {@link AlertDispatcher} 异步处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTrigger {

  private final JobAlertRuleRepository jobAlertRuleRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final CronjobProperties cronjobProperties;

  /**
   * 告警规则本地缓存（P1-P5）。
   *
   * <p>Key: jobId（可为 null 表示全局规则缓存），Value: 规则列表。 使用 ConcurrentHashMap 实现线程安全，TTL 通过写入时间判断。 规则增删改时由
   * {@link #invalidateAlertRuleCache(String)} 手动失效。
   */
  private final ConcurrentHashMap<String, CacheEntry<List<JobAlertRule>>> ruleCache =
      new ConcurrentHashMap<>();

  /** 缓存时间戳 + 条目（简化 TTL 实现，无需额外依赖） */
  private static final class CacheEntry<T> {
    final long expireAt;
    final T value;

    CacheEntry(T value, long ttlSeconds) {
      this.value = value;
      this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
    }

    boolean isExpired() {
      return System.currentTimeMillis() > expireAt;
    }
  }

  /**
   * 触发告警：查询匹配规则并发布告警事件。
   *
   * <p>同步入口，发布事件后立即返回（事件由 {@link AlertDispatcher} 异步处理）。 触发点可在任务执行 finally 块中安全调用，不会阻塞主流程。
   *
   * @param context 告警上下文
   */
  public void trigger(AlertContext context) {
    if (context == null || context.alertType() == null) {
      return;
    }
    try {
      List<JobAlertRule> rules = findMatchingRules(context);
      if (rules.isEmpty()) {
        log.debug(
            "[AlertTrigger] 无匹配规则, 跳过: alertType={} jobId={}",
            context.alertType(),
            context.jobId());
        return;
      }
      log.info(
          "[AlertTrigger] 触发告警: alertType={} jobId={} matchedRules={}",
          context.alertType(),
          context.jobId(),
          rules.size());
      for (JobAlertRule rule : rules) {
        if (!isRuleMatched(rule, context)) {
          continue;
        }
        AlertEvent event = AlertEvent.of(context, rule);
        eventPublisher.publishEvent(event);
      }
    } catch (Exception e) {
      log.error(
          "[AlertTrigger] 触发告警异常(不影响主流程): alertType={} jobId={} reason={}",
          context.alertType(),
          context.jobId(),
          e.getMessage(),
          e);
    }
  }

  /**
   * P3-1: 触发恢复通知：查询匹配规则并发布恢复事件。
   *
   * <p>当告警条件解除（如任务从失败恢复为成功、慢任务恢复为正常耗时）时调用。 发布的 {@link AlertEvent} 携带 recovery=true 标志，由 {@link
   * AlertDispatcher} 处理时跳过冷却窗口检查，日志 status 带 {@code _RECOVERY} 后缀。
   *
   * <p>规则匹配逻辑与 {@link #trigger(AlertContext)} 不同：
   *
   * <ul>
   *   <li>仅按 {@code alert_type} 匹配（不做阈值判定）
   *   <li>原因：恢复时 triggerValue 通常已低于阈值，正常匹配会失败
   * </ul>
   *
   * @param context 恢复上下文（建议通过 {@link AlertContext#recovery} 工厂方法构造）
   */
  public void triggerRecovery(AlertContext context) {
    if (context == null || context.alertType() == null) {
      return;
    }
    try {
      List<JobAlertRule> rules = findMatchingRules(context);
      if (rules.isEmpty()) {
        log.debug(
            "[AlertTrigger] 无匹配规则, 跳过恢复通知: alertType={} jobId={}",
            context.alertType(),
            context.jobId());
        return;
      }
      log.info(
          "[AlertTrigger] 触发恢复通知: alertType={} jobId={} matchedRules={}",
          context.alertType(),
          context.jobId(),
          rules.size());
      for (JobAlertRule rule : rules) {
        if (!isRuleMatchedForRecovery(rule, context)) {
          continue;
        }
        AlertEvent event = AlertEvent.recovery(context, rule);
        eventPublisher.publishEvent(event);
      }
    } catch (Exception e) {
      log.error(
          "[AlertTrigger] 触发恢复通知异常(不影响主流程): alertType={} jobId={} reason={}",
          context.alertType(),
          context.jobId(),
          e.getMessage(),
          e);
    }
  }

  /**
   * 查询匹配的告警规则（含全局规则）。
   *
   * <p>P1-P5: 使用本地缓存减少高频告警场景下的 DB 查询压力。 缓存 TTL 通过 {@code ydsz.cronjob.alert.rule-cache-ttl-seconds}
   * 配置（默认 60s）。 规则增删改时通过 {@link #invalidateAlertRuleCache(String)} 手动失效。
   */
  private List<JobAlertRule> findMatchingRules(AlertContext context) {
    if (context.jobId() != null) {
      return getCachedOrLoad(
          "job:" + context.jobId(),
          () -> jobAlertRuleRepository.selectByJobIdOrGlobal(context.jobId()));
    }
    return getCachedOrLoad("global", jobAlertRuleRepository::selectAllEnabled);
  }

  /**
   * 从缓存获取规则，未命中或已过期时从 DB 加载并缓存。
   *
   * @param key 缓存 key
   * @param loader DB 加载器
   * @return 规则列表
   */
  private List<JobAlertRule> getCachedOrLoad(
      String key, java.util.function.Supplier<List<JobAlertRule>> loader) {
    CacheEntry<List<JobAlertRule>> entry = ruleCache.get(key);
    if (entry != null && !entry.isExpired()) {
      return entry.value;
    }
    List<JobAlertRule> rules = loader.get();
    if (rules == null) {
      rules = Collections.emptyList();
    }
    int ttl = cronjobProperties.getAlert().getRuleCacheTtlSeconds();
    ruleCache.put(key, new CacheEntry<>(rules, ttl));
    return rules;
  }

  /**
   * 手动失效告警规则缓存。
   *
   * <p>由 {@code AlertServiceImpl} 在规则新增/更新/删除后调用， 确保缓存的一致性。
   *
   * @param jobId 规则对应的 jobId（null 表示全局规则需全部失效）
   */
  public void invalidateAlertRuleCache(String jobId) {
    if (jobId != null) {
      ruleCache.remove("job:" + jobId);
    } else {
      // 无法区分具体 job，全量失效
      ruleCache.clear();
    }
    // 全局规则缓存也需失效（因为 selectByJobIdOrGlobal 包含全局规则）
    ruleCache.remove("global");
    log.debug("[AlertTrigger] 告警规则缓存已失效: jobId={}", jobId);
  }

  /**
   * 判断规则是否匹配当前告警上下文。
   *
   * <p>判定逻辑：
   *
   * <ul>
   *   <li>{@code alert_type} 必须一致
   *   <li>SLOW 类型额外判定 {@code context.triggerValue}（耗时毫秒）&gt;= {@code rule.threshold}
   *   <li>FAIL_RATE / DURATION_P95 类型不在单次触发中判定（需周期性扫描统计）
   * </ul>
   */
  private boolean isRuleMatched(JobAlertRule rule, AlertContext context) {
    AlertType ruleAlertType = AlertType.parse(rule.getAlertType());
    if (ruleAlertType != context.alertType()) {
      return false;
    }
    // SLOW 类型: 额外判定耗时阈值
    if (ruleAlertType == AlertType.SLOW
        && rule.getThreshold() != null
        && context.triggerValue() != null) {
      try {
        long durationMs = Long.parseLong(context.triggerValue());
        if (durationMs < rule.getThreshold()) {
          return false;
        }
      } catch (NumberFormatException e) {
        log.warn(
            "[AlertTrigger] SLOW 告警 triggerValue 非数字: {} ruleId={}",
            context.triggerValue(),
            rule.getId());
        return false;
      }
    }
    return true;
  }

  /**
   * P3-1: 判断规则是否匹配当前恢复上下文。
   *
   * <p>恢复通知的匹配逻辑较告警匹配更宽松：
   *
   * <ul>
   *   <li>{@code alert_type} 必须一致
   *   <li><b>不做阈值判定</b>：恢复时 triggerValue 通常已低于阈值， 若仍按阈值判定将无法匹配到规则，导致恢复通知无法发出
   * </ul>
   *
   * @param rule 告警规则
   * @param context 恢复上下文
   * @return true 表示规则匹配（按 alert_type 维度）
   */
  private boolean isRuleMatchedForRecovery(JobAlertRule rule, AlertContext context) {
    AlertType ruleAlertType = AlertType.parse(rule.getAlertType());
    return ruleAlertType == context.alertType();
  }
}
