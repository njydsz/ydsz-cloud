package com.njydsz.pmis.agent.integration;

import com.njydsz.pmis.agent.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.dto.TokenUsage;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.llm.MockLlmProvider;
import com.njydsz.pmis.agent.engine.trace.AgentTracer;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.entity.TokenQuotaDO;
import com.njydsz.pmis.agent.entity.TokenUsageLogDO;
import com.njydsz.pmis.agent.enums.AgentRunStatus;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.agent.mapper.AgentPromptTemplateMapper;
import com.njydsz.pmis.agent.mapper.AgentTraceMapper;
import com.njydsz.pmis.agent.mapper.TokenQuotaMapper;
import com.njydsz.pmis.agent.mapper.TokenUsageLogMapper;
import com.njydsz.pmis.agent.service.AgentService;
import com.njydsz.pmis.agent.service.TokenQuotaService;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 模块集成测试（P2-7 落地）。
 *
 * <p>使用 {@link SpringBootTest} 加载 {@link TestAgentModuleConfig} 自定义配置，
 * 在 <b>无 Nacos / 无 PostgreSQL / 无 Redis</b> 的 CI 环境下完成 4 个核心场景的集成验证：
 *
 * <ol>
 *   <li><b>容器启动</b>：所有核心 Bean（LlmProviderRouter / TokenQuotaService /
 *       TokenQuotaAspect / AgentServiceImpl / RiskWarningAgent / MockLlmProvider /
 *       AgentTracer / agentExecutor）能正常装配</li>
 *   <li><b>Provider 路由</b>：{@link LlmProviderRouter#active()} 按
 *       {@code pmis.agent.llm.provider=mock} 配置精确匹配，返回 {@link MockLlmProvider}</li>
 *   <li><b>Token 配额扣减</b>：{@code TokenQuotaService.checkQuota / recordUsage}
 *       正确协作，配额不足抛 {@link BizException}，配额充足时写入明细 + 原子递增</li>
 *   <li><b>事务行为</b>：{@link AgentServiceImpl#run} 在 Agent 执行成功时 status=SUCCESS，
 *       在无效 agentType 时抛 {@link BizException} 并提前返回（不触发 insert）</li>
 * </ol>
 *
 * <p><b>外部依赖</b>：5 个 Mapper 通过 {@link MockitoBean} 提供 Mock 实现，
 * 不连接真实 DB；AgentService / TokenQuotaService 等业务组件使用真实实例，
 * 便于验证 Bean 装配与跨组件协作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-7)
 */
@SpringBootTest(classes = TestAgentModuleConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "pmis.agent.llm.provider=mock",
        "pmis.agent.llm.timeout-millis=5000",
        "pmis.agent.llm.max-retries=0",
        "pmis.agent.llm.fallback-to-mock=true",
        "pmis.agent.token-quota.enabled=true",
        "pmis.agent.token-quota.default-monthly-quota=1000000",
        "pmis.agent.token-quota.auto-init=true",
        "pmis.agent.react.max-steps=10",
        "pmis.agent.tool.mock-enabled=true"
})
@DisplayName("P2-7: Agent 模块集成测试（启动+路由+配额+事务）")
class IntegrationTestForAgentModuleTest {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    // ==================== Spring 注入 ====================

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private LlmProviderRouter llmProviderRouter;
    @Autowired
    private AgentService agentService;
    @Autowired
    private TokenQuotaService tokenQuotaService;
    @Autowired
    private TokenQuotaProperties tokenQuotaProperties;
    @Autowired
    @Qualifier("agentExecutor")
    private ThreadPoolTaskExecutor agentExecutor;

    // ==================== MockitoBean Mapper ====================

    @MockitoBean
    private AgentPredictionMapper agentPredictionMapper;
    @MockitoBean
    private AgentPromptTemplateMapper agentPromptTemplateMapper;
    @MockitoBean
    private AgentTraceMapper agentTraceMapper;
    @MockitoBean
    private TokenQuotaMapper tokenQuotaMapper;
    @MockitoBean
    private TokenUsageLogMapper tokenUsageLogMapper;

    // ==================== 公共夹具 ====================

