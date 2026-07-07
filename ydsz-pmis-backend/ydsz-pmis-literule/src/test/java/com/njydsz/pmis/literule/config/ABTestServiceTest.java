package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ABTestService 单元测试
 *
 * <p>测试目标：验证 A/B 测试服务对同一事实数据并行评估当前规则版本和候选规则版本，
 * 正确对比触发状态（triggered）、严重度（severity）、标题（title）、描述（description）的差异，
 * 并产出完整的差异详情 map。
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>无差异场景（全部维度一致，hasDiff=false）</li>
 *   <li>触发状态差异（triggeredChanged=true，含 true→false 与 false→true）</li>
 *   <li>严重度差异（severityChanged=true）</li>
 *   <li>标题差异（titleChanged=true），含 null 安全比较全分支</li>
 *   <li>描述差异（descriptionChanged=true），含 null 安全比较全分支</li>
 *   <li>差异详情 map 的 triggeredBefore/After、severityBefore/After 字段完整性</li>
 *   <li>ABTestReport 记录字段（ruleCode/version/summary）正确性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("ABTestService 单元测试")
@ExtendWith(MockitoExtension.class)
class ABTestServiceTest {

    @Mock
    private ExpressionEvaluator evaluator;

    private ABTestService abTestService;

    @BeforeEach
    void setUp() {
        abTestService = new ABTestService(evaluator);
    }

    /**
     * 构建规则定义辅助方法
     *
     * @param code               规则编码
     * @param conditionExpr      条件表达式（不同版本使用不同表达式以避免上下文缓存命中）
     * @param severity           默认严重度
     * @param titleTemplate      标题模板（null 时回退到规则名）
     * @param descriptionTemplate 描述模板（null 时回退到规则名）
     * @param name               规则名（null 时标题/描述回退为 null）
     * @param version            版本号
     * @return RuleDefinition 实例
     */
    private RuleDefinition def(String code, String conditionExpr, RuleSeverity severity,
                               String titleTemplate, String descriptionTemplate,
                               String name, int version) {
        return RuleDefinition.builder()
                .code(code)
                .name(name)
                .conditionExpression(conditionExpr)
                .defaultSeverity(severity)
                .titleTemplate(titleTemplate)
                .descriptionTemplate(descriptionTemplate)
                .version(version)
                .build();
    }

    /**
     * 简化版规则定义构造（name=code, version=1）
     */
    private RuleDefinition def(String code, String conditionExpr, RuleSeverity severity,
                               String titleTemplate, String descriptionTemplate) {
        return def(code, conditionExpr, severity, titleTemplate, descriptionTemplate, code, 1);
    }

    @Nested
    @DisplayName("无差异场景")
    class NoDiffScenario {

        @Test
        @DisplayName("两版本均触发且 severity/title/description 一致时，hasDiff=false，summary=无差异")
        void test_noDiff_bothTriggeredSameResult() {
            // 给定：当前版本和候选版本使用不同条件表达式但评估结果一致
            RuleDefinition current = def("R001", "curr_cond", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "cand_cond", RuleSeverity.RED, "标题A", "描述X");

            // 模拟两个表达式均触发
            when(evaluator.evalBoolean(eq("curr_cond"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("cand_cond"), any())).thenReturn(true);

            // 当：执行 A/B 测试
            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of("x", 1));

            // 则：无差异
            assertThat(report.diff()).containsEntry("hasDiff", false);
            assertThat(report.diff()).containsEntry("triggeredChanged", false);
            assertThat(report.diff()).containsEntry("severityChanged", false);
            assertThat(report.diff()).containsEntry("titleChanged", false);
            assertThat(report.diff()).containsEntry("descriptionChanged", false);
            assertThat(report.summary()).contains("无差异");
            assertThat(report.diff()).doesNotContainKey("triggeredBefore");
            assertThat(report.diff()).doesNotContainKey("severityBefore");
        }

        @Test
        @DisplayName("两版本均未触发时，hasDiff=false")
        void test_noDiff_bothNotTriggered() {
            RuleDefinition current = def("R001", "curr_cond", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "cand_cond", RuleSeverity.RED, "标题A", "描述X");

            when(evaluator.evalBoolean(eq("curr_cond"), any())).thenReturn(false);
            when(evaluator.evalBoolean(eq("cand_cond"), any())).thenReturn(false);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("hasDiff", false);
            assertThat(report.diff()).containsEntry("triggeredChanged", false);
            // 未触发时 severity 均为 null，safeEquals 判断无差异
            assertThat(report.diff()).containsEntry("severityChanged", false);
            assertThat(report.summary()).contains("无差异");
        }
    }

    @Nested
    @DisplayName("触发状态差异")
    class TriggeredChangedScenario {

