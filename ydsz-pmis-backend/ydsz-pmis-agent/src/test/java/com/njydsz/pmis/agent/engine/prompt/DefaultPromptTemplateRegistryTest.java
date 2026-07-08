package com.njydsz.pmis.agent.engine.prompt;

import com.njydsz.pmis.agent.entity.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.mapper.AgentPromptTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 默认 Prompt 模板注册中心单元测试（P2-2 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>DB 命中生效模板 → 返回 DB 内容</li>
 *   <li>DB 返回 null → 降级为内置默认</li>
 *   <li>DB 异常 → 降级为内置默认</li>
 *   <li>Mapper 不可用（无 DB 环境）→ 降级为内置默认</li>
 *   <li>DB 与内置均无此 code → 返回空串</li>
 *   <li>缓存命中：第 2 次查询不访问 DB</li>
 *   <li>refresh() 清空缓存后重新查询 DB</li>
 *   <li>render() 渲染：DB 模板 + 参数替换</li>
 *   <li>null / 空 code 处理</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultPromptTemplateRegistry 注册中心测试")
class DefaultPromptTemplateRegistryTest {

    @Mock
    private ObjectProvider<AgentPromptTemplateMapper> mapperProvider;

    @Mock
    private AgentPromptTemplateMapper mapper;

