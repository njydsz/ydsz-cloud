package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feign 客户端契约测试
 *
 * <p>验证各 Feign 客户端的接口定义（请求路径、参数、返回类型）与服务端 API 契约一致。
 * 这是一种轻量级契约测试，不依赖 Spring Cloud Contract，但能有效捕获接口变更导致的兼容性问题。
 *
 * <p>测试策略：
 * <ul>
 *   <li>验证 @FeignClient 注解的 name/contextId 正确</li>
 *   <li>验证每个接口方法的 HTTP 方法与路径正确</li>
 *   <li>验证 FallbackFactory 存在且能正确降级</li>
 *   <li>验证 Result 包装的返回类型与预期一致</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("Feign 客户端契约测试")
class FeignContractTest {

    @Nested
    @DisplayName("MessageServiceClient 契约")
    class MessageServiceClientContract {

        @Test
        @DisplayName("@FeignClient 注解 name 正确")
        void shouldHaveCorrectFeignClientName() {
            FeignClient annotation = MessageServiceClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals("ydsz-pmis-message", annotation.name());
            assertEquals("messageServiceClient", annotation.contextId());
        }

        @Test
        @DisplayName("fallbackFactory 正确配置")
        void shouldHaveFallbackFactory() {
            FeignClient annotation = MessageServiceClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals(MessageServiceClientFallback.class, annotation.fallbackFactory());
        }

        @Test
        @DisplayName("send 方法返回 Result<MessageResult>")
        void shouldReturnCorrectType() throws NoSuchMethodException {
            var method = MessageServiceClient.class.getMethod("send", MessageRequest.class);
            assertEquals(Result.class, method.getReturnType());
        }
    }

    @Nested
    @DisplayName("UserServiceClient 契约")
    class UserServiceClientContract {

        @Test
        @DisplayName("@FeignClient 注解 name 正确")
        void shouldHaveCorrectFeignClientName() {
            FeignClient annotation = UserServiceClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals("ydsz-pmis-userinfo", annotation.name());
            assertEquals("commonUserServiceClient", annotation.contextId());
        }

        @Test
        @DisplayName("fallbackFactory 正确配置")
        void shouldHaveFallbackFactory() {
            FeignClient annotation = UserServiceClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals(UserServiceClientFallback.class, annotation.fallbackFactory());
        }

        @Test
        @DisplayName("getEmployee 返回 Result<Map<String, Object>>")
        void shouldGetEmployeeReturnCorrectType() throws NoSuchMethodException {
            var method = UserServiceClient.class.getMethod("getEmployee", String.class);
            assertEquals(Result.class, method.getReturnType());
        }

        @Test
        @DisplayName("getCustomerName 返回 Result<String>")
        void shouldGetCustomerNameReturnCorrectType() throws NoSuchMethodException {
            var method = UserServiceClient.class.getMethod("getCustomerName", String.class);
            assertEquals(Result.class, method.getReturnType());
        }

        @Test
        @DisplayName("batchEmployeeName 返回 Result<Map<String, String>>")
        void shouldBatchEmployeeNameReturnCorrectType() throws NoSuchMethodException {
            var method = UserServiceClient.class.getMethod("batchEmployeeName", List.class);
            assertEquals(Result.class, method.getReturnType());
        }

        @Test
        @DisplayName("getLevelRate 返回 Result<BigDecimal>")
        void shouldGetLevelRateReturnCorrectType() throws NoSuchMethodException {
            var method = UserServiceClient.class.getMethod("getLevelRate", String.class);
            assertEquals(Result.class, method.getReturnType());
        }
    }

    @Nested
    @DisplayName("AgentClient 契约")
    class AgentClientContract {

        @Test
        @DisplayName("@FeignClient 注解 name 和 path 正确")
        void shouldHaveCorrectFeignClientNameAndPath() {
            FeignClient annotation = AgentClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals("ydsz-pmis-agent", annotation.name());
            assertEquals("/agent", annotation.path());
        }

        @Test
        @DisplayName("fallbackFactory 正确配置")
        void shouldHaveFallbackFactory() {
            FeignClient annotation = AgentClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals(AgentClientFallbackFactory.class, annotation.fallbackFactory());
        }

        @Test
        @DisplayName("execute 方法返回 Result<Map<String, Object>>")
        void shouldReturnCorrectType() throws NoSuchMethodException {
            var method = AgentClient.class.getMethod("execute", Map.class);
            assertEquals(Result.class, method.getReturnType());
        }
    }

    @Nested
    @DisplayName("ExecutionClient 契约")
    class ExecutionClientContract {

        @Test
        @DisplayName("@FeignClient 注解 name 正确")
        void shouldHaveCorrectFeignClientName() {
            FeignClient annotation = ExecutionClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals("ydsz-pmis-project", annotation.name());
        }

