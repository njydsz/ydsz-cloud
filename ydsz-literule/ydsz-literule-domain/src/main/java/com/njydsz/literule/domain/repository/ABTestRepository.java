package com.njydsz.literule.domain.repository;

import java.util.List;

import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;

/**
 * A/B 测试策略与回滚历史仓库接口（DDD domain 层）
 *
 * <p>定义 A/B 测试自动回滚策略与回滚历史的持久化操作。 默认由 server 层提供内存实现（{@code
 * com.njydsz.literule.server.config.DefaultABTestRepository}）， 消费方可提供自定义实现（如基于 MyBatis 的数据库存储）以替代默认存储。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ABTestRepository {

  /**
   * 查询规则的 A/B 自动回滚策略
   *
   * @param ruleCode 规则编码
   * @return 策略 VO；不存在时返回 null
   */
  RuleABPolicyVO findPolicy(String ruleCode);

  /**
   * 保存（新增或覆盖）规则的 A/B 自动回滚策略
   *
   * @param policy 策略 VO（ruleCode 必填）
   * @param operator 操作人
   */
  void savePolicy(RuleABPolicyVO policy, String operator);

  /**
   * 查询规则的回滚历史（按时间倒序）
   *
   * @param ruleCode 规则编码
   * @return 回滚历史 VO 列表
   */
  List<RuleABRollbackVO> listRollbacks(String ruleCode);

  /**
   * 记录一次回滚事件
   *
   * @param rollback 回滚记录 VO
   */
  void saveRollback(RuleABRollbackVO rollback);
}
