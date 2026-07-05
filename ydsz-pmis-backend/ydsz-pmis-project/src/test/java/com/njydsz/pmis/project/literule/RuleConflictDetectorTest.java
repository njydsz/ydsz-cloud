package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.project.entity.RuleDefinitionDO;
import com.njydsz.pmis.project.literule.RuleConflictDetector.RuleConflictInfo;
import com.njydsz.pmis.project.mapper.RuleDefinitionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RuleConflictDetector 单元测试
 *
 * <p>覆盖变量提取、重叠率计算、严重度分级、边界条件。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("规则冲突检测器测试")
class RuleConflictDetectorTest {

    @Mock
    private RuleDefinitionMapper ruleDefinitionMapper;

    @InjectMocks
    private RuleConflictDetector detector;

    @Test
    @DisplayName("无启用规则时返回空列表")
    void shouldReturnEmptyWhenNoEnabledRules() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of());

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("仅一条启用规则时返回空列表")
    void shouldReturnEmptyWhenOnlyOneEnabledRule() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("两条规则变量无重叠时不报告冲突")
    void shouldNotReportWhenNoOverlap() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true),
                buildRule("R002", "count >= 5", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("两条规则变量完全重叠时 severity=high")
    void shouldReportHighWhenFullOverlap() {
        // 两条规则都用 amount 和 ratio
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000 && ratio >= 0.5", true),
                buildRule("R002", "amount < 5000 && ratio < 0.8", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).hasSize(1);
        RuleConflictInfo info = result.get(0);
        assertThat(info.getSeverity()).isEqualTo("high");
        assertThat(info.getOverlapFields()).containsExactlyInAnyOrder("amount", "ratio");
    }

    @Test
    @DisplayName("两条规则变量部分重叠时 severity=medium 或 low")
    void shouldReportMediumOrLowWhenPartialOverlap() {
        // R001: amount, ratio, count（3 个变量）
        // R002: amount, ratio, name, type（4 个变量）
        // 重叠 2 个，total=7，overlapRatio = 2*2/7 ≈ 0.57 → medium
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000 && ratio >= 0.5 && count >= 3", true),
                buildRule("R002", "amount < 5000 && ratio < 0.8 && name == 'x' && type == 'y'", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeverity()).isEqualTo("medium");
    }

    @Test
    @DisplayName("低重叠率时 severity=low")
    void shouldReportLowWhenMinimalOverlap() {
        // R001: amount（1 个变量）
        // R002: amount, ratio, count, name, type, value, score（7 个变量）
        // 重叠 1 个，total=8，overlapRatio = 2*1/8 = 0.25 → low
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true),
                buildRule("R002", "amount > 0 && ratio > 0 && count > 0 && name != '' && type != '' && value > 0 && score > 0", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeverity()).isEqualTo("low");
    }

    @Test
    @DisplayName("禁用的规则不参与冲突检测")
    void shouldSkipDisabledRules() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true),
                buildRule("R002", "amount < 5000", false)  // 禁用
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("条件表达式为空的规则不产生冲突")
    void shouldHandleEmptyExpression() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", null, true),
                buildRule("R002", "", true),
                buildRule("R003", "amount > 1000", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        // R001 和 R002 无变量，只有与 R003 比较时无重叠
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("关键字和函数名不应识别为变量")
    void shouldFilterKeywordsAndFunctions() {
        // 包含关键字 true/false/null/Math/max/min
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000 && true == false", true),
                buildRule("R002", "ratio < 0.5 && Math.max(amount, 100) > 200", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        // R001 变量: amount；R002 变量: ratio, amount
        // 重叠: amount
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOverlapFields()).containsExactly("amount");
    }

    @Test
    @DisplayName("单字符标识符不识别为变量")
    void shouldFilterSingleCharIdentifiers() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true),
                buildRule("R002", "amount < 5000 && x > 0", true)  // x 是单字符
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).hasSize(1);
        // x 不应出现在重叠字段中
        assertThat(result.get(0).getOverlapFields()).containsExactly("amount");
    }

    @Test
    @DisplayName("三条规则两两比较应产出多个冲突")
    void shouldDetectMultipleConflicts() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true),
                buildRule("R002", "amount < 5000", true),
                buildRule("R003", "amount != 0", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        // C(3,2) = 3 对
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("冲突信息应包含规则编码和名称")
    void shouldIncludeRuleCodeAndName() {
        when(ruleDefinitionMapper.selectList(any())).thenReturn(List.of(
                buildRule("R001", "amount > 1000", true),
                buildRule("R002", "amount < 5000", true)
        ));

        List<RuleConflictInfo> result = detector.detectConflicts();

        assertThat(result).hasSize(1);
        RuleConflictInfo info = result.get(0);
        assertThat(info.getRuleA()).isEqualTo("R001");
        assertThat(info.getRuleAName()).isEqualTo("规则1");
        assertThat(info.getRuleB()).isEqualTo("R002");
        assertThat(info.getRuleBName()).isEqualTo("规则2");
    }

    // ============ 辅助方法 ============

    private RuleDefinitionDO buildRule(String code, String conditionExpr, boolean enabled) {
        RuleDefinitionDO rule = new RuleDefinitionDO();
        rule.setRuleCode(code);
        rule.setRuleName("规则" + code.substring(code.length() - 1));
        rule.setConditionExpression(conditionExpr);
        rule.setEnabled(enabled);
        return rule;
    }
}
