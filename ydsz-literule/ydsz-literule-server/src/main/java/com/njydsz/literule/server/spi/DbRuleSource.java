package com.njydsz.literule.server.spi;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.RuleDefinition;

/**
 * 数据库规则数据源
 *
 * <p>代理现有 {@link RuleConfigProvider} 实现，作为默认数据源。不支持 Watch 推送（需配合 {@link
 * RuleConfigBroadcaster} 实现分布式热刷新）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@RequiredArgsConstructor
public class DbRuleSource implements RuleConfigProvider {

  private final RuleConfigProvider configProvider;

  @Override
  public List<RuleDefinition> loadEnabledRules() {
    return configProvider.loadEnabledRules();
  }

  @Override
  public List<RuleDefinition> loadAllRules() {
    return configProvider.loadAllRules();
  }

  @Override
  public RuleDefinition save(RuleDefinition definition, String operator) {
    return configProvider.save(definition, operator);
  }

  @Override
  public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
    configProvider.toggleEnabled(ruleCode, enabled, operator);
  }

  @Override
  public RuleDefinition findByCode(String ruleCode) {
    return configProvider.findByCode(ruleCode);
  }

  @Override
  public SourceType getType() {
    return SourceType.DB;
  }

  @Override
  public boolean supportsWatch() {
    return false;
  }

  @Override
  public boolean isAvailable() {
    return configProvider != null;
  }
}
