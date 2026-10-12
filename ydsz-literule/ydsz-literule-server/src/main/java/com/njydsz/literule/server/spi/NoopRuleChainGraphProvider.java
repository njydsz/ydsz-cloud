package com.njydsz.literule.server.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.literule.server.orchestrator.RuleChainGraph;

/**
 * 规则链画布提供者 SPI 的默认空实现。
 *
 * <p>当消费方（如项目模块）未提供 {@link RuleChainGraphProvider} 的自定义实现时，
 * 回退到此默认 Bean，所有方法均为空操作：查询返回 null，保存返回 入参对象，删除返回 false。
 *
 * <p>此 Bean 仅作为兜底，确保 {@code @RequiredArgsConstructor} 注入不会因缺少实现而启动失败。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class NoopRuleChainGraphProvider implements RuleChainGraphProvider {

  /**
   * 查询指定规则的画布。
   *
   * <p>默认实现始终返回 null（无持久化画布数据）。
   *
   * @param ruleCode 规则编码
   * @return null
   */
  @Override
  public RuleChainGraph getByRuleCode(String ruleCode) {
    log.debug("[LiteRule-Graph] NoopRuleChainGraphProvider.getByRuleCode({}) — 无持久化画布", ruleCode);
    return null;
  }

  /**
   * 保存或更新画布。
   *
   * <p>默认实现直接返回入参对象（无持久化）。
   *
   * @param ruleCode 规则编码
   * @param graph 画布
   * @param operator 操作人
   * @return 入参 graph 本身
   */
  @Override
  public RuleChainGraph save(String ruleCode, RuleChainGraph graph, String operator) {
    log.debug("[LiteRule-Graph] NoopRuleChainGraphProvider.save({}) — 无持久化，返回原对象", ruleCode);
    return graph;
  }

  /**
   * 删除画布。
   *
   * <p>默认实现始终返回 false（无持久化数据可删除）。
   *
   * @param ruleCode 规则编码
   * @return false
   */
  @Override
  public boolean delete(String ruleCode) {
    log.debug("[LiteRule-Graph] NoopRuleChainGraphProvider.delete({}) — 无持久化数据", ruleCode);
    return false;
  }
}
