package com.njydsz.pmis.agent.integration;

import com.njydsz.pmis.agent.aop.TokenQuotaAspect;
import com.njydsz.pmis.agent.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.engine.RiskWarningAgent;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.llm.MockLlmProvider;
import com.njydsz.pmis.agent.engine.trace.AgentTracer;
import com.njydsz.pmis.agent.service.impl.AgentServiceImpl;
import com.njydsz.pmis.agent.service.impl.DefaultTokenQuotaService;
import com.njydsz.pmis.common.config.AsyncThreadPoolConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Agent 模块集成测试专用 Spring 配置（P2-7 落地）。
 *
 * <p><b>设计目标</b>：在 <b>无 Nacos / 无 PostgreSQL / 无 Redis</b> 的 CI 环境下，
 * 加载 agent 模块的核心业务组件（LlmProviderRouter / TokenQuotaService / TokenQuotaAspect /
 * AgentServiceImpl / RiskWarningAgent / MockLlmProvider / AgentTracer），覆盖
 * 「启动 / Provider 路由 / Token 配额扣减 / 事务行为」四个集成验证场景。
 *
 * <p><b>关键策略</b>：
 * <ol>
 *   <li>使用 {@link SpringBootConfiguration} 替代 {@code @TestConfiguration}，
 *       使本类成为 SpringBoot 主配置类（避免 SpringBoot 自动发现
 *       {@code AgentApplication} 导致加载 Nacos / Feign 等外部依赖）</li>
 *   <li><b>不启用</b> {@code @EnableAutoConfiguration}，避免 agent 模块依赖的
 *       Druid / Seata / spring-ai / Resilience4j / dynamic-datasource 等第三方库的
 *       自动配置在无中间件环境下启动失败</li>
 *   <li>通过 {@link EnableConfigurationProperties} 注册 {@link TokenQuotaProperties}，
 *       触发 {@code @ConfigurationProperties} 属性绑定</li>
 *   <li>通过 {@link Import} 显式导入所需业务组件，Mapper 由 {@code @MockitoBean} 提供</li>
 *   <li>显式声明 {@link AgentTracer#noOp()} 作为 {@link AgentTracer} Bean，
 *       避免加载 Tracing 组件需要的 Brave / OpenTelemetry 依赖</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-7)
 */
@SpringBootConfiguration
@EnableConfigurationProperties(TokenQuotaProperties.class)
@Import({
        AsyncThreadPoolConfig.class,
        MockLlmProvider.class,
        RiskWarningAgent.class,
        LlmProviderRouter.class,
        DefaultTokenQuotaService.class,
        TokenQuotaAspect.class,
        AgentServiceImpl.class
})
public class TestAgentModuleConfig {

    /**
     * 显式声明 NoOpAgentTracer 作为 AgentTracer Bean。
     *
     * <p>避免加载 DefaultAgentTracer 依赖的 Brave / OpenTelemetry 等组件。
     * 通过 {@link AgentTracer#noOp()} 工厂方法获取单例实例。
     *
     * @return NoOp tracer 实现
     */
    @Bean
    public AgentTracer agentTracer() {
        return AgentTracer.noOp();
    }

    /**
     * 测试用 Stub Provider Bean（用于 Provider 路由多 Provider 场景验证）。
     *
     * <p>{@code name()} 返回 {@code "stub-test"}，配合配置
     * {@code pmis.agent.llm.provider=stub-test} 验证路由器按 {@code name()} 精确匹配。
     *
     * @return stub LlmProvider
     */
    @Bean
    public LlmProvider stubTestLlmProvider() {
        return new StubLlmProvider("stub-test", "stub-response-from-integration-test");
    }

    /**
     * 测试用 Stub LlmProvider（P2-7 集成测试专用）。
     */
    public static class StubLlmProvider implements LlmProvider {
        private final String name;
        private final String response;

        public StubLlmProvider(String name, String response) {
            this.name = name;
            this.response = response;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String chat(String systemPrompt, String userPrompt,
                           com.njydsz.pmis.agent.engine.AgentContext context) {
            return response;
        }
    }
}