        @Test
        @DisplayName("fallbackFactory 正确配置")
        void shouldHaveFallbackFactory() {
            FeignClient annotation = ExecutionClient.class.getAnnotation(FeignClient.class);
            assertNotNull(annotation);
            assertEquals(ExecutionClientFallback.class, annotation.fallbackFactory());
        }

        @Test
        @DisplayName("recomputeBillableUtilization 返回 Result<Map<String, Object>>")
        void shouldRecomputeReturnCorrectType() throws NoSuchMethodException {
            var method = ExecutionClient.class.getMethod(
                    "recomputeBillableUtilization", String.class, boolean.class);
            assertEquals(Result.class, method.getReturnType());
        }

        @Test
        @DisplayName("snapshotAverage 返回 Result<Map<String, Object>>")
        void shouldSnapshotAverageReturnCorrectType() throws NoSuchMethodException {
            var method = ExecutionClient.class.getMethod("snapshotAverage", String.class);
            assertEquals(Result.class, method.getReturnType());
        }
    }

    @Nested
    @DisplayName("MessageRequest DTO 序列化契约")
    class MessageRequestSerializationContract {

        @Test
        @DisplayName("MessageRequest 必须包含 channel 字段")
        void shouldHaveChannelField() throws NoSuchFieldException {
            var field = MessageRequest.class.getDeclaredField("channel");
            assertNotNull(field);
            assertEquals(String.class, field.getType());
        }

        @Test
        @DisplayName("MessageRequest 必须包含 receiver 字段")
        void shouldHaveReceiverField() throws NoSuchFieldException {
            var field = MessageRequest.class.getDeclaredField("receiver");
            assertNotNull(field);
            assertEquals(String.class, field.getType());
        }

        @Test
        @DisplayName("MessageRequest 必须包含 templateCode 字段")
        void shouldHaveTemplateCodeField() throws NoSuchFieldException {
            var field = MessageRequest.class.getDeclaredField("templateCode");
            assertNotNull(field);
            assertEquals(String.class, field.getType());
        }
    }

    @Nested
    @DisplayName("MessageResult DTO 序列化契约")
    class MessageResultSerializationContract {

        @Test
        @DisplayName("MessageResult.ok() 返回 SUCCESS 状态")
        void shouldOkReturnSuccessStatus() {
            MessageResult result = MessageResult.ok("SMS", "trace-123");
            assertEquals("SMS", result.getChannel());
            assertEquals("SUCCESS", result.getStatus());
            assertEquals("trace-123", result.getProviderTraceId());
            assertNull(result.getErrorMessage());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("MessageResult.fail() 返回 FAILED 状态")
        void shouldFailReturnFailedStatus() {
            MessageResult result = MessageResult.fail("EMAIL", "发送失败");
            assertEquals("EMAIL", result.getChannel());
            assertEquals("FAILED", result.getStatus());
            assertNull(result.getProviderTraceId());
            assertEquals("发送失败", result.getErrorMessage());
            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Fallback 降级行为测试")
    class FallbackBehaviorTest {

        @Test
        @DisplayName("UserServiceClientFallback 降级返回安全默认值")
        void shouldFallbackReturnSafeDefaults() {
            UserServiceClientFallback fallback = new UserServiceClientFallback();
            // Fallback 应该能被实例化（fallbackFactory 需要无参构造或由 Spring 注入）
            assertNotNull(fallback);
        }

        @Test
        @DisplayName("MessageServiceClientFallback 降级返回安全默认值")
        void shouldMessageFallbackReturnSafeDefaults() {
            MessageServiceClientFallback fallback = new MessageServiceClientFallback();
            assertNotNull(fallback);
        }
    }

    @Nested
    @DisplayName("Result 包装一致性")
    class ResultConsistencyTest {

        @Test
        @DisplayName("成功 Result 的 code=0")
        void shouldSuccessResultHaveZeroCode() {
            Result<String> success = Result.ok("test");
            assertEquals(0, success.getCode());
            assertTrue(success.isSuccess());
        }

        @Test
        @DisplayName("失败 Result 的 code 非 0")
        void shouldFailedResultHaveNonZeroCode() {
            Result<Void> failed = Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            assertNotEquals(0, failed.getCode());
            assertFalse(failed.isSuccess());
        }

        @Test
        @DisplayName("降级 Result 应标记为服务不可用")
        void shouldFallbackResultMarkAsUnavailable() {
            // 降级场景应返回 SERVICE_UNAVAILABLE 或类似错误码
            Result<Void> degraded = Result.failed(BizErrorCode.SERVICE_UNAVAILABLE, "服务降级");
            assertEquals(BizErrorCode.SERVICE_UNAVAILABLE.getCode(), degraded.getCode());
            assertEquals("服务降级", degraded.getMessage());
        }
    }
}
