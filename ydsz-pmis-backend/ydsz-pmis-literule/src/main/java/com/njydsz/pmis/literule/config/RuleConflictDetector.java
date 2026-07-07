package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则冲突检测器
 *
 * <p>在规则保存前检测新规则与现有规则的潜在冲突，输出 {@link RuleConflict} 列表。
 *
 * <p>检测维度（1.5.0 增强表达式归一化与范围重叠分析）：
 * <ul>
 *   <li>{@link RuleConflict.Type#IDENTICAL_CONDITION}：同 category + 同 tenantId 下，
 *       条件表达式归一化后完全相同（WARN，可能重复定义）</li>
 *   <li>{@link RuleConflict.Type#CONTRADICTORY_SEVERITY}：条件表达式相同但严重度不同
 *       （ERROR，语义冲突）</li>
 *   <li>{@link RuleConflict.Type#NAME_COLLISION}：同 category + 同 tenantId 下，
 *       name 相同但条件表达式不同（WARN，命名冲突）</li>
 *   <li>{@link RuleConflict.Type#CONDITION_OVERLAP}：条件范围重叠（WARN），
 *       两条规则在同一变量上存在范围交集，可能导致同一事实同时命中（1.5.0 起）</li>
 * </ul>
 *
 * <p><b>表达式归一化（1.5.0 增强）</b>：
 * <ul>
 *   <li>去除所有空白字符</li>
 *   <li>统一逻辑运算符：{@code and}→{@code &&}、{@code or}→{@code ||}、{@code not}→{@code !}</li>
 *   <li>统一大小写</li>
 *   <li>翻转比较操作数顺序：{@code 3 &lt; x} → {@code x &gt; 3}（规范化为 变量在左、常量在右）</li>
 * </ul>
 *
 * <p><b>条件重叠分析（1.5.0 新增）</b>：
 * 仅对简单比较表达式（{@code var OP number}）做范围交集检测，
 * 复杂表达式（含 &amp;&amp; / || 或嵌套）降级为不检测，避免误报。
 * 同互斥组内的规则不报重叠（互斥组本身保证短路）。
 *
 * <p>租户隔离：仅在同一 tenantId 内检测冲突（单租户部署下 tenantId 恒为 1）。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RequiredArgsConstructor
public class RuleConflictDetector {

    private final RuleConfigProvider configProvider;

    /** 简单比较表达式模式：var OP number（用于范围重叠分析） */
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "^([a-zA-Z_]\\w*)\\s*(>=|<=|>|<|==|!=)\\s*(-?\\d+(?:\\.\\d+)?)$");

    /** 反向比较表达式模式：number OP var（如 "3 < x"） */
    private static final Pattern REVERSE_COMPARISON_PATTERN = Pattern.compile(
            "^(-?\\d+(?:\\.\\d+)?)\\s*(>=|<=|>|<|==|!=)\\s*([a-zA-Z_]\\w*)$");

    /**
     * 检测新规则与所有现有规则的冲突
     *
     * @param newDefinition 待保存的新规则定义
     * @return 冲突列表；无冲突返回空列表
     */
    public List<RuleConflict> detect(RuleDefinition newDefinition) {
        List<RuleConflict> conflicts = new ArrayList<>();
        List<RuleDefinition> existingRules;
        try {
            existingRules = configProvider.loadAllRules();
        } catch (Exception e) {
            log.warn("[LiteRule-Conflict] 加载现有规则失败，跳过冲突检测: {}", e.getMessage());
            return conflicts;
        }

        String newCode = newDefinition.getCode();
        String newTenantId = newDefinition.getTenantId();
        String newCategory = newDefinition.getCategory();
        String newName = newDefinition.getName();
        String newConditionRaw = newDefinition.getConditionExpression();
        String newCondition = normalize(newConditionRaw);
        String newSeverity = severityKey(newDefinition);
        String newMutexGroup = newDefinition.getMutexGroup();

        // 解析新规则的简单比较条件（用于范围重叠分析）
        ComparisonCondition newComparison = parseComparison(newConditionRaw);

        // ===== 单规则自检（1.5.1 新增）=====
        // 1. 死规则检测：复合条件中同变量存在矛盾范围
        detectDeadRule(conflicts, newDefinition);
        // 2. 子条件不可达检测：复合条件中被包含的冗余子句
        detectUnreachableSubcondition(conflicts, newDefinition);

        for (RuleDefinition other : existingRules) {
            if (Objects.equals(other.getCode(), newCode)) continue;
            if (!Objects.equals(other.getTenantId(), newTenantId)) continue;

            String otherCondition = normalize(other.getConditionExpression());
            String otherSeverity = severityKey(other);

            // 1. 条件表达式归一化后完全相同
            boolean sameCondition = newCondition != null
                    && newCondition.equals(otherCondition)
                    && !newCondition.isEmpty();

            if (sameCondition) {
                if (!Objects.equals(newSeverity, otherSeverity)) {
                    conflicts.add(RuleConflict.builder()
                            .type(RuleConflict.Type.CONTRADICTORY_SEVERITY)
                            .level(RuleConflict.Level.ERROR)
                            .newRuleCode(newCode)
                            .conflictingRuleCode(other.getCode())
                            .description("条件表达式与规则 " + other.getCode()
                                    + " 完全相同，但严重度不同（" + newSeverity + " vs " + otherSeverity
                                    + "），存在语义冲突")
                            .build());
                } else {
                    conflicts.add(RuleConflict.builder()
                            .type(RuleConflict.Type.IDENTICAL_CONDITION)
                            .level(RuleConflict.Level.WARN)
                            .newRuleCode(newCode)
                            .conflictingRuleCode(other.getCode())
                            .description("条件表达式与规则 " + other.getCode() + " 完全相同，可能为重复定义")
                            .build());
                }
                continue;
            }

            // 2. 同 category 下名称相同但条件不同
            if (Objects.equals(newCategory, other.getCategory())
                    && newName != null && newName.equals(other.getName())
                    && !newName.isEmpty()) {
                conflicts.add(RuleConflict.builder()
                        .type(RuleConflict.Type.NAME_COLLISION)
                        .level(RuleConflict.Level.WARN)
                        .newRuleCode(newCode)
                        .conflictingRuleCode(other.getCode())
                        .description("规则名称 '" + newName + "' 在类别 " + newCategory
                                + " 下与规则 " + other.getCode() + " 重名，但条件不同")
                        .build());
            }

            // 3. 条件范围重叠（1.5.0 新增）
            if (newComparison != null) {
                ComparisonCondition otherComparison = parseComparison(other.getConditionExpression());
                if (otherComparison != null && isOverlap(newComparison, otherComparison, newMutexGroup, other.getMutexGroup())) {
                    conflicts.add(RuleConflict.builder()
                            .type(RuleConflict.Type.CONDITION_OVERLAP)
                            .level(RuleConflict.Level.WARN)
                            .newRuleCode(newCode)
                            .conflictingRuleCode(other.getCode())
                            .description("条件范围与规则 " + other.getCode() + " 在变量 '"
                                    + newComparison.variable + "' 上存在重叠（"
                                    + newComparison.original + " vs " + otherComparison.original
                                    + "），可能导致同一事实同时命中两条规则")
                            .build());
                }
            }
        }

        return conflicts;
    }

    /**
     * 死规则检测：复合 AND 条件中同一变量存在矛盾范围
     *
     * <p>检测形如 {@code x > 10 && x < 5} 的永假条件。
     * 仅对 AND 连接的简单比较做检测，OR/嵌套表达式降级为不检测。
     *
     * @param conflicts     冲突输出列表
     * @param newDefinition 待检测规则
     * @since 1.5.1
     */
    private void detectDeadRule(List<RuleConflict> conflicts, RuleDefinition newDefinition) {
        String expr = newDefinition.getConditionExpression();
        if (expr == null || expr.isBlank()) return;
        // 拆分 AND 子句（支持 && 和 and）
        List<String> clauses = splitAndClauses(expr);
        if (clauses.size() < 2) return;
        // 按变量分组收集比较条件
        Map<String, List<ComparisonCondition>> byVar = new LinkedHashMap<>();
        for (String clause : clauses) {
            ComparisonCondition cc = parseComparison(clause.trim());
            if (cc != null) {
                byVar.computeIfAbsent(cc.variable, k -> new ArrayList<>()).add(cc);
            }
        }
        // 检查同变量的条件范围是否无交集
        for (Map.Entry<String, List<ComparisonCondition>> entry : byVar.entrySet()) {
            List<ComparisonCondition> conds = entry.getValue();
            if (conds.size() < 2) continue;
            // 取所有条件的范围交集
            double lower = -Double.MAX_VALUE;
            double upper = Double.MAX_VALUE;
            for (ComparisonCondition cc : conds) {
                double[] range = toRange(cc.operator, cc.value);
                if (range[0] > lower) lower = range[0];
                if (range[1] < upper) upper = range[1];
            }
            if (lower > upper) {
                conflicts.add(RuleConflict.builder()
                        .type(RuleConflict.Type.DEAD_RULE)
                        .level(RuleConflict.Level.ERROR)
                        .newRuleCode(newDefinition.getCode())
                        .conflictingRuleCode(newDefinition.getCode())
                        .description("规则条件为死规则：变量 '" + entry.getKey()
                                + "' 的范围条件存在矛盾（" + conds.stream()
                                    .map(c -> c.original).reduce((a, b) -> a + " && " + b).orElse("")
                                + "），永远不会触发")
                        .build());
            }
        }
    }

    /**
     * 子条件不可达检测：AND 条件中被包含的冗余子句
     *
     * <p>检测形如 {@code x > 5 && x > 3} 中 {@code x > 3} 被 {@code x > 5} 完全包含的情况，
     * 该子句恒为冗余。
     *
     * @param conflicts     冲突输出列表
     * @param newDefinition 待检测规则
     * @since 1.5.1
     */
    private void detectUnreachableSubcondition(List<RuleConflict> conflicts, RuleDefinition newDefinition) {
        String expr = newDefinition.getConditionExpression();
        if (expr == null || expr.isBlank()) return;
        List<String> clauses = splitAndClauses(expr);
        if (clauses.size() < 2) return;
        // 按变量分组
        Map<String, List<ComparisonCondition>> byVar = new LinkedHashMap<>();
        for (String clause : clauses) {
            ComparisonCondition cc = parseComparison(clause.trim());
            if (cc != null) {
                byVar.computeIfAbsent(cc.variable, k -> new ArrayList<>()).add(cc);
            }
        }
        for (Map.Entry<String, List<ComparisonCondition>> entry : byVar.entrySet()) {
            List<ComparisonCondition> conds = entry.getValue();
            if (conds.size() < 2) continue;
            // 两两检查包含关系
            for (int i = 0; i < conds.size(); i++) {
                for (int j = 0; j < conds.size(); j++) {
                    if (i == j) continue;
                    if (isSubset(conds.get(i), conds.get(j))) {
                        // conds[i] 的范围被 conds[j] 包含 → conds[j] 冗余
                        conflicts.add(RuleConflict.builder()
                                .type(RuleConflict.Type.UNREACHABLE_SUBCONDITION)
                                .level(RuleConflict.Level.WARN)
                                .newRuleCode(newDefinition.getCode())
                                .conflictingRuleCode(newDefinition.getCode())
                                .description("子条件不可达：'" + conds.get(j).original
                                        + "' 被 '" + conds.get(i).original + "' 完全包含，为冗余子句")
                                .build());
                    }
                }
            }
        }
    }

    /**
     * 拆分 AND 连接的子句（支持 && 和 and，忽略 ||）
     *
     * @param expr 表达式
     * @return AND 子句列表；含 OR 时整表达式作为一个子句返回
     */
    private List<String> splitAndClauses(String expr) {
        List<String> result = new ArrayList<>();
        if (expr == null || expr.isBlank()) return result;
        // 先按 && 拆分
        String[] parts = expr.split("&&|(?i)\\band\\b");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * 判断条件 a 的范围是否完全包含条件 b 的范围
     *
     * @param a 条件 a（更严格）
     * @param b 条件 b（更宽松，被包含）
     * @return true 表示 b 被 a 包含，b 冗余
     */
    private boolean isSubset(ComparisonCondition a, ComparisonCondition b) {
        if (!a.variable.equals(b.variable)) return false;
        if (a.operator.equals(b.operator) && a.value == b.value) return false;
        double[] ra = toRange(a.operator, a.value);
        double[] rb = toRange(b.operator, b.value);
        // a 包含 b：a 的范围 ⊇ b 的范围 → ra.lower ≤ rb.lower 且 ra.upper ≥ rb.upper
        return ra[0] <= rb[0] && ra[1] >= rb[1];
    }

    /**
     * 规范化条件表达式（1.5.0 增强）
     *
     * <p>归一化步骤：
     * <ol>
     *   <li>去除所有空白字符</li>
     *   <li>统一逻辑运算符：and→&&、or→||、not→!</li>
     *   <li>翻转反向比较：3 &lt; x → x &gt; 3、3 &gt; x → x &lt; 3</li>
     *   <li>统一大小写</li>
     * </ol>
     *
     * @param expression 原始表达式
     * @return 归一化后的表达式；null 输入返回 null
     */
    private String normalize(String expression) {
        if (expression == null) {
            return null;
        }
        String s = expression;
        // 先统一逻辑运算符（大小写不敏感，在去空白前替换以利用 \b 单词边界）
        s = s.replaceAll("(?i)\\band\\b", "&&");
        s = s.replaceAll("(?i)\\bor\\b", "||");
        s = s.replaceAll("(?i)\\bnot\\b", "!");
        // 再去除所有空白字符
        s = s.replaceAll("\\s+", "");
        // 翻转反向比较：number OP var → var FLIP_OP number
        Matcher rm = REVERSE_COMPARISON_PATTERN.matcher(s);
        if (rm.matches()) {
            String number = rm.group(1);
            String op = rm.group(2);
            String var = rm.group(3);
            s = var + flipOperator(op) + number;
        }
        return s.toLowerCase();
    }

    /**
     * 翻转比较运算符（用于反向比较归一化）
     *
     * @param op 原始运算符
     * @return 翻转后的运算符
     */
    private String flipOperator(String op) {
        return switch (op) {
            case ">" -> "<";
            case "<" -> ">";
            case ">=" -> "<=";
            case "<=" -> ">=";
            default -> op; // == 和 != 不翻转
        };
    }

    /**
     * 解析简单比较表达式为 ComparisonCondition
     *
     * <p>仅支持 {@code var OP number} 或 {@code number OP var} 形式，
     * 复杂表达式（含 && / ||）返回 null。
     *
     * @param expression 条件表达式
     * @return ComparisonCondition；无法解析返回 null
     */
    private ComparisonCondition parseComparison(String expression) {
        if (expression == null || expression.isBlank()) return null;
        String trimmed = expression.trim();
        // 不支持复合条件
        if (trimmed.contains("&&") || trimmed.contains("||")
                || trimmed.toLowerCase().contains(" and ") || trimmed.toLowerCase().contains(" or ")) {
            return null;
        }
        // 正向：var OP number
        Matcher m = COMPARISON_PATTERN.matcher(trimmed);
        if (m.matches()) {
            return new ComparisonCondition(m.group(1), m.group(2), Double.parseDouble(m.group(3)), trimmed);
        }
        // 反向：number OP var → 翻转为 var FLIP_OP number
        Matcher rm = REVERSE_COMPARISON_PATTERN.matcher(trimmed);
        if (rm.matches()) {
            String number = rm.group(1);
            String op = rm.group(2);
            String var = rm.group(3);
            return new ComparisonCondition(var, flipOperator(op), Double.parseDouble(number), trimmed);
        }
        return null;
    }

    /**
     * 判断两个比较条件在相同变量上的范围是否重叠
     *
     * <p>规则：
     * <ul>
     *   <li>变量不同 → 不重叠</li>
     *   <li>同互斥组 → 不报重叠（互斥组保证短路）</li>
     *   <li>相同变量 + 相同操作符 + 相同阈值 → 已由 IDENTICAL_CONDITION 覆盖，不重复报</li>
     *   <li>相同变量 + 范围有交集 → 重叠</li>
     * </ul>
     *
     * @param a 条件 A
     * @param b 条件 B
     * @param mutexA 规则 A 的互斥组
     * @param mutexB 规则 B 的互斥组
     * @return true 表示范围重叠
     */
    private boolean isOverlap(ComparisonCondition a, ComparisonCondition b, String mutexA, String mutexB) {
        // 变量不同不重叠
        if (!a.variable.equals(b.variable)) return false;
        // 同互斥组不报重叠
        if (mutexA != null && !mutexA.isBlank() && mutexA.equals(mutexB)) return false;
        // 相同操作符+相同阈值视为等价（由 IDENTICAL_CONDITION 覆盖）
        if (a.operator.equals(b.operator) && a.value == b.value) return false;
        // 计算两个条件的命中范围是否有交集
        // 范围用 [lower, upper] 表示，-∞/∞ 用 null 表示
        double[] rangeA = toRange(a.operator, a.value);
        double[] rangeB = toRange(b.operator, b.value);
        // 交集判断：max(lower) <= min(upper)
        double lower = Math.max(rangeA[0], rangeB[0]);
        double upper = Math.min(rangeA[1], rangeB[1]);
        return lower <= upper;
    }

    /**
     * 将比较条件转换为数值范围 [lower, upper]
     *
     * @param op 比较运算符
     * @param value 阈值
     * @return [lower, upper]，-∞ 用 -Double.MAX_VALUE，+∞ 用 Double.MAX_VALUE
     */
    private double[] toRange(String op, double value) {
        return switch (op) {
            case ">" -> new double[]{value, Double.MAX_VALUE}; // (value, +∞)
            case ">=" -> new double[]{value, Double.MAX_VALUE}; // [value, +∞)
            case "<" -> new double[]{-Double.MAX_VALUE, value}; // (-∞, value)
            case "<=" -> new double[]{-Double.MAX_VALUE, value}; // (-∞, value]
            case "==", "!=" -> new double[]{value, value}; // 单点
            default -> new double[]{-Double.MAX_VALUE, Double.MAX_VALUE}; // 全域
        };
    }

    /**
     * 提取规则的严重度标识
     */
    private String severityKey(RuleDefinition def) {
        if (def.getSeverityExpression() != null && !def.getSeverityExpression().isBlank()) {
            return "expr:" + def.getSeverityExpression().trim();
        }
        RuleSeverity severity = def.getDefaultSeverity();
        return severity != null ? "default:" + severity.getCode() : "default:YELLOW";
    }

    /**
     * 简单比较条件（内部数据结构）
     *
     * @param variable 变量名
     * @param operator 比较运算符（已归一化为正向）
     * @param value 阈值
     * @param original 原始表达式字符串
     */
    private record ComparisonCondition(String variable, String operator, double value, String original) {
    }
}