    private DefaultPromptTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        // 默认让 mapperProvider.getIfAvailable() 返回 mock mapper
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        registry = new DefaultPromptTemplateRegistry(
                new DefaultPromptTemplateRenderer(), mapperProvider);
    }

    // ==================== 辅助方法 ====================

    /** 构造 DB 模板实体 */
    private AgentPromptTemplateDO dbTemplate(String code, String content, String version) {
        AgentPromptTemplateDO t = new AgentPromptTemplateDO();
        t.setId("db-" + code);
        t.setTemplateCode(code);
        t.setTemplateName("DB-" + code);
        t.setContent(content);
        t.setVersion(version);
        t.setIsActive(true);
        return t;
    }

    // ==================== DB 命中测试 ====================

    @Nested
    @DisplayName("DB 命中测试")
    class DbHitTest {

        @Test
        @DisplayName("DB 存在生效模板时返回 DB 内容（不使用内置默认）")
        void shouldReturnDbContentWhenActiveTemplateExists() {
            String dbContent = "DB 自定义的 ReAct 格式说明";
            when(mapper.selectActiveByCode(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION))
                    .thenReturn(dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                            dbContent, "2.0.0"));

            String content = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(content).isEqualTo(dbContent);
            verify(mapper, times(1)).selectActiveByCode(
                    PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
        }

        @Test
        @DisplayName("DB 内容为空串时降级为内置默认")
        void shouldFallbackToBuiltInWhenDbContentEmpty() {
            when(mapper.selectActiveByCode(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION))
                    .thenReturn(dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                            "", "1.0.0"));

            String content = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            // 空串视为无效，降级为内置
            assertThat(content).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION));
        }

        @Test
        @DisplayName("DB 内容为 null 时降级为内置默认")
        void shouldFallbackToBuiltInWhenDbContentNull() {
            AgentPromptTemplateDO t = dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                    null, "1.0.0");
            when(mapper.selectActiveByCode(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION))
                    .thenReturn(t);

            String content = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(content).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION));
        }
    }

    // ==================== DB 异常 / 不可用降级测试 ====================

    @Nested
    @DisplayName("DB 异常降级测试")
    class DbFallbackTest {

        @Test
        @DisplayName("DB 查询抛异常时降级为内置默认")
        void shouldFallbackToBuiltInWhenDbThrowsException() {
            when(mapper.selectActiveByCode(anyString()))
                    .thenThrow(new RuntimeException("DB 连接失败"));

            String content = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(content).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION));
        }

        @Test
        @DisplayName("DB 返回 null 时降级为内置默认")
        void shouldFallbackToBuiltInWhenDbReturnsNull() {
            when(mapper.selectActiveByCode(anyString())).thenReturn(null);

            String content = registry.getTemplate(PromptTemplateCodes.FLOW_GENERATOR_SYSTEM);

            assertThat(content).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.FLOW_GENERATOR_SYSTEM));
        }

        @Test
        @DisplayName("Mapper 不可用时（无 DB 环境）降级为内置默认")
        void shouldFallbackToBuiltInWhenMapperUnavailable() {
            // 模拟 ObjectProvider.getIfAvailable() 返回 null（无 Spring 容器 / 无 DB）
            when(mapperProvider.getIfAvailable()).thenReturn(null);
            DefaultPromptTemplateRegistry r = new DefaultPromptTemplateRegistry(
                    new DefaultPromptTemplateRenderer(), mapperProvider);

            String content = r.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(content).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION));
            // 不应调用 mapper
            verify(mapper, never()).selectActiveByCode(anyString());
        }

        @Test
        @DisplayName("DB 与内置均无此 code 时返回空串")
        void shouldReturnEmptyWhenCodeNotFoundInDbAndBuiltIn() {
            when(mapper.selectActiveByCode("UNKNOWN_CODE")).thenReturn(null);

            String content = registry.getTemplate("UNKNOWN_CODE");

            assertThat(content).isEqualTo("");
        }
    }

    // ==================== 缓存测试 ====================

    @Nested
    @DisplayName("缓存测试")
    class CacheTest {

        @Test
        @DisplayName("第 2 次查询相同 code 不访问 DB（缓存命中）")
        void shouldUseCacheOnSecondQuery() {
            when(mapper.selectActiveByCode(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION))
                    .thenReturn(dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                            "DB content", "1.0.0"));

            // 第 1 次查询：访问 DB
            String first = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
            // 第 2 次查询：应命中缓存，不访问 DB
            String second = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            assertThat(first).isEqualTo("DB content");
            assertThat(second).isEqualTo("DB content");
            // selectActiveByCode 仅被调用 1 次
            verify(mapper, times(1)).selectActiveByCode(
                    PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
        }

        @Test
        @DisplayName("refresh() 清空缓存后第 2 次查询重新访问 DB")
        void shouldRequeryDbAfterRefresh() {
            // 第 1 次：返回 DB 内容 v1
            when(mapper.selectActiveByCode(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION))
                    .thenReturn(dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                            "DB v1", "1.0.0"));

            String first = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
            assertThat(first).isEqualTo("DB v1");

            // 刷新缓存
            registry.refresh();

            // 模拟 DB 内容已更新为 v2（激活了新版本）
            when(mapper.selectActiveByCode(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION))
                    .thenReturn(dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                            "DB v2", "2.0.0"));

            String second = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
            assertThat(second).isEqualTo("DB v2");

            // 共调用 2 次 DB
            verify(mapper, times(2)).selectActiveByCode(
                    PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
        }

        @Test
        @DisplayName("refresh() 后即使无 DB 也降级为内置")
        void shouldFallbackToBuiltInAfterRefresh() {
            // 第 1 次：DB 命中
            when(mapper.selectActiveByCode(anyString()))
                    .thenReturn(dbTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION,
                            "DB content", "1.0.0"));
            registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);

            // 模拟 DB 删除后返回 null
            when(mapper.selectActiveByCode(anyString())).thenReturn(null);
            registry.refresh();

            String content = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
            assertThat(content).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION));
        }
    }

    // ==================== render 渲染测试 ====================

    @Nested
    @DisplayName("render 渲染测试")
    class RenderTest {

        @Test
        @DisplayName("render() 使用内置模板进行变量替换")
        void shouldRenderBuiltInTemplateWithVariables() {
            when(mapper.selectActiveByCode(anyString())).thenReturn(null);

            Map<String, Object> params = new HashMap<>();
            params.put("description", "请假审批流程");

            String rendered = registry.render(PromptTemplateCodes.FLOW_GENERATOR_USER, params);

            assertThat(rendered).contains("请假审批流程");
            assertThat(rendered).doesNotContain("${description}");
        }

        @Test
        @DisplayName("render() 使用 DB 模板进行变量替换")
        void shouldRenderDbTemplateWithVariables() {
            when(mapper.selectActiveByCode(PromptTemplateCodes.FLOW_GENERATOR_USER))
                    .thenReturn(dbTemplate(PromptTemplateCodes.FLOW_GENERATOR_USER,
                            "Hello ${name}, welcome to ${place}", "1.0.0"));

            Map<String, Object> params = new HashMap<>();
            params.put("name", "张三");
            params.put("place", "南京");

            String rendered = registry.render(PromptTemplateCodes.FLOW_GENERATOR_USER, params);

            assertThat(rendered).isEqualTo("Hello 张三, welcome to 南京");
        }

        @Test
        @DisplayName("render() 不存在的 code 返回空串")
        void shouldReturnEmptyWhenRenderUnknownCode() {
            when(mapper.selectActiveByCode(anyString())).thenReturn(null);

            String rendered = registry.render("NON_EXISTENT", new HashMap<>());

            assertThat(rendered).isEqualTo("");
        }

        @Test
        @DisplayName("render() null params 返回原始模板（无替换）")
        void shouldReturnOriginalTemplateWhenParamsNull() {
            when(mapper.selectActiveByCode(anyString())).thenReturn(null);

            String rendered = registry.render(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION, null);

            // 内置模板无占位符，应原样返回
            assertThat(rendered).isEqualTo(
                    BuiltInPromptTemplates.get(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION));
        }

        @Test
        @DisplayName("render() 使用缓存的模板（不重复查询 DB）")
        void shouldUseCachedTemplateForRender() {
            when(mapper.selectActiveByCode(PromptTemplateCodes.FLOW_GENERATOR_USER))
                    .thenReturn(dbTemplate(PromptTemplateCodes.FLOW_GENERATOR_USER,
                            "Hello ${name}", "1.0.0"));

            Map<String, Object> params = new HashMap<>();
            params.put("name", "张三");

            registry.render(PromptTemplateCodes.FLOW_GENERATOR_USER, params);
            registry.render(PromptTemplateCodes.FLOW_GENERATOR_USER, params);

            // 缓存命中，仅查询 1 次 DB
            verify(mapper, times(1)).selectActiveByCode(
                    PromptTemplateCodes.FLOW_GENERATOR_USER);
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("null code 返回空串")
        void shouldReturnEmptyWhenCodeNull() {
            String content = registry.getTemplate(null);

            assertThat(content).isEqualTo("");
            verify(mapper, never()).selectActiveByCode(anyString());
        }

        @Test
        @DisplayName("空串 code 返回空串")
        void shouldReturnEmptyWhenCodeEmpty() {
            String content = registry.getTemplate("");

            assertThat(content).isEqualTo("");
            verify(mapper, never()).selectActiveByCode(anyString());
        }

        @Test
        @DisplayName("render() null code 返回空串")
        void shouldReturnEmptyWhenRenderNullCode() {
            String rendered = registry.render(null, new HashMap<>());

            assertThat(rendered).isEqualTo("");
        }

        @Test
        @DisplayName("render() 空 code 返回空串")
        void shouldReturnEmptyWhenRenderEmptyCode() {
            String rendered = registry.render("", new HashMap<>());

            assertThat(rendered).isEqualTo("");
        }

        @Test
        @DisplayName("多次 refresh() 不抛异常")
        void shouldNotThrowWhenRefreshMultipleTimes() {
            registry.refresh();
            registry.refresh();
            registry.refresh();

            // 多次清空缓存不会异常
            String content = registry.getTemplate(PromptTemplateCodes.REACT_FORMAT_INSTRUCTION);
            assertThat(content).isNotEmpty();
        }
    }

    // ==================== 综合 E2E 测试 ====================

    @Nested
    @DisplayName("综合 E2E 测试")
    class E2ETest {

        @Test
        @DisplayName("DB 优先 → 缓存 → refresh → 内置降级 全链路")
        void shouldCompleteFullLifecycle() {
            String code = PromptTemplateCodes.REACT_FORMAT_INSTRUCTION;

            // 1. 初始 DB 内容
            when(mapper.selectActiveByCode(code))
                    .thenReturn(dbTemplate(code, "DB v1", "1.0.0"));
            assertThat(registry.getTemplate(code)).isEqualTo("DB v1");

            // 2. 缓存命中
            assertThat(registry.getTemplate(code)).isEqualTo("DB v1");

            // 3. DB 更新为 v2，但缓存未刷新 → 仍返回 v1
            when(mapper.selectActiveByCode(code))
                    .thenReturn(dbTemplate(code, "DB v2", "2.0.0"));
            assertThat(registry.getTemplate(code)).isEqualTo("DB v1");

            // 4. refresh 后查询 DB，返回 v2
            registry.refresh();
            assertThat(registry.getTemplate(code)).isEqualTo("DB v2");

            // 5. DB 删除，refresh 后降级为内置
            when(mapper.selectActiveByCode(code)).thenReturn(null);
            registry.refresh();
            assertThat(registry.getTemplate(code)).isEqualTo(BuiltInPromptTemplates.get(code));
        }
    }
}
