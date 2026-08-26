package com.njydsz.literule.server.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.literule.domain.repository.ABTestRepository;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;

/**
 * A/B 测试默认内存仓库实现（server 层）
 *
 * <p>策略与回滚历史默认使用内存 Map 存储（进程内，重启丢失）。 消费方可通过装配自定义 {@link ABTestRepository} 实现
 * （如基于 MyBatis 的数据库存储）替代默认实现，实现持久化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultABTestRepository implements ABTestRepository {

  /** 回滚历史初始容量 */
  private static final int INITIAL_ROLLBACK_CAPACITY = 4;

  /** 策略存储：ruleCode → 策略 VO */
  private final Map<String, RuleABPolicyVO> policies = new ConcurrentHashMap<>();

  /** 回滚历史存储：ruleCode → 回滚记录列表（新记录在前） */
  private final Map<String, List<RuleABRollbackVO>> rollbacks = new ConcurrentHashMap<>();

  @Override
  public RuleABPolicyVO findPolicy(String ruleCode) {
    return ruleCode == null ? null : policies.get(ruleCode);
  }

  @Override
  public void savePolicy(RuleABPolicyVO policy, String operator) {
    if (policy == null || policy.getRuleCode() == null) {
      return;
    }
    RuleABPolicyVO merged = new RuleABPolicyVO();
    merged.setRuleCode(policy.getRuleCode());
    merged.setAutoRollbackEnabled(
        policy.getAutoRollbackEnabled() != null && policy.getAutoRollbackEnabled());
    merged.setRollbackAction(policy.getRollbackAction());
    merged.setErrorRateThreshold(policy.getErrorRateThreshold());
    merged.setMinSampleSize(policy.getMinSampleSize());
    merged.setCheckWindowMinutes(policy.getCheckWindowMinutes());
    merged.setNotifyChannels(policy.getNotifyChannels());
    merged.setDescription(policy.getDescription());
    merged.setCreatedBy(policy.getCreatedBy() != null ? policy.getCreatedBy() : operator);
    merged.setCreatedAt(policy.getCreatedAt() != null ? policy.getCreatedAt() : LocalDateTime.now());
    merged.setUpdatedBy(operator);
    merged.setUpdatedAt(LocalDateTime.now());
    policies.put(policy.getRuleCode(), merged);
  }

  @Override
  public List<RuleABRollbackVO> listRollbacks(String ruleCode) {
    List<RuleABRollbackVO> list = rollbacks.get(ruleCode);
    return list == null ? List.of() : List.copyOf(list);
  }

  @Override
  public void saveRollback(RuleABRollbackVO rollback) {
    if (rollback == null || rollback.getRuleCode() == null) {
      return;
    }
    rollbacks.compute(
        rollback.getRuleCode(),
        (key, list) -> {
          List<RuleABRollbackVO> target = list == null ? new ArrayList<>(INITIAL_ROLLBACK_CAPACITY) : new ArrayList<>(list);
          target.add(0, rollback);
          target.sort(Comparator.comparing(RuleABRollbackVO::getCreatedAt).reversed());
          return target;
        });
  }
}
