package com.njydsz.literule.server.spi;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.api.RuleDefinition;

/**
 * 数据库规则数据源（P1-5）
 *
 * <p>代理现有 {@link RuleConfigProvider} 实现，作为默认数据源。
 * 不支持 Watch 推送（需配合 {@link RuleConfigBroadcaster} 实现分布式热刷新）。
 *
 * @since 1.0.0
 * @author ydsz-team
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
