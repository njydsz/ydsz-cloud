package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 规则冲突检测器
 *
 * <p>在规则保存前检测新规则与现有规则的潜在冲突，输出 {@link RuleConflict} 列表。
 *
 * <p>当前实现的检测维度（基于字段精确匹配，不做表达式语义分析）：
 * <ul>
 *   <li>{@link RuleConflict.Type#IDENTICAL_CONDITION}：同 category + 同 tenantId 下，
 *       条件表达式 trim 后完全相同（WARN，可能重复定义）</li>
 *   <li>{@link RuleConflict.Type#CONTRADICTORY_SEVERITY}：条件表达式相同但严重度不同
 *       （ERROR，语义冲突）</li>
 *   <li>{@link RuleConflict.Type#NAME_COLLISION}：同 category + 同 tenantId 下，
 *       name 相同但条件表达式不同（WARN，命名冲突）</li>
 * </ul>
 *
 * <p>说明：条件范围重叠（overlap）的语义分析依赖变量空间元数据（P2-4），
 * 当前版本不做样本探测，避免误报。未来 VariableRegistry 完成后可增强。
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

    /**
     * 检测新规则与所有现有规则的冲突
     *
     * <p>会跳过自身（ruleCode 相同的规则，用于更新场景）和不同租户的规则。
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
        long newTenantId = newDefinition.getTenantId();
        String newCategory = newDefinition.getCategory();
        String newName = newDefinition.getName();
        String newCondition = normalize(newDefinition.getConditionExpression());
        String newSeverity = severityKey(newDefinition);

        for (RuleDefinition other : existingRules) {
            // 跳过自身（更新场景）
            if (Objects.equals(other.getCode(), newCode)) {
                continue;
            }
            // 跨租户不检测
            if (other.getTenantId() != newTenantId) {
                continue;
            }

            String otherCondition = normalize(other.getConditionExpression());
            String otherSeverity = severityKey(other);

            // 1. 条件表达式完全相同
            boolean sameCondition = newCondition != null
                    && newCondition.equals(otherCondition)
                    && !newCondition.isEmpty();

            if (sameCondition) {
                // 2. 条件相同但严重度不同 → 语义冲突（ERROR）
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
                    // 3. 条件相同且严重度相同 → 重复定义（WARN）
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

            // 4. 同 category 下名称相同但条件不同 → 命名冲突（WARN）
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
        }

        return conflicts;
    }

    /**
     * 规范化条件表达式（去空白、转小写），用于精确匹配
     *
     * @param expression 原始表达式
     * @return 规范化后的表达式；null 输入返回 null
     */
    private String normalize(String expression) {
        if (expression == null) {
            return null;
        }
        // 去除所有空白字符后转小写，使 "a > 1" 与 "a>1" 视为相同
        return expression.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 提取规则的严重度标识（severityExpression 优先，否则 defaultSeverity）
     *
     * @param def 规则定义
     * @return 严重度标识字符串
     */
    private String severityKey(RuleDefinition def) {
        if (def.getSeverityExpression() != null && !def.getSeverityExpression().isBlank()) {
            return "expr:" + def.getSeverityExpression().trim();
        }
        RuleSeverity severity = def.getDefaultSeverity();
        return severity != null ? "default:" + severity.getCode() : "default:YELLOW";
    }
}
