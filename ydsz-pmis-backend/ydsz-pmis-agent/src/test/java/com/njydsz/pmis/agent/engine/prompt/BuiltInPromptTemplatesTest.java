package com.njydsz.pmis.agent.engine.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置默认 Prompt 模板单元测试（P2-2 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@link BuiltInPromptTemplates#get(String)} 对已知 code 返回非空内容</li>
 *   <li>{@link BuiltInPromptTemplates#get(String)} 对未知 code 返回 null</li>
 *   <li>{@link BuiltInPromptTemplates#get(String)} 对 null 返回 null</li>
 *   <li>{@link BuiltInPromptTemplates#contains(String)} 对已知/未知/null 的判断</li>
 *   <li>各内置模板内容特征断言（确保文本未被误改）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@DisplayName("BuiltInPromptTemplates 内置默认模板测试")
class BuiltInPromptTemplatesTest {

    // ==================== get 方法测试 ====================

    @Nested
    @DisplayName("get(code) 方法测试")
    class GetTest {

        @Test
        @DisplayName("REACT_FORMAT_INSTRUCTION 返回非空且包含 ReAct 关键字")
        void shouldReturnReactFormatInstruction() {
            String content = BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(content).isNotNull();
            assertThat(content).isNotEmpty();
            assertThat(content).contains("ReAct");
            assertThat(content).contains("Thought");
            assertThat(content).contains("Action");
            assertThat(content).contains("Observation");
            assertThat(content).contains("final_answer");
        }

        @Test
        @DisplayName("FLOW_GENERATOR_SYSTEM 返回非空且包含 BPMN 关键字")
        void shouldReturnFlowGeneratorSystem() {
            String content = BuiltInPromptTemplates.get(PromptTemplateCodes.FLOW_GENERATOR_SYSTEM);

            assertThat(content).isNotNull();
            assertThat(content).isNotEmpty();
            assertThat(content).contains("BPMN");
            assertThat(content).contains("bpmn:definitions");
            assertThat(content).contains("startEvent");
            assertThat(content).contains("userTask");
            assertThat(content).contains("endEvent");
        }

        @Test
        @DisplayName("FLOW_GENERATOR_USER 返回非空且包含 ${description} 占位符")
        void shouldReturnFlowGeneratorUser() {
            String content = BuiltInPromptTemplates.get(PromptTemplateCodes.FLOW_GENERATOR_USER);

            assertThat(content).isNotNull();
            assertThat(content).isNotEmpty();
            assertThat(content).contains("${description}");
            assertThat(content).contains("BPMN 2.0");
        }

        @Test
        @DisplayName("未知 code 返回 null")
        void shouldReturnNullForUnknownCode() {
            String content = BuiltInPromptTemplates.get("NON_EXISTENT_CODE");

            assertThat(content).isNull();
        }

        @Test
        @DisplayName("null code 返回 null（Map.get 容忍 null key）")
        void shouldReturnNullForNullCode() {
            String content = BuiltInPromptTemplates.get(null);

            assertThat(content).isNull();
        }

        @Test
        @DisplayName("空串 code 返回 null")
        void shouldReturnNullForEmptyCode() {
            String content = BuiltInPromptTemplates.get("");

            assertThat(content).isNull();
        }
    }

    // ==================== contains 方法测试 ====================

    @Nested
    @DisplayName("contains(code) 方法测试")
    class ContainsTest {

        @Test
        @DisplayName("已知 code 返回 true")
        void shouldReturnTrueForKnownCodes() {
            assertThat(BuiltInPromptTemplates.contains(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION)).isTrue();
            assertThat(BuiltInPromptTemplates.contains(PromptTemplateCodes.FLOW_GENERATOR_SYSTEM)).isTrue();
            assertThat(BuiltInPromptTemplates.contains(PromptTemplateCodes.FLOW_GENERATOR_USER)).isTrue();
        }

        @Test
        @DisplayName("未知 code 返回 false")
        void shouldReturnFalseForUnknownCode() {
            assertThat(BuiltInPromptTemplates.contains("UNKNOWN")).isFalse();
        }

        @Test
        @DisplayName("null code 返回 false")
        void shouldReturnFalseForNullCode() {
            assertThat(BuiltInPromptTemplates.contains(null)).isFalse();
        }

        @Test
        @DisplayName("空串 code 返回 false")
        void shouldReturnFalseForEmptyCode() {
            assertThat(BuiltInPromptTemplates.contains("")).isFalse();
        }
    }

    // ==================== 模板内容一致性测试 ====================

    @Nested
    @DisplayName("模板内容一致性测试")
    class ContentConsistencyTest {

        @Test
        @DisplayName("3 个内置模板均存在且非空")
        void shouldHaveAllThreeBuiltInTemplates() {
            assertThat(BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION)).isNotEmpty();
            assertThat(BuiltInPromptTemplates.get(PromptTemplateCodes.FLOW_GENERATOR_SYSTEM)).isNotEmpty();
            assertThat(BuiltInPromptTemplates.get(PromptTemplateCodes.FLOW_GENERATOR_USER)).isNotEmpty();
        }

        @Test
        @DisplayName("get 与 contains 对同一 code 行为一致")
        void shouldGetAndContainsBeConsistent() {
            String[] codes = {
                    PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                    PromptTemplateCodes.FLOW_GENERATOR_SYSTEM,
                    PromptTemplateCodes.FLOW_GENERATOR_USER,
                    "NON_EXISTENT"
            };
            for (String code : codes) {
                String content = BuiltInPromptTemplates.get(code);
                boolean contains = BuiltInPromptTemplates.contains(code);
                assertThat(content != null).isEqualTo(contains);
            }
        }

        @Test
        @DisplayName("FLOW_GENERATOR_USER 必须包含 ${description} 变量（用于渲染时替换）")
        void shouldFlowGeneratorUserContainDescriptionPlaceholder() {
            String content = BuiltInPromptTemplates.get(PromptTemplateCodes.FLOW_GENERATOR_USER);
            assertThat(content).contains("${description}");
        }

        @Test
        @DisplayName("多次调用返回同一引用（无副作用）")
        void shouldReturnConsistentReferenceAcrossCalls() {
            String first = BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
            String second = BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(first).isSameAs(second);
        }
    }
}
