package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.literule.entity.RuleDefinitionDO;
import com.njydsz.pmis.literule.mapper.RuleDefinitionMapper;
import com.njydsz.pmis.literule.spi.RuleConflictDetectorProvider;
import com.njydsz.pmis.literule.util.RuleConflictAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则冲突检测服务
 * <p>
 * 通过分析条件表达式中的变量引用，检测多条规则之间是否存在重叠，
 * 帮助运维人员识别潜在的规则冲突。
 * </p>
 *
 * <p>实现 {@link RuleConflictDetectorProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * <p><b>P1-4 架构优化</b>：变量提取和重叠分析逻辑委托到
 * {@link RuleConflictAnalyzer}（literule 模块统一工具），消除重复代码。
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleConflictDetector implements RuleConflictDetectorProvider {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    /**
     * 检测所有启用规则之间的冲突
     *
     * @return 冲突规则对列表
     */
    @Override
    public List<RuleConflictInfo> detectConflicts() {
        List<RuleDefinitionDO> rules = ruleDefinitionMapper.selectList(null);
        // 仅检查启用的规则
        List<RuleDefinitionDO> enabledRules = rules.stream()
            .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
            .collect(Collectors.toList());

        if (enabledRules.size() < 2) {
            return Collections.emptyList();
        }

        List<RuleConflictInfo> conflicts = new ArrayList<>();

        for (int i = 0; i < enabledRules.size(); i++) {
            for (int j = i + 1; j < enabledRules.size(); j++) {
                RuleDefinitionDO ruleA = enabledRules.get(i);
                RuleDefinitionDO ruleB = enabledRules.get(j);

                // 提取变量（P1-4: 委托到 RuleConflictAnalyzer）
                Set<String> varsA = RuleConflictAnalyzer.extractVariables(ruleA.getConditionExpression());
                Set<String> varsB = RuleConflictAnalyzer.extractVariables(ruleB.getConditionExpression());

                // 计算重叠字段
                Set<String> overlap = RuleConflictAnalyzer.intersection(varsA, varsB);

                if (overlap.isEmpty()) {
                    continue;
                }

                // 根据重叠度判断严重程度（P1-4: 委托到 RuleConflictAnalyzer）
                double overlapRatio = RuleConflictAnalyzer.calculateOverlapRatio(varsA, varsB);
                String severity = RuleConflictAnalyzer.determineSeverity(overlapRatio);

                conflicts.add(RuleConflictInfo.builder()
                    .ruleA(ruleA.getRuleCode())
                    .ruleAName(ruleA.getRuleName())
                    .ruleB(ruleB.getRuleCode())
                    .ruleBName(ruleB.getRuleName())
                    .overlapFields(new ArrayList<>(overlap))
                    .severity(severity)
                    .build());
            }
        }

        log.info("Conflict detection completed: {} enabled rules, {} conflicts found",
            enabledRules.size(), conflicts.size());
        return conflicts;
    }
}