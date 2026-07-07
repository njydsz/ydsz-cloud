package com.njydsz.pmis.agent.engine.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultPromptTemplateRenderer} 单元测试（P2-2）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@DisplayName("DefaultPromptTemplateRenderer ${var} 变量替换测试")
class DefaultPromptTemplateRendererTest {

    private final DefaultPromptTemplateRenderer renderer = new DefaultPromptTemplateRenderer();

    @Nested
    @DisplayName("基础替换")
    class BasicReplacementTest {

        @Test
        @DisplayName("单个 ${var} 替换")
        void shouldReplaceSingleVariable() {
            String result = renderer.render("你好 ${name}", Map.of("name", "张三"));
            assertThat(result).isEqualTo("你好 张三");
        }

        @Test
        @DisplayName("多个 ${var} 替换")
        void shouldReplaceMultipleVariables() {
            String result = renderer.render("${greeting} ${name}，欢迎来到 ${place}",
                    Map.of("greeting", "你好", "name", "李四", "place", "PMIS"));
            assertThat(result).isEqualTo("你好 李四，欢迎来到 PMIS");
        }

        @Test
        @DisplayName("同一变量出现多次")
        void shouldReplaceSameVariableMultipleTimes() {
            String result = renderer.render("${name}说：${name}是最好的",
                    Map.of("name", "PMIS"));
            assertThat(result).isEqualTo("PMIS说：PMIS是最好的");
        }
    }

    @Nested
    @DisplayName("嵌套 Map 取值")
    class NestedMapTest {

        @Test
        @DisplayName("${a.b.c} 嵌套 Map 取值")
        void shouldResolveNestedMap() {
            Map<String, Object> params = Map.of(
                    "user", Map.of(
                            "name", "王五",
                            "dept", Map.of("name", "研发部")
                    )
            );
            assertThat(renderer.render("${user.name}", params)).isEqualTo("王五");
            assertThat(renderer.render("${user.dept.name}", params)).isEqualTo("研发部");
        }

        @Test
        @DisplayName("嵌套路径中间为 null 时替换为空串")
        void shouldReturnEmptyWhenNestedPathNull() {
            Map<String, Object> params = Map.of("user", Map.of("name", "ok"));
            assertThat(renderer.render("${user.unknown.field}", params)).isEmpty();
        }
    }

    @Nested
    @DisplayName("边界处理")
    class EdgeCaseTest {

        @Test
        @DisplayName("模板为 null 返回空串")
        void shouldReturnEmptyWhenTemplateNull() {
            assertThat(renderer.render(null, Map.of("a", "b"))).isEmpty();
        }

        @Test
        @DisplayName("模板为空串返回空串")
        void shouldReturnEmptyWhenTemplateEmpty() {
            assertThat(renderer.render("", Map.of("a", "b"))).isEmpty();
        }

        @Test
        @DisplayName("params 为 null 返回原模板")
        void shouldReturnOriginalWhenParamsNull() {
            assertThat(renderer.render("无变量模板", null)).isEqualTo("无变量模板");
        }

        @Test
        @DisplayName("未命中的变量替换为空串")
        void shouldReplaceMissingVarWithEmpty() {
            assertThat(renderer.render("${missing}", Map.of())).isEmpty();
        }

        @Test
        @DisplayName("无变量的模板原样返回")
        void shouldReturnOriginalWhenNoVariables() {
            assertThat(renderer.render("纯文本无变量", Map.of("a", "b")))
                    .isEqualTo("纯文本无变量");
        }

        @Test
        @DisplayName("变量值为 null 替换为空串")
        void shouldReplaceNullValueWithEmpty() {
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("val", null);
            assertThat(renderer.render("[${val}]", params)).isEqualTo("[]");
        }

        @Test
        @DisplayName("变量值为数字类型")
        void shouldReplaceWithNumberValue() {
            assertThat(renderer.render("数量: ${count}", Map.of("count", 42)))
                    .isEqualTo("数量: 42");
        }

        @Test
        @DisplayName("特殊字符在替换值中被正确处理（不破坏正则）")
        void shouldHandleSpecialCharsInValue() {
            assertThat(renderer.render("path=${path}", Map.of("path", "C:\\Users\\test$")))
                    .isEqualTo("path=C:\\Users\\test$");
        }
    }
}
