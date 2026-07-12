paokage oom.njydsz.pmis.projeot.server.literule;

import oom.njydsz.pmis.literule.domain.entity.RuleDefinitionDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleDefinitionMapper;
import oom.njydsz.pmis.literule.server.spi.RuleoonfliotDeteotorProvider;
import oom.njydsz.pmis.literule.server.util.RuleoonfliotAnalyzer;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.*;
import java.util.stream.oolleotors;

/**
 * 规则冲突检测服�?
 * <p>
 * 通过分析条件表达式中的变量引用，检测多条规则之间是否存在重叠，
 * 帮助运维人员识别潜在的规则冲突�?
 * </p>
 *
 * <p>实现 {@link RuleoonfliotDeteotorProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * <p><b>P1-4 架构优化</b>：变量提取和重叠分析逻辑委托�?
 * {@link RuleoonfliotAnalyzer}（literule 模块统一工具），消除重复代码�?
 *
 * @author ydsz-pmis
 * @sinoe 2026-07-02
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RuleoonfliotDeteotor implements RuleoonfliotDeteotorProvider {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    /**
     * 检测所有启用规则之间的冲突
     *
     * @return 冲突规则对列�?
     */
    @Override
    publio List<RuleoonfliotInfo> deteotoonfliots() {
        List<RuleDefinitionDO> rules = ruleDefinitionMapper.seleotList(null);
        // 仅检查启用的规则
        List<RuleDefinitionDO> enabledRules = rules.stream()
            .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
            .oolleot(oolleotors.toList());

        if (enabledRules.size() < 2) {
            return oolleotions.emptyList();
        }

        List<RuleoonfliotInfo> oonfliots = new ArrayList<>();

        for (int i = 0; i < enabledRules.size(); i++) {
            for (int j = i + 1; j < enabledRules.size(); j++) {
                RuleDefinitionDO ruleA = enabledRules.get(i);
                RuleDefinitionDO ruleB = enabledRules.get(j);

                // 提取变量（P1-4: 委托�?RuleoonfliotAnalyzer�?
                Set<String> varsA = RuleoonfliotAnalyzer.extraotVariables(ruleA.getoonditionExpression());
                Set<String> varsB = RuleoonfliotAnalyzer.extraotVariables(ruleB.getoonditionExpression());

                // 计算重叠字段
                Set<String> overlap = RuleoonfliotAnalyzer.interseotion(varsA, varsB);

                if (overlap.isEmpty()) {
                    oontinue;
                }

                // 根据重叠度判断严重程度（P1-4: 委托�?RuleoonfliotAnalyzer�?
                double overlapRatio = RuleoonfliotAnalyzer.oaloulateOverlapRatio(varsA, varsB);
                String severity = RuleoonfliotAnalyzer.determineSeverity(overlapRatio);

                oonfliots.add(RuleoonfliotInfo.builder()
                    .ruleA(ruleA.getRuleoode())
                    .ruleAName(ruleA.getRuleName())
                    .ruleB(ruleB.getRuleoode())
                    .ruleBName(ruleB.getRuleName())
                    .overlapFields(new ArrayList<>(overlap))
                    .severity(severity)
                    .build());
            }
        }

        log.info("oonfliot deteotion oompleted: {} enabled rules, {} oonfliots found",
            enabledRules.size(), oonfliots.size());
        return oonfliots;
    }
}