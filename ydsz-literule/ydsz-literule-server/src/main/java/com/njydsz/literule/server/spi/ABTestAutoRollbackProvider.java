package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;

/**
 * A/B 测试自动回滚 SPI
 *
 * <p>规则发布后通过 A/B 灰度验证新版本效果，当核心指标（触发率、严重度分布）劣化超过阈值时自动回滚到上一个稳定版本，
 * 同时支持 Owner 主动触发人工回滚作为紧急操作手段。
 *
 * <p>默认实现为 {@code com.njydsz.literule.server.config.DefaultABTestAutoRollbackProvider}（内存策略存储 + 简化评估），
 * 消费方可通过装配自定义实现覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ABTestAutoRollbackProvider {

  /**
   * 获取规则的 A/B 自动回滚策略（无配置时返回默认策略）
   *
   * @param ruleCode 规则编码
   * @return 策略 VO
   */
  RuleABPolicyVO getPolicy(String ruleCode);

  /**
   * 保存（新增或覆盖）规则的 A/B 自动回滚策略
   *
   * @param policy 策略 VO
   * @param operator 操作人
   */
  void savePolicy(RuleABPolicyVO policy, String operator);

  /**
   * 查询规则的回滚历史（按时间倒序）
   *
   * @param ruleCode 规则编码
   * @return 回滚历史 VO 列表
   */
  List<RuleABRollbackVO> listRollbackHistory(String ruleCode);

  /**
   * 主动触发一次 A/B 评估（人工立即检查）
   *
   * <p>基于策略配置的阈值与规则近期指标判断是否需要回滚。 默认实现仅在策略启用且样本量达标时返回 true。
   *
   * @param ruleCode 规则编码
   * @return true=检测到劣化需要回滚；false=正常
   */
  boolean evaluateOne(String ruleCode);

  /**
   * 人工回滚（Owner 主动请求 / 紧急操作）
   *
   * <p>回滚到上一个稳定版本并记录回滚历史。
   *
   * @param ruleCode 规则编码
   * @param operator 操作人
   * @param reason 回滚原因（MANUAL / OWNER_REQUEST 等）
   * @return 回滚记录 VO
   */
  RuleABRollbackVO manualRollback(String ruleCode, String operator, String reason);
}
