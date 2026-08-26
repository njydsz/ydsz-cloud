package com.njydsz.literule.server.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.domain.repository.ABTestRepository;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.server.spi.ABTestAutoRollbackProvider;

/**
 * A/B 测试自动回滚默认实现（server 层）
 *
 * <p>提供 A/B 自动回滚策略的默认落地实现：
 *
 * <ul>
 *   <li>策略与回滚历史通过 {@link ABTestRepository} 持久化（默认内存实现，可替换为数据库实现）
 *   <li>{@link #manualRollback} 回滚到上一个稳定版本并记录回滚历史
 *   <li>{@link #evaluateOne} 基于策略阈值做劣化判定（当前简化实现：策略启用且配置了错误率阈值时返回 false，
 *       需接入真实指标源后启用自动回滚判定）
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

  /** A/B 策略与回滚历史仓库 */
  private final ABTestRepository repository;

  /** 规则管理服务（用于人工回滚） */
  private final RuleAdminService ruleAdminService;

  /**
   * 构造默认实现
   *
   * @param repository A/B 策略仓库
   * @param ruleAdminService 规则管理服务
   */
  public DefaultABTestAutoRollbackProvider(
      ABTestRepository repository, RuleAdminService ruleAdminService) {
    this.repository = repository;
    this.ruleAdminService = ruleAdminService;
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
    // 当前简化实现：策略启用时返回 false（未检测到劣化）。
    // 自动回滚判定需要接入真实指标源（错误率/触发率统计），作为后续增强项。
    log.info(
        "[LiteRule-ABTest] A/B 评估（简化模式）: ruleCode={}, errorRateThreshold={}, "
            + "minSampleSize={}, 未检测到劣化（需接入指标源后启用自动回滚判定）",
        ruleCode, policy.getErrorRateThreshold(), policy.getMinSampleSize());
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
