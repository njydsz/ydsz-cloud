package com.njydsz.pmis.agent.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ToolRegistry 工具注册中心单元测试（P1-1 落地）
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>register(): 单个/多个注册、同名覆盖、null/空名/空白名跳过</li>
 *   <li>getTool(): 已存在/不存在/null 名称查找</li>
 *   <li>listTools() / listToolNames(): 不可变性、名称列表、空注册中心</li>
 *   <li>formatToolsForPrompt(): 空注册中心返回"无可用工具"、包含名称/描述/参数信息</li>
 *   <li>Spring 构造注入: null List / 空 List 不抛异常</li>
 * </ul>
 *
 * <p>使用 Mockito mock {@link AgentTool}，不依赖具体工具实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolRegistry 工具注册中心测试")
class ToolRegistryTest {

    // ==================== 辅助方法 ====================

    /**
     * 构造 mock AgentTool，指定 name / description / parameterSchema。
     * 使用 LENIENT strictness，未调用的 stub 不会导致测试失败。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param schema      参数 schema（可为 null 或空 Map）
     * @return mock 工具实例
     */
    private AgentTool mockTool(String name, String description, Map<String, Class<?>> schema) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(description);
        when(tool.parameterSchema()).thenReturn(schema);
        return tool;
    }

    /** 构造带名称的 mock AgentTool，description/schema 使用默认值 */
    private AgentTool mockTool(String name) {
        return mockTool(name, "默认描述", new LinkedHashMap<>());
    }

    // ==================== 1. 注册测试 ====================

    @Nested
    @DisplayName("register() 工具注册测试")
    class RegisterTest {

        @Test
        @DisplayName("注册单个工具后可按名称查找")
        void shouldFindByNameAfterRegisterSingle() {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentTool tool = mockTool("single_tool", "单工具", new LinkedHashMap<>());

            registry.register(tool);

            assertThat(registry.getTool("single_tool")).containsSame(tool);
        }

        @Test
        @DisplayName("注册多个工具后 listTools 返回全部")
        void shouldReturnAllAfterRegisterMultiple() {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentTool t1 = mockTool("t1");
            AgentTool t2 = mockTool("t2");
            AgentTool t3 = mockTool("t3");

            registry.register(t1);
            registry.register(t2);
            registry.register(t3);

            assertThat(registry.listTools())
                    .hasSize(3)
                    .containsExactlyInAnyOrder(t1, t2, t3);
        }

        @Test
        @DisplayName("注册同名工具时新值覆盖旧值")
        void shouldOverwriteWhenRegisterSameName() {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentTool oldTool = mockTool("dup", "旧描述", new LinkedHashMap<>());
            AgentTool newTool = mockTool("dup", "新描述", new LinkedHashMap<>());

            registry.register(oldTool);
            registry.register(newTool);

            assertThat(registry.getTool("dup")).containsSame(newTool);
            assertThat(registry.listTools()).hasSize(1);
        }

        @Test
        @DisplayName("register(null) 跳过不注册")
        void shouldSkipNullTool() {
            ToolRegistry registry = new ToolRegistry(List.of());

            registry.register((AgentTool) null);

            assertThat(registry.listTools()).isEmpty();
        }

        @Test
        @DisplayName("register name 返回 null 的工具时跳过")
        void shouldSkipToolWithNullName() {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentTool tool = mock(AgentTool.class);
            when(tool.name()).thenReturn(null);

            registry.register(tool);

            assertThat(registry.listTools()).isEmpty();
        }

        @Test
        @DisplayName("register name 为空字符串的工具时跳过")
        void shouldSkipToolWithEmptyName() {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentTool tool = mockTool("");

            registry.register(tool);

            assertThat(registry.listTools()).isEmpty();
        }

        @Test
        @DisplayName("register name 为空白字符串的工具时跳过")
        void shouldSkipToolWithBlankName() {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentTool tool = mockTool("   ");

            registry.register(tool);

            assertThat(registry.listTools()).isEmpty();
        }
    }

    // ==================== 2. 查找测试 ====================

    @Nested
    @DisplayName("getTool() 工具查找测试")
    class LookupTest {

        @Test
        @DisplayName("getTool(已存在名称) 返回 Optional 非空")
        void shouldReturnPresentWhenNameExists() {
            ToolRegistry registry = new ToolRegistry(List.of());
            registry.register(mockTool("exists"));

            assertThat(registry.getTool("exists")).isPresent();
        }

        @Test
        @DisplayName("getTool(不存在名称) 返回 Optional.empty")
        void shouldReturnEmptyWhenNameNotExists() {
            ToolRegistry registry = new ToolRegistry(List.of());

            assertThat(registry.getTool("not_exists")).isEmpty();
        }

        @Test
        @DisplayName("getTool(null) 返回 Optional.empty")
        void shouldReturnEmptyWhenNameIsNull() {
            ToolRegistry registry = new ToolRegistry(List.of());

            assertThat(registry.getTool(null)).isEmpty();
        }
    }

    // ==================== 3. 列表测试 ====================

    @Nested
    @DisplayName("listTools() / listToolNames() 列表测试")
    class ListTest {

        @Test
        @DisplayName("listTools 返回不可变列表")
        void shouldReturnUnmodifiableList() {
            ToolRegistry registry = new ToolRegistry(List.of());
            registry.register(mockTool("t1"));

            List<AgentTool> tools = registry.listTools();

            assertThatThrownBy(() -> tools.add(mockTool("t2")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("listToolNames 返回所有名称")
        void shouldReturnAllNames() {
            ToolRegistry registry = new ToolRegistry(List.of());
            registry.register(mockTool("alpha"));
            registry.register(mockTool("beta"));

            assertThat(registry.listToolNames())
                    .hasSize(2)
                    .containsExactlyInAnyOrder("alpha", "beta");
        }

        @Test
        @DisplayName("空注册中心返回空列表")
        void shouldReturnEmptyListWhenRegistryEmpty() {
            ToolRegistry registry = new ToolRegistry(List.of());

            assertThat(registry.listTools()).isEmpty();
            assertThat(registry.listToolNames()).isEmpty();
        }
    }

    // ==================== 4. prompt 格式化测试 ====================

    @Nested
    @DisplayName("formatToolsForPrompt() prompt 格式化测试")
    class FormatPromptTest {

        @Test
        @DisplayName("空注册中心返回 '无可用工具'")
        void shouldReturnNoToolAvailableWhenEmpty() {
            ToolRegistry registry = new ToolRegistry(List.of());

            assertThat(registry.formatToolsForPrompt()).isEqualTo("无可用工具");
        }

        @Test
        @DisplayName("有工具时返回包含工具名称和描述的文本")
        void shouldContainToolNameAndDescription() {
            ToolRegistry registry = new ToolRegistry(List.of());
            registry.register(mockTool("project_status", "查询项目指标", new LinkedHashMap<>()));

            String prompt = registry.formatToolsForPrompt();

            assertThat(prompt)
                    .contains("可用工具")
                    .contains("project_status")
                    .contains("查询项目指标");
        }

        @Test
        @DisplayName("格式化文本包含参数信息")
        void shouldContainParameterInfo() {
            ToolRegistry registry = new ToolRegistry(List.of());
            Map<String, Class<?>> schema = new LinkedHashMap<>();
            schema.put("projectId", String.class);
            schema.put("severity", String.class);
            registry.register(mockTool("risk_events", "查询风险事件", schema));

            String prompt = registry.formatToolsForPrompt();

            assertThat(prompt)
                    .contains("参数")
                    .contains("projectId(String)")
                    .contains("severity(String)");
        }
    }

    // ==================== 5. Spring 构造注入测试 ====================

    @Nested
    @DisplayName("Spring 构造注入测试")
    class SpringInjectionTest {

        @Test
        @DisplayName("构造函数接收 null List 时不抛异常")
        void shouldNotThrowWhenListIsNull() {
            ToolRegistry registry = new ToolRegistry(null);

            assertThat(registry.listTools()).isEmpty();
            assertThat(registry.listToolNames()).isEmpty();
        }

        @Test
        @DisplayName("构造函数接收空 List 时不抛异常")
        void shouldNotThrowWhenListIsEmpty() {
            ToolRegistry registry = new ToolRegistry(List.of());

            assertThat(registry.listTools()).isEmpty();
            assertThat(registry.listToolNames()).isEmpty();
        }
    }
}
