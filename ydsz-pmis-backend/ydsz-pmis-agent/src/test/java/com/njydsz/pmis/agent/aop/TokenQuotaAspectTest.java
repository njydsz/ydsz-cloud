package com.njydsz.pmis.agent.aop;

import com.njydsz.pmis.agent.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.dto.TokenUsage;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.service.TokenQuotaService;
import com.njydsz.pmis.common.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TokenQuotaAspect} 切面单元测试（P1-2 修复验证）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>P1-2: chat 方法被拦截，token 被统计</li>
 *   <li>P1-2: chatForJson 方法被拦截，token 被统计（核心修复点）</li>
 *   <li>chatForJson 返回对象时 completion token 基于 JSON 序列化估算</li>
 *   <li>异常时仍记录使用量（completion=0）并抛出原异常</li>
 *   <li>租户 ID 从 TenantContext 获取</li>
 * </ul>
 *
 * <p>不启动 Spring 容器，直接调用 {@link TokenQuotaAspect#aroundLlmCall} 方法，
 * 通过 mock {@link ProceedingJoinPoint} 模拟 AOP 连接点。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenQuotaAspect Token 配额切面测试")
class TokenQuotaAspectTest {

    @Mock
    private TokenQuotaService tokenQuotaService;
    @Mock
    private ProceedingJoinPoint pjp;
    @Mock
    private Signature signature;
    @Mock
    private LlmProvider llmProvider;

    private TokenQuotaAspect aspect;
    private TokenQuotaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TokenQuotaProperties();
        properties.setEnabled(true);
        aspect = new TokenQuotaAspect(tokenQuotaService, properties);
        TenantContext.setTenantId("tenant-test");
        when(llmProvider.name()).thenReturn("mock");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 辅助方法 ====================

    /** 配置 pjp 模拟 chat 方法调用 */
    private void mockChatCall(String systemPrompt, String userPrompt, AgentContext ctx,
                              Object proceedResult) throws Throwable {
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("chat");
        when(pjp.getArgs()).thenReturn(new Object[]{systemPrompt, userPrompt, ctx});
        when(pjp.getTarget()).thenReturn(llmProvider);
        when(pjp.proceed()).thenReturn(proceedResult);
    }

    /** 配置 pjp 模拟 chatForJson 方法调用 */
    private void mockChatForJsonCall(String systemPrompt, String userPrompt, AgentContext ctx,
                                     Class<?> type, Object proceedResult) throws Throwable {
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("chatForJson");
        when(pjp.getArgs()).thenReturn(new Object[]{systemPrompt, userPrompt, type, ctx});
        when(pjp.getTarget()).thenReturn(llmProvider);
        when(pjp.proceed()).thenReturn(proceedResult);
    }

    // ==================== chat 拦截测试 ====================

    @Nested
    @DisplayName("chat 方法拦截")
    class ChatInterceptTest {

        @Test
        @DisplayName("chat 调用前后均统计 token")
        void shouldRecordUsageForChat() throws Throwable {
            AgentContext ctx = new AgentContext();
            ctx.setTraceId("trace-001");
            ctx.setBizRef("REF-001");
            mockChatCall("系统提示", "用户问题", ctx, "LLM 返回的文本结果");

            Object result = aspect.aroundLlmCall(pjp);

            assertThat(result).isEqualTo("LLM 返回的文本结果");
            // 验证调用前检查配额
            verify(tokenQuotaService, times(1)).checkQuota(eq("tenant-test"), anyLong());
            // 验证调用后记录使用量
            ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
            verify(tokenQuotaService, times(1)).recordUsage(captor.capture());
            TokenUsage usage = captor.getValue();
            assertThat(usage.getTenantId()).isEqualTo("tenant-test");
            assertThat(usage.getProvider()).isEqualTo("mock");
            assertThat(usage.getTraceId()).isEqualTo("trace-001");
            assertThat(usage.getBizRef()).isEqualTo("REF-001");
            assertThat(usage.getPromptTokens()).isGreaterThan(0);
            assertThat(usage.getCompletionTokens()).isGreaterThan(0);
            assertThat(usage.getTotalTokens())
                    .isEqualTo(usage.getPromptTokens() + usage.getCompletionTokens());
        }

        @Test
        @DisplayName("chat 异常时记录使用量（completion=0）并抛出原异常")
        void shouldRecordUsageAndRethrowOnChatError() throws Throwable {
            AgentContext ctx = new AgentContext();
            RuntimeException llmError = new RuntimeException("LLM 服务不可用");
            when(pjp.getSignature()).thenReturn(signature);
            when(signature.getName()).thenReturn("chat");
            when(pjp.getArgs()).thenReturn(new Object[]{"sys", "user", ctx});
            when(pjp.getTarget()).thenReturn(llmProvider);
            when(pjp.proceed()).thenThrow(llmError);

            assertThatThrownBy(() -> aspect.aroundLlmCall(pjp))
                    .isSameAs(llmError);

            // 验证调用前检查配额
            verify(tokenQuotaService, times(1)).checkQuota(eq("tenant-test"), anyLong());
            // 验证异常时记录使用量（completion=0）
            ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
            verify(tokenQuotaService, times(1)).recordUsage(captor.capture());
            assertThat(captor.getValue().getCompletionTokens()).isZero();
            assertThat(captor.getValue().getPromptTokens()).isGreaterThan(0);
        }
    }

    // ==================== chatForJson 拦截测试（P1-2 核心） ====================

    @Nested
    @DisplayName("chatForJson 方法拦截（P1-2 核心修复）")
    class ChatForJsonInterceptTest {

        @Test
        @DisplayName("chatForJson 调用前后均统计 token")
        void shouldRecordUsageForChatForJson() throws Throwable {
            AgentContext ctx = new AgentContext();
            ctx.setTraceId("trace-002");
            // chatForJson 返回解析后的对象（非 String）
            Object jsonObj = new TestResult("风险预警", 85);

            mockChatForJsonCall("系统提示", "用户问题", ctx, TestResult.class, jsonObj);

            Object result = aspect.aroundLlmCall(pjp);

            assertThat(result).isSameAs(jsonObj);
            // 验证调用前检查配额
            verify(tokenQuotaService, times(1)).checkQuota(eq("tenant-test"), anyLong());
            // 验证调用后记录使用量
            ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
            verify(tokenQuotaService, times(1)).recordUsage(captor.capture());
            TokenUsage usage = captor.getValue();
            assertThat(usage.getTenantId()).isEqualTo("tenant-test");
            assertThat(usage.getProvider()).isEqualTo("mock");
            assertThat(usage.getTraceId()).isEqualTo("trace-002");
            assertThat(usage.getPromptTokens()).isGreaterThan(0);
            // chatForJson 返回对象，completion token 基于序列化后的 JSON 估算
            assertThat(usage.getCompletionTokens()).isGreaterThan(0);
        }

        @Test
        @DisplayName("chatForJson 返回 null 时 completion token 为 0")
        void shouldHandleNullChatForJsonResult() throws Throwable {
            AgentContext ctx = new AgentContext();
            mockChatForJsonCall("sys", "user", ctx, TestResult.class, null);

            Object result = aspect.aroundLlmCall(pjp);

            assertThat(result).isNull();
            verify(tokenQuotaService, times(1)).checkQuota(anyString(), anyLong());
            ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
            verify(tokenQuotaService, times(1)).recordUsage(captor.capture());
            assertThat(captor.getValue().getCompletionTokens()).isZero();
        }

        @Test
        @DisplayName("chatForJson 异常时记录使用量并抛出原异常")
        void shouldRecordUsageAndRethrowOnChatForJsonError() throws Throwable {
            AgentContext ctx = new AgentContext();
            RuntimeException error = new RuntimeException("JSON 解析失败");
            when(pjp.getSignature()).thenReturn(signature);
            when(signature.getName()).thenReturn("chatForJson");
            when(pjp.getArgs()).thenReturn(new Object[]{"sys", "user", TestResult.class, ctx});
            when(pjp.getTarget()).thenReturn(llmProvider);
            when(pjp.proceed()).thenThrow(error);

            assertThatThrownBy(() -> aspect.aroundLlmCall(pjp))
                    .isSameAs(error);

            verify(tokenQuotaService, times(1)).checkQuota(eq("tenant-test"), anyLong());
            ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
            verify(tokenQuotaService, times(1)).recordUsage(captor.capture());
            assertThat(captor.getValue().getCompletionTokens()).isZero();
        }
    }

    // ==================== 租户上下文测试 ====================

    @Nested
    @DisplayName("租户上下文")
    class TenantContextTest {

        @Test
        @DisplayName("tenantId 从 TenantContext 获取")
        void shouldResolveTenantIdFromContext() throws Throwable {
            TenantContext.clear();
            TenantContext.setTenantId("tenant-from-context");
            mockChatCall("sys", "user", new AgentContext(), "result");

            aspect.aroundLlmCall(pjp);

            verify(tokenQuotaService, times(1)).checkQuota(eq("tenant-from-context"), anyLong());
        }

        @Test
        @DisplayName("TenantContext 未设置时使用默认租户")
        void shouldUseDefaultTenantWhenNotSet() throws Throwable {
            TenantContext.clear();
            mockChatCall("sys", "user", new AgentContext(), "result");

            aspect.aroundLlmCall(pjp);

            verify(tokenQuotaService, times(1))
                    .checkQuota(eq(TenantContext.DEFAULT_TENANT_ID), anyLong());
        }
    }

    // ==================== 测试辅助 DTO ====================

    /** chatForJson 测试用的简单 DTO */
    @lombok.Data
    @lombok.AllArgsConstructor
    static class TestResult {
        private String name;
        private int score;
    }
}