    @BeforeEach
    void setUpTenant() {
        TenantContext.setTenantId("test-tenant-001");
        // 公共 Mapper 行为：insert / updateById 返回 1
        when(agentPredictionMapper.insert(any(AgentPredictionDO.class))).thenReturn(1);
        when(agentPredictionMapper.updateById(any(AgentPredictionDO.class))).thenReturn(1);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ==================== 场景 1：容器启动 ====================

    @Nested
    @DisplayName("场景 1: Spring 容器启动 - 核心 Bean 装配")
    class ContainerStartupTest {

        @Test
        @DisplayName("ApplicationContext 能加载，未抛出启动异常")
        void testSpringContainerStartup() {
            assertThat(applicationContext).isNotNull();
            // ApplicationContext 加载成功即表示启动未抛异常
            assertThat(applicationContext.getBean(LlmProviderRouter.class)).isNotNull();
        }

        @Test
        @DisplayName("核心 Bean 均已注入：LlmProviderRouter / AgentService / TokenQuotaService / AgentTracer / agentExecutor")
        void testCoreBeansInjected() {
            assertThat(llmProviderRouter).isNotNull();
            assertThat(agentService).isNotNull();
            assertThat(tokenQuotaService).isNotNull();
            assertThat(applicationContext.getBean(AgentTracer.class)).isNotNull();
            assertThat(agentExecutor).isNotNull();
        }

        @Test
        @DisplayName("LlmProvider Bean 数量 = 2（MockLlmProvider + StubLlmProvider）")
        void testLlmProviderBeansRegistered() {
            Map<String, LlmProvider> providers =
                    applicationContext.getBeansOfType(LlmProvider.class);
            assertThat(providers).hasSize(2);
            assertThat(providers.values()).extracting(LlmProvider::name)
                    .containsExactlyInAnyOrder("mock", "stub-test");
        }

        @Test
        @DisplayName("Agent Bean 已注册（RiskWarningAgent）")
        void testAgentBeanRegistered() {
            Map<String, Agent> agents = applicationContext.getBeansOfType(Agent.class);
            assertThat(agents).isNotEmpty();
            assertThat(agents.values()).extracting(Agent::type)
                    .contains(AgentType.RISK_WARNING);
        }

        @Test
        @DisplayName("TokenQuotaProperties 配置已正确绑定（enabled=true, quota=1000000）")
        void testTokenQuotaPropertiesBound() {
            assertThat(tokenQuotaProperties.isEnabled()).isTrue();
            assertThat(tokenQuotaProperties.getDefaultMonthlyQuota()).isEqualTo(1_000_000L);
            assertThat(tokenQuotaProperties.isAutoInit()).isTrue();
        }
    }

    // ==================== 场景 2：Provider 路由 ====================

    @Nested
    @DisplayName("场景 2: LLM Provider 路由")
    class LlmProviderRoutingTest {

        @Test
        @DisplayName("配置 pmis.agent.llm.provider=mock 时，active() 返回 MockLlmProvider")
        void testLlmProviderRouting() {
            LlmProvider active = llmProviderRouter.active();

            assertThat(active).isNotNull();
            assertThat(active).isInstanceOf(MockLlmProvider.class);
            assertThat(active.name()).isEqualTo("mock");
        }

        @Test
        @DisplayName("getActiveProviderName() 返回 'mock'")
        void testGetActiveProviderName() {
            String name = llmProviderRouter.getActiveProviderName();

            assertThat(name).isEqualTo("mock");
        }

        @Test
        @DisplayName("reload('stub-test') 后切换到 StubLlmProvider")
        void testReloadToStubProvider() {
            // 初始为 mock
            assertThat(llmProviderRouter.getActiveProviderName()).isEqualTo("mock");

            // 切换到 stub-test
            llmProviderRouter.reload("stub-test");

            LlmProvider active = llmProviderRouter.active();
            assertThat(active.name()).isEqualTo("stub-test");
            assertThat(active.chat("", "", new AgentContext()))
                    .isEqualTo("stub-response-from-integration-test");
        }

        @Test
        @DisplayName("reload('non-existent') 降级到 MockLlmProvider")
        void testReloadFallbackToMock() {
            llmProviderRouter.reload("non-existent-provider");

            LlmProvider active = llmProviderRouter.active();
            assertThat(active).isInstanceOf(MockLlmProvider.class);
        }
    }

    // ==================== 场景 3：Token 配额扣减 ====================

    @Nested
    @DisplayName("场景 3: Token 配额扣减")
    class TokenQuotaConsumptionTest {

        @Test
        @DisplayName("checkQuota 在配额充足时不抛异常")
        void testCheckQuotaSufficient() {
            // 准备：当月配额记录
            TokenQuotaDO quota = buildQuota("test-tenant-001", 1_000_000L, 0L);
            when(tokenQuotaMapper.selectByTenantAndMonth(eq("test-tenant-001"),
                    anyString())).thenReturn(quota);

            // 执行：检查 1000 token
            tokenQuotaService.checkQuota("test-tenant-001", 1000L);

            // 验证：未抛异常即表示配额充足
            verify(tokenQuotaMapper).selectByTenantAndMonth(eq("test-tenant-001"),
                    eq(LocalDateTime.now().format(MONTH_FMT)));
        }

        @Test
        @DisplayName("checkQuota 在配额不足时抛 BizException")
        void testCheckQuotaInsufficient() {
            TokenQuotaDO quota = buildQuota("test-tenant-001", 500L, 400L);
            when(tokenQuotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota);

            // 需求 200，剩余 100，配额不足
            // BizException message 为错误码 error.agent.token_quota_exceeded，
            // 租户 ID 作为参数传入（非 message 拼接），故断言错误码即可
            assertThatThrownBy(() -> tokenQuotaService.checkQuota("test-tenant-001", 200L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.agent.token_quota_exceeded");
        }

        @Test
        @DisplayName("recordUsage 写入明细 + 原子递增配额")
        void testRecordUsageWritesLogAndIncrementsQuota() {
            TokenQuotaDO quota = buildQuota("test-tenant-001", 1_000_000L, 1000L);
            when(tokenQuotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota);
            when(tokenQuotaMapper.incrementUsedTokens(anyString(), anyLong())).thenReturn(1);
            when(tokenUsageLogMapper.insert(any(TokenUsageLogDO.class))).thenReturn(1);

            TokenUsage usage = TokenUsage.builder()
                    .tenantId("test-tenant-001")
                    .provider("mock")
                    .promptTokens(100)
                    .completionTokens(50)
                    .costMs(200L)
                    .build();
            tokenQuotaService.recordUsage(usage);

            // 验证：明细写入
            verify(tokenUsageLogMapper, atLeastOnce()).insert(any(TokenUsageLogDO.class));
            // 验证：配额递增（delta = 150）
            verify(tokenQuotaMapper, atLeastOnce())
                    .incrementUsedTokens(eq(quota.getId()), eq(150L));
        }

        @Test
        @DisplayName("首次访问时自动初始化当月配额（autoInit=true）")
        void testAutoInitQuota() {
            // 首次查询返回 null
            when(tokenQuotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(null);
            when(tokenQuotaMapper.insert(any(TokenQuotaDO.class))).thenReturn(1);
            when(tokenQuotaMapper.incrementUsedTokens(anyString(), anyLong())).thenReturn(1);

            TokenUsage usage = TokenUsage.builder()
                    .tenantId("new-tenant-002")
                    .provider("mock")
                    .promptTokens(10)
                    .completionTokens(5)
                    .costMs(50L)
                    .build();
            tokenQuotaService.recordUsage(usage);

            // 验证：自动初始化触发 insert
            verify(tokenQuotaMapper, atLeastOnce()).insert(any(TokenQuotaDO.class));
        }
    }

    // ==================== 场景 4：事务行为 ====================

    @Nested
    @DisplayName("场景 4: Agent 事务行为")
    class TransactionBehaviorTest {

        @Test
        @DisplayName("Agent 执行成功时 status=SUCCESS 并 updateById")
        void testAgentSuccess() {
            AgentRunRequestDTO req = new AgentRunRequestDTO();
            req.setAgentType(AgentType.RISK_WARNING.getCode());
            req.setBizType("PROJECT");
            req.setBizId("P-2026-001");
            req.setBizRef("测试项目");
            req.setCallerId("user-001");
            req.setCallerName("测试用户");
            req.setSource("MANUAL");
            req.setParams(Map.of("cpi", 0.8, "spi", 0.9));

            AgentPredictionDO result = agentService.run(req);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(AgentRunStatus.SUCCESS.getCode());
            assertThat(result.getAgentType()).isEqualTo(AgentType.RISK_WARNING.getCode());
            assertThat(result.getTaskCode()).isNotBlank();
            // 验证：先 insert 创建 RUNNING 记录，再 updateById 更新为 SUCCESS
            verify(agentPredictionMapper, atLeastOnce()).insert(any(AgentPredictionDO.class));
            verify(agentPredictionMapper, atLeastOnce()).updateById(any(AgentPredictionDO.class));
        }

        @Test
        @DisplayName("DB insert 失败时异常正确传播（RuntimeException）")
        void testAgentFailure() {
            // 模拟 DB 故障：predictionMapper.insert 抛 RuntimeException
            // AgentServiceImpl.run 在 try 块之前调用 insert，异常直接传播（不进入 catch）
            // 验证异常不被吞掉，便于上层统一处理
            org.mockito.Mockito.reset(agentPredictionMapper);
            when(agentPredictionMapper.insert(any(AgentPredictionDO.class)))
                    .thenThrow(new RuntimeException("DB 故障：insert 失败"));

            AgentRunRequestDTO req = new AgentRunRequestDTO();
            req.setAgentType(AgentType.RISK_WARNING.getCode());
            req.setBizType("PROJECT");
            req.setBizId("P-2026-002");
            req.setBizRef("故障测试项目");
            req.setCallerId("user-001");
            req.setCallerName("测试用户");
            req.setSource("MANUAL");

            // 验证：insert 失败时抛出 RuntimeException
            assertThatThrownBy(() -> agentService.run(req))
                    .isInstanceOf(RuntimeException.class);

            // 验证：insert 被调用过（且抛异常）
            verify(agentPredictionMapper, atLeastOnce()).insert(any(AgentPredictionDO.class));
        }

        @Test
        @DisplayName("无效 agentType 时抛 BizException 并提前返回（不触发 insert）")
        void testInvalidAgentType() {
            AgentRunRequestDTO req = new AgentRunRequestDTO();
            req.setAgentType("INVALID_TYPE");
            req.setBizType("PROJECT");
            req.setBizId("P-2026-003");

            assertThatThrownBy(() -> agentService.run(req))
                    .isInstanceOf(BizException.class);

            // 验证：未触发 insert（提前校验失败）
            verify(agentPredictionMapper, never()).insert(any(AgentPredictionDO.class));
        }

        @Test
        @DisplayName("run 成功后预测记录包含正确的告警等级与 score")
        void testRunResultContainsAlertLevel() {
            AgentRunRequestDTO req = new AgentRunRequestDTO();
            req.setAgentType(AgentType.RISK_WARNING.getCode());
            req.setBizType("PROJECT");
            req.setBizId("P-2026-004");
            req.setBizRef("高风险项目");
            req.setCallerId("user-001");
            req.setCallerName("测试用户");
            req.setSource("MANUAL");
            // CPI=0.7, SPI=0.7, costOverrun=0.25, grossMargin=-0.1, highRiskCount=2
            // 命中规则应触发 RED 等级
            req.setParams(Map.of(
                    "cpi", 0.7,
                    "spi", 0.7,
                    "costOverrun", 0.25,
                    "grossMargin", -0.1,
                    "highRiskCount", 2,
                    "riskCount", 5));

            AgentPredictionDO result = agentService.run(req);

            assertThat(result.getStatus()).isEqualTo(AgentRunStatus.SUCCESS.getCode());
            assertThat(result.getAlertLevel()).isNotNull();
            // score = 0.35(CPI) + 0.20(SPI) + 0.20(超支) + 0.15(负毛利) + 0.10(高风) + 0.05(风数) = 1.05 → capped 1.0
            // 命中 ≥0.55 → RED
            assertThat(result.getAlertLevel()).isEqualTo("RED");
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造一个 TokenQuotaDO 配额记录 */
    private TokenQuotaDO buildQuota(String tenantId, long total, long used) {
        TokenQuotaDO quota = new TokenQuotaDO();
        quota.setId("quota-" + tenantId);
        quota.setTenantId(tenantId);
        quota.setQuotaMonth(LocalDateTime.now().format(MONTH_FMT));
        quota.setTotalQuota(total);
        quota.setUsedTokens(used);
        quota.setStatus("ACTIVE");
        quota.setResetAt(LocalDateTime.now());
        return quota;
    }
}
