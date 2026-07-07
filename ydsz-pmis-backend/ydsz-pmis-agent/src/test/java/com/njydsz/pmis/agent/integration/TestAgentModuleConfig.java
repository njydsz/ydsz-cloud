package com.njydsz.pmis.agent.integration;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.njydsz.pmis.agent.aop.TokenQuotaAspect;
import com.njydsz.pmis.agent.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.engine.RiskWarningAgent;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.llm.MockLlmProvider;
import com.njydsz.pmis.agent.engine.trace.AgentTracer;
import com.njydsz.pmis.agent.engine.trace.NoOpAgentTracer;
import com.njydsz.pmis.agent.service.impl.AgentServiceImpl;
import com.njydsz.pmis.agent.service.impl.DefaultTokenQuotaService;
import com.njydsz.pmis.common.config.AsyncThreadPoolConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
 *   <li>通过 {@link EnableAutoConfiguration#exclude()} 排除 DataSource / Redis /
 *       Flyway / Liquibase / MyBatis-Plus 自动配置，避免启动时拉取外部中间件</li>
 *   <li>Mapper Bean 由测试类通过 {@code @MockBean} 提供，避免真实 DB 调用</li>
 *   <li>显式 {@link Import} {@link AsyncThreadPoolConfig} 提供 {@code agentExecutor}，
 *       满足 {@link AgentServiceImpl} 的 {@code @Qualifier(AsyncExecutorNames.AGENT)} 依赖</li>
 *   <li>显式声明 {@link NoOpAgentTracer} 作为 {@link AgentTracer} Bean，
 *       避免加载 Tracing 组件需要的 Brave / OpenTelemetry 依赖</li>
 *   <li>仅 {@link Import} 必要的 {@link Agent} 实现（{@link RiskWarningAgent}），
 *       排除其他可能引入额外依赖的 Agent</li>
 * </ol>
 *
 * <p><b>覆盖范围</b>：
 * <ul>
 *   <li>容器启动：验证所有核心 Bean 能正常装配（无 UnsatisfiedDependency / NoUniqueBean）</li>
 *   <li>Provider 路由：{@link LlmProviderRouter#active()} 按 {@code pmis.agent.llm.provider=mock}
 *       正确返回 {@link MockLlmProvider}</li>
 *   <li>Token 配额扣减：{@link TokenQuotaAspect} 拦截 {@code LlmProvider.chat}，
 *       调用 {@code TokenQuotaService.recordUsage} 写入明细 + 递增配额</li>
 *   <li>事务行为：{@link AgentServiceImpl#run} 在异常时 status=FAILED 并回滚业务态</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-7)
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        RedisAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@EnableTransactionManagement
@Import({
        AsyncThreadPoolConfig.class,
        TokenQuotaProperties.class,
        MockLlmProvider.class,
        RiskWarningAgent.class,
        LlmProviderRouter.class,
        DefaultTokenQuotaService.class,
        TokenQuotaAspect.class,
        AgentServiceImpl.class
})
@ComponentScan(basePackages = "com.njydsz.pmis.common.config")
public class TestAgentModuleConfig {

    /**
     * 显式声明 NoOpAgentTracer 作为 AgentTracer Bean。
     *
     * <p>避免加载 DefaultAgentTracer 依赖的 Brave / OpenTelemetry 等组件。
     *
     * @return NoOp tracer 实现
     */
    @Bean
    public AgentTracer agentTracer() {
        return new NoOpAgentTracer();
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