        @Test
        @DisplayName("当前触发→候选未触发时，triggeredChanged=true，包含 triggeredBefore/After")
        void test_triggeredChanged_trueToFalse() {
            RuleDefinition current = def("R001", "curr_cond", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "cand_cond", RuleSeverity.YELLOW, "标题B", "描述Y");

            when(evaluator.evalBoolean(eq("curr_cond"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("cand_cond"), any())).thenReturn(false);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("hasDiff", true);
            assertThat(report.diff()).containsEntry("triggeredChanged", true);
            assertThat(report.diff()).containsEntry("triggeredBefore", true);
            assertThat(report.diff()).containsEntry("triggeredAfter", false);
            assertThat(report.summary()).contains("检测到差异");
            // 触发→未触发，severity 也随之变化（RED→null）
            assertThat(report.diff()).containsEntry("severityChanged", true);
            assertThat(report.diff()).containsEntry("severityBefore", RuleSeverity.RED);
            assertThat(report.diff()).containsEntry("severityAfter", null);
            // 标题/描述也变化（"标题A"→null）
            assertThat(report.diff()).containsEntry("titleChanged", true);
            assertThat(report.diff()).containsEntry("descriptionChanged", true);
        }

        @Test
        @DisplayName("当前未触发→候选触发时，triggeredChanged=true，包含 triggeredBefore/After")
        void test_triggeredChanged_falseToTrue() {
            RuleDefinition current = def("R001", "curr_cond", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "cand_cond", RuleSeverity.YELLOW, "标题B", "描述Y");

            when(evaluator.evalBoolean(eq("curr_cond"), any())).thenReturn(false);
            when(evaluator.evalBoolean(eq("cand_cond"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("triggeredChanged", true);
            assertThat(report.diff()).containsEntry("triggeredBefore", false);
            assertThat(report.diff()).containsEntry("triggeredAfter", true);
            assertThat(report.diff()).containsEntry("hasDiff", true);
            // 未触发→触发，severity 变化（null→YELLOW）
            assertThat(report.diff()).containsEntry("severityChanged", true);
            assertThat(report.diff()).containsEntry("severityBefore", null);
            assertThat(report.diff()).containsEntry("severityAfter", RuleSeverity.YELLOW);
        }
    }

    @Nested
    @DisplayName("严重度差异")
    class SeverityChangedScenario {

        @Test
        @DisplayName("两版本均触发但 severity 不同时，severityChanged=true，包含 severityBefore/After")
        void test_severityChanged_only() {
            RuleDefinition current = def("R001", "curr_cond", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "cand_cond", RuleSeverity.YELLOW, "标题A", "描述X");

            when(evaluator.evalBoolean(eq("curr_cond"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("cand_cond"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("hasDiff", true);
            assertThat(report.diff()).containsEntry("severityChanged", true);
            assertThat(report.diff()).containsEntry("severityBefore", RuleSeverity.RED);
            assertThat(report.diff()).containsEntry("severityAfter", RuleSeverity.YELLOW);
            // 其他维度无差异
            assertThat(report.diff()).containsEntry("triggeredChanged", false);
            assertThat(report.diff()).containsEntry("titleChanged", false);
            assertThat(report.diff()).containsEntry("descriptionChanged", false);
        }

        @Test
        @DisplayName("severity INFO→RED 变化也被检测")
        void test_severityChanged_infoToRed() {
            RuleDefinition current = def("R002", "c1", RuleSeverity.INFO, "T", "D");
            RuleDefinition candidate = def("R002", "c2", RuleSeverity.RED, "T", "D");

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("severityChanged", true);
            assertThat(report.diff()).containsEntry("severityBefore", RuleSeverity.INFO);
            assertThat(report.diff()).containsEntry("severityAfter", RuleSeverity.RED);
        }
    }

    @Nested
    @DisplayName("标题差异（含 null 安全比较）")
    class TitleChangedScenario {

        @Test
        @DisplayName("两版本均触发但 title 不同时，titleChanged=true")
        void test_titleChanged_differentStrings() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "标题B", "描述X");

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("titleChanged", true);
            assertThat(report.diff()).containsEntry("hasDiff", true);
            assertThat(report.diff()).containsEntry("triggeredChanged", false);
            assertThat(report.diff()).containsEntry("severityChanged", false);
            assertThat(report.diff()).containsEntry("descriptionChanged", false);
        }

        @Test
        @DisplayName("current title=null（name=null且template=null）→ candidate title 非空，titleChanged=true")
        void test_titleChanged_nullToNonNull() {
            // name=null, titleTemplate=null → renderTemplate 返回 getName()=null
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, null, "描述X", null, 1);
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "标题B", "描述X");

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.currentResult().getTitle()).isNull();
            assertThat(report.candidateResult().getTitle()).isEqualTo("标题B");
            assertThat(report.diff()).containsEntry("titleChanged", true);
        }

        @Test
        @DisplayName("current title 非空 → candidate title=null，titleChanged=true")
        void test_titleChanged_nonNullToNull() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "标题A", "描述X");
            // name=null, titleTemplate=null → title=null
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, null, "描述X", null, 1);

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.currentResult().getTitle()).isEqualTo("标题A");
            assertThat(report.candidateResult().getTitle()).isNull();
            assertThat(report.diff()).containsEntry("titleChanged", true);
        }

