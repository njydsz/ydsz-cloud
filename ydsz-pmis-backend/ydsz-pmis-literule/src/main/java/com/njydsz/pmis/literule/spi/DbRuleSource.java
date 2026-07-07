package com.njydsz.pmis.literule.spi;

import com.njydsz.pmis.literule.api.RuleDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 数据库规则数据源（P1-5）
 *
 * <p>代理现有 {@link RuleConfigProvider} 实现，作为默认数据源。
 * 不支持 Watch 推送（需配合 {@link RuleConfigBroadcaster} 实现分布式热刷新）。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
@RequiredArgsConstructor
public class DbRuleSource implements RuleSource {

    private final RuleConfigProvider configProvider;

    @Override
    public SourceType getType() {
        return SourceType.DB;
    }

    @Override
    public List<RuleDefinition> loadEnabledRules() {
        return configProvider.loadEnabledRules();
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
