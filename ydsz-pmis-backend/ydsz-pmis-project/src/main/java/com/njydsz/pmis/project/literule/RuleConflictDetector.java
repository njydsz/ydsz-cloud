package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.literule.entity.RuleDefinitionDO;
import com.njydsz.pmis.literule.mapper.RuleDefinitionMapper;
import com.njydsz.pmis.literule.spi.RuleConflictDetectorProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleConflictDetector implements RuleConflictDetectorProvider {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    /** 提取变量名的正则 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\b([a-zA-Z_]\\w*)\\b");

    /** 关键字/函数名，非变量 */
    private static final Set<String> KEYWORDS = Set.of(
        "true", "false", "nil", "null",
        "RED", "YELLOW", "INFO", "GREEN",
        "if", "else", "return", "seq", "lambda",
        "println", "print", "p", "string", "long", "double",
        "boolean", "int", "math", "Math", "max", "min", "abs",
        "round", "floor", "ceil", "sqrt", "pow", "log",
        "contains", "startsWith", "endsWith", "length",
        "count", "sum", "avg", "rand", "now", "date"
    );

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

                // 提取变量
                Set<String> varsA = extractVariables(ruleA.getConditionExpression());
                Set<String> varsB = extractVariables(ruleB.getConditionExpression());

                // 计算重叠字段
                Set<String> overlap = new HashSet<>(varsA);
                overlap.retainAll(varsB);

                if (overlap.isEmpty()) {
                    continue;
                }

                // 根据重叠度判断严重程度
                int totalVars = varsA.size() + varsB.size();
                double overlapRatio = totalVars > 0 ? (double) (2 * overlap.size()) / totalVars : 0;

                String severity;
                if (overlapRatio >= 0.8) {
                    severity = "high";
                } else if (overlapRatio >= 0.4) {
                    severity = "medium";
                } else {
                    severity = "low";
                }

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

    /**
     * 从表达式文本中提取变量名
     */
    private Set<String> extractVariables(String expression) {
        if (expression == null || expression.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> vars = new HashSet<>();
        Matcher matcher = VAR_PATTERN.matcher(expression);
        while (matcher.find()) {
            String word = matcher.group(1);
            // 过滤关键字、数字、单字符
            if (KEYWORDS.contains(word)) continue;
            if (word.matches("\\d+")) continue;
            if (word.length() <= 1) continue;
            // 保留首字母小写的标识符（驼峰变量名）
            if (Character.isLowerCase(word.charAt(0)) || word.contains("_")) {
                vars.add(word);
            }
        }
        return vars;
    }
}