        @Test
        @DisplayName("两版本 title 均为 null（name=null 且 template=null），titleChanged=false")
        void test_titleChanged_bothNull() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, null, "描述X", null, 1);
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, null, "描述X", null, 1);

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.currentResult().getTitle()).isNull();
            assertThat(report.candidateResult().getTitle()).isNull();
            assertThat(report.diff()).containsEntry("titleChanged", false);
        }
    }

    @Nested
    @DisplayName("描述差异（含 null 安全比较）")
    class DescriptionChangedScenario {

        @Test
        @DisplayName("两版本均触发但 description 不同时，descriptionChanged=true")
        void test_descriptionChanged_differentStrings() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "标题A", "描述Y");

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsEntry("descriptionChanged", true);
            assertThat(report.diff()).containsEntry("hasDiff", true);
            assertThat(report.diff()).containsEntry("titleChanged", false);
            assertThat(report.diff()).containsEntry("triggeredChanged", false);
            assertThat(report.diff()).containsEntry("severityChanged", false);
        }

        @Test
        @DisplayName("current description=null → candidate description 非空，descriptionChanged=true")
        void test_descriptionChanged_nullToNonNull() {
            // name=null, descriptionTemplate=null → description=getName()=null
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "标题A", null, null, 1);
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "标题A", "描述Y");

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.currentResult().getDescription()).isNull();
            assertThat(report.candidateResult().getDescription()).isEqualTo("描述Y");
            assertThat(report.diff()).containsEntry("descriptionChanged", true);
        }

        @Test
        @DisplayName("current description 非空 → candidate description=null，descriptionChanged=true")
        void test_descriptionChanged_nonNullToNull() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "标题A", "描述X");
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "标题A", null, null, 1);

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.currentResult().getDescription()).isEqualTo("描述X");
            assertThat(report.candidateResult().getDescription()).isNull();
            assertThat(report.diff()).containsEntry("descriptionChanged", true);
        }

        @Test
        @DisplayName("两版本 description 均为 null，descriptionChanged=false")
        void test_descriptionChanged_bothNull() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "标题A", null, null, 1);
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "标题A", null, null, 1);

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.currentResult().getDescription()).isNull();
            assertThat(report.candidateResult().getDescription()).isNull();
            assertThat(report.diff()).containsEntry("descriptionChanged", false);
        }
    }

    @Nested
    @DisplayName("报告字段完整性")
    class ReportFieldsScenario {

        @Test
        @DisplayName("ABTestReport 包含正确的 ruleCode/currentVersion/candidateVersion")
        void test_reportFields() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "T", "D", "R001", 3);
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "T", "D", "R001", 5);

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of("x", 1));

            assertThat(report.ruleCode()).isEqualTo("R001");
            assertThat(report.currentVersion()).isEqualTo(3);
            assertThat(report.candidateVersion()).isEqualTo(5);
            assertThat(report.currentResult()).isNotNull();
            assertThat(report.candidateResult()).isNotNull();
            assertThat(report.currentResult().isTriggered()).isTrue();
            assertThat(report.candidateResult().isTriggered()).isTrue();
            assertThat(report.currentResult().getSeverity()).isEqualTo(RuleSeverity.RED);
            assertThat(report.candidateResult().getSeverity()).isEqualTo(RuleSeverity.RED);
        }

        @Test
        @DisplayName("差异 map 包含全部 5 个基础字段")
        void test_diffMapKeys() {
            RuleDefinition current = def("R001", "c1", RuleSeverity.RED, "T", "D");
            RuleDefinition candidate = def("R001", "c2", RuleSeverity.RED, "T", "D");

            when(evaluator.evalBoolean(eq("c1"), any())).thenReturn(true);
            when(evaluator.evalBoolean(eq("c2"), any())).thenReturn(true);

            ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

            assertThat(report.diff()).containsOnlyKeys(
                    "triggeredChanged", "severityChanged", "titleChanged",
                    "descriptionChanged", "hasDiff"
            );
        }
    }
}
