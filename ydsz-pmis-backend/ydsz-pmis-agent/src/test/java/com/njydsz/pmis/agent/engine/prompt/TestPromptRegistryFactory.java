package com.njydsz.pmis.agent.engine.prompt;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试专用 PromptTemplateRegistry 工厂（P2-2）。
 *
 * <p>创建一个使用内置默认模板的 mock registry，供单元测试使用。
 * 避免在每个测试类中重复编写 mock 配置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
public final class TestPromptRegistryFactory {

    private TestPromptRegistryFactory() {}

    /**
     * 创建使用内置默认模板的 PromptTemplateRegistry mock。
     *
     * <p>mock 行为：
     * <ul>
     *   <li>{@code getTemplate(code)} 返回 {@link BuiltInPromptTemplates#get(code)}</li>
     *   <li>{@code render(code, params)} 使用 {@link DefaultPromptTemplateRenderer} 渲染内置模板</li>
     * </ul>
     *
     * @return mock registry
     */
    public static PromptTemplateRegistry createWithBuiltInDefaults() {
        PromptTemplateRegistry registry = mock(PromptTemplateRegistry.class);
        PromptTemplateRenderer renderer = new DefaultPromptTemplateRenderer();

        when(registry.getTemplate(anyString())).thenAnswer(inv ->
                BuiltInPromptTemplates.get(inv.getArgument(0)));

        when(registry.render(anyString(), any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            Map<String, Object> params = inv.getArgument(1);
            String template = BuiltInPromptTemplates.get(code);
            return template == null ? "" : renderer.render(template, params);
        });

        return registry;
    }
}
