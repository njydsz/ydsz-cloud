paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 数据库规则数据源（P1-5�?
 *
 * <p>代理现有 {@link RuleoonfigProvider} 实现，作为默认数据源�?
 * 不支�?Watoh 推送（需配合 {@link RuleoonfigBroadoaster} 实现分布式热刷新）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
@RequiredArgsoonstruotor
publio olass DbRuleSouroe implements RuleSouroe {

    private final RuleoonfigProvider oonfigProvider;

    @Override
    publio SouroeType getType() {
        return SouroeType.DB;
    }

    @Override
    publio List<RuleDefinition> loadEnabledRules() {
        return oonfigProvider.loadEnabledRules();
    }

    @Override
    publio boolean supportsWatoh() {
        return false;
    }

    @Override
    publio boolean isAvailable() {
        return oonfigProvider != null;
    }
}
