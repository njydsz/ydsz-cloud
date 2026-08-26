package com.njydsz.literule.server.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.domain.repository.ABTestRepository;
import com.njydsz.literule.domain.repository.RuleExecutionTraceRepository;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.server.spi.ABTestAutoRollbackProvider;

/**
 * A/B 测试自动回滚默认实现（server 层）
 *
 * <p>提供 A/B 自动回滚策略的默认落地实现：
 *
 * <ul>
 *   <li>策略与回滚历史通过 {@link ABTestRepository} 持久化（默认内存实现，可替换为数据库实现）
 *   <li>{@link #manualRollback} 回滚到上一个稳定版本并记录回滚历史
 *   <li>{@link #evaluateOne} 基于<b>执行轨迹真实指标</b>做劣化判定（P0-2）：
 *       统计最近 {@code checkWindowMinutes} 分钟内该规则的执行轨迹，当样本量 ≥ {@code minSampleSize}
 *       且错误率 ≥ {@code errorRateThreshold} 时判定劣化、返回 true（触发自动回滚）。
 * </ul>
 *
 * <h3>场景差异</h3>
 *
 * <ul>
 *   <li><b>非嵌入式场景</b>（数据库可用，{@link RuleExecutionTraceRepository} 已注入）：
 *       {@link #evaluateOne} 正常工作，基于轨迹错误率判定劣化。
 *   <li><b>嵌入式场景</b>（无持久化，{@link RuleExecutionTraceRepository} 未注入）：
 *       {@link #evaluateOne} 返回 false 并打印 INFO 日志，自动回滚不可用。
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DefaultABTestAutoRollbackProvider implements ABTestAutoRollbackProvider {

  /** 默认错误率阈值 */
  private static final BigDecimal DEFAULT_ERROR_RATE_THRESHOLD = new BigDecimal("0.30");

  /** 默认最小样本量 */
  private static final int DEFAULT_MIN_SAMPLE_SIZE = 500;

  /** 默认评估窗口（分钟） */
  private static final int DEFAULT_CHECK_WINDOW_MINUTES = 30;

  /** 单次评估拉取的轨迹样本上限（防内存/IO 放大） */
  private static final int SAMPLE_CAP = 2000;

  /** 错误率计算保留小数位 */
  private static final int ERROR_RATE_SCALE = 4;

  /** A/B 策略与回滚历史仓库 */
  private final ABTestRepository repository;

  /** 规则管理服务（用于人工回滚） */
  private final RuleAdminService ruleAdminService;

  /** 执行轨迹仓库（真实指标源，P0-2；可为 null = 嵌入式无持久化场景） */
  private final RuleExecutionTraceRepository traceRepository;

  /**
   * 构造默认实现
   *
   * @param repository A/B 策略仓库
   * @param ruleAdminService 规则管理服务
   */
  public DefaultABTestAutoRollbackProvider(
      ABTestRepository repository, RuleAdminService ruleAdminService) {
    this(repository, ruleAdminService, null);
  }

  /**
   * 构造默认实现（支持注入执行轨迹仓库）
   *
   * @param repository A/B 策略仓库
   * @param ruleAdminService 规则管理服务
   * @param traceRepository 执行轨迹仓库（可为 null）
   */
  public DefaultABTestAutoRollbackProvider(
      ABTestRepository repository,
      RuleAdminService ruleAdminService,
      RuleExecutionTraceRepository traceRepository) {
    this.repository = repository;
    this.ruleAdminService = ruleAdminService;
    this.traceRepository = traceRepository;
  }

  @Override
  public RuleABPolicyVO getPolicy(String ruleCode) {
    RuleABPolicyVO policy = repository.findPolicy(ruleCode);
    if (policy != null) {
      return policy;
    }
    // 无配置时返回默认策略（关闭状态）
    RuleABPolicyVO defaults = new RuleABPolicyVO();
    defaults.setRuleCode(ruleCode);
    defaults.setAutoRollbackEnabled(false);
    defaults.setErrorRateThreshold(DEFAULT_ERROR_RATE_THRESHOLD);
    defaults.setMinSampleSize(DEFAULT_MIN_SAMPLE_SIZE);
    defaults.setCheckWindowMinutes(DEFAULT_CHECK_WINDOW_MINUTES);
    return defaults;
  }

  @Override
  public void savePolicy(RuleABPolicyVO policy, String operator) {
    repository.savePolicy(policy, operator);
    log.info("[LiteRule-ABTest] A/B 回滚策略已保存: ruleCode={}, autoRollbackEnabled={}, operator={}",
        policy.getRuleCode(), policy.getAutoRollbackEnabled(), operator);
  }

  @Override
  public List<RuleABRollbackVO> listRollbackHistory(String ruleCode) {
    return repository.listRollbacks(ruleCode);
  }

  @Override
  public boolean evaluateOne(String ruleCode) {
    RuleABPolicyVO policy = repository.findPolicy(ruleCode);
    if (policy == null || !Boolean.TRUE.equals(policy.getAutoRollbackEnabled())) {
      return false;
    }
    if (traceRepository == null) {
      log.info(
          "[LiteRule-ABTest] A/B 评估跳过（未注入执行轨迹仓库，自动回滚不可用）: ruleCode={}",
          ruleCode);
      return false;
    }

    BigDecimal threshold =
        policy.getErrorRateThreshold() != null
            ? policy.getErrorRateThreshold()
            : DEFAULT_ERROR_RATE_THRESHOLD;
    int minSample =
        policy.getMinSampleSize() != null
            ? Math.max(1, policy.getMinSampleSize())
            : DEFAULT_MIN_SAMPLE_SIZE;
    int windowMinutes =
        policy.getCheckWindowMinutes() != null
            ? Math.max(1, policy.getCheckWindowMinutes())
            : DEFAULT_CHECK_WINDOW_MINUTES;

    // 拉取最近轨迹样本，按窗口过滤
    LocalDateTime windowStart = LocalDateTime.now().minusMinutes(windowMinutes);
    List<RuleExecutionTraceVO> recent = traceRepository.findRecentByRuleCode(ruleCode, SAMPLE_CAP);
    long total = 0;
    long errors = 0;
    for (RuleExecutionTraceVO trace : recent) {
      if (trace == null || trace.getCreatedAt() == null || trace.getCreatedAt().isBefore(windowStart)) {
        continue;
      }
      total++;
      if (trace.getErrorMessage() != null && !trace.getErrorMessage().isBlank()) {
        errors++;
      }
    }

    if (total < minSample) {
      log.info(
          "[LiteRule-ABTest] A/B 评估样本不足，暂不判定: ruleCode={}, 窗口内样本={}, 最小样本={}",
          ruleCode, total, minSample);
      return false;
    }

    BigDecimal errorRate =
        BigDecimal.valueOf(errors)
            .divide(BigDecimal.valueOf(total), ERROR_RATE_SCALE, RoundingMode.HALF_UP);
    if (errorRate.compareTo(threshold) >= 0) {
      log.warn(
          "[LiteRule-ABTest] A/B 检测到劣化，建议自动回滚: ruleCode={}, errorRate={}, threshold={}, "
              + "窗口样本={}, 窗口={}分钟",
          ruleCode, errorRate, threshold, total, windowMinutes);
      return true;
    }
    log.info(
        "[LiteRule-ABTest] A/B 评估正常: ruleCode={}, errorRate={}, threshold={}, 窗口样本={}",
        ruleCode, errorRate, threshold, total);
    return false;
  }

  @Override
  public RuleABRollbackVO manualRollback(String ruleCode, String operator, String reason) {
    // 回滚到上一个稳定版本（当前版本 - 1）
    RuleDefinition current = ruleAdminService.getByCode(ruleCode);
    if (current == null) {
      throw new IllegalStateException("人工回滚失败：规则不存在，ruleCode=" + ruleCode);
    }
    int currentVersion = current.getVersion() > 1 ? current.getVersion() : 2;
    int targetVersion = Math.max(1, currentVersion - 1);
    Optional<RuleDefinitionVO> rolledBack =
        ruleAdminService.rollback(ruleCode, targetVersion, operator);
    if (rolledBack.isEmpty()) {
      log.warn("[LiteRule-ABTest] 人工回滚失败: ruleCode={}, targetVersion={}, operator={}",
          ruleCode, targetVersion, operator);
      throw new IllegalStateException(
          "人工回滚失败：目标版本不存在，ruleCode=" + ruleCode + ", targetVersion=" + targetVersion);
    }

    RuleABRollbackVO record = new RuleABRollbackVO();
    record.setRuleCode(ruleCode);
    record.setTriggerReason(reason);
    record.setFromCanary(Boolean.FALSE);
    record.setOperator(operator);
    record.setNotifyStatus("SKIPPED");
    record.setCreatedAt(LocalDateTime.now());
    repository.saveRollback(record);
    log.info("[LiteRule-ABTest] 人工回滚完成: ruleCode={}, reason={}, operator={}",
        ruleCode, reason, operator);
    return record;
  }
}
