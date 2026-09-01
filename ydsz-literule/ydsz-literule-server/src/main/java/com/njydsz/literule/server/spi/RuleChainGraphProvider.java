package com.njydsz.literule.server.spi;

import com.njydsz.literule.server.orchestrator.RuleChainGraph;

/**
 * 规则链画布提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，提供画布的 CRUD 与持久化能力。 将原有 {@code RuleChainGraphService} 的能力抽象为 SPI，避免
 * literule 模块直接依赖 project 模块。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface RuleChainGraphProvider {

  /**
   * 查询指定规则的画布
   *
   * @param ruleCode 规则编码
   * @return 画布；不存在返回 null
   */
  RuleChainGraph getByRuleCode(String ruleCode);

  /**
   * 保存或更新画布
   *
   * @param ruleCode 规则编码
   * @param graph 画布
   * @param operator 操作人
   * @return 保存后的画布
   */
  RuleChainGraph save(String ruleCode, RuleChainGraph graph, String operator);

  /**
   * 删除画布
   *
   * @param ruleCode 规则编码
   * @return true=有删除，false=无记录
   */
  boolean delete(String ruleCode);
}
