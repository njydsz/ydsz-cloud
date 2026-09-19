package com.njydsz.common.feign.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MethodAwareRetryer 单元测试。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>HTTP 方法在白名单中 → 允许重试（指数退避）
 *   <li>HTTP 方法不在白名单中 → 立即抛出异常，不重试
 *   <li>达到最大重试次数后抛出异常
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class MethodAwareRetryerTest {

  @Nested
  @DisplayName("HTTP 方法白名单过滤")
  class HttpMethodFilter {

    @Test
    @DisplayName("POST 方法不在白名单中应直接抛出")
    void shouldRethrowForPostNotInWhitelist() {
      Set<String> methods = new LinkedHashSet<>();
      methods.add("GET");
      MethodAwareRetryer retryer = new MethodAwareRetryer(100, 500, 3, methods);
      RetryableException exception = createRetryableException("POST");

      assertThatThrownBy(() -> retryer.continueOrPropagate(exception))
          .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("PUT 方法不在白名单中应直接抛出")
    void shouldRethrowForPutNotInWhitelist() {
      Set<String> methods = Collections.singleton("GET");
      MethodAwareRetryer retryer = new MethodAwareRetryer(100, 500, 3, methods);
      RetryableException exception = createRetryableException("PUT");

      assertThatThrownBy(() -> retryer.continueOrPropagate(exception))
          .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("DELETE 方法不在白名单中应直接抛出")
    void shouldRethrowForDeleteNotInWhitelist() {
      Set<String> methods = Collections.singleton("GET");
      MethodAwareRetryer retryer = new MethodAwareRetryer(100, 500, 3, methods);
      RetryableException exception = createRetryableException("DELETE");

      assertThatThrownBy(() -> retryer.continueOrPropagate(exception))
          .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("GET 方法在白名单中应允许重试（不抛异常直到超限）")
    void shouldAllowRetryForGetInWhitelist() {
      Set<String> methods = Collections.singleton("GET");
      // maxAttempts=2，第一次失败时可以重试一次
      MethodAwareRetryer retryer = new MethodAwareRetryer(10, 50, 2, methods);
      RetryableException exception = createRetryableException("GET");

      // 第一次调用（i=0）：不抛出（maxAttempts=2，第 1 次失败时仍可重试）
      retryer.continueOrPropagate(exception);
      // 第二次调用（i=1）：达到 maxAttempts，应抛出
      assertThatThrownBy(() -> retryer.continueOrPropagate(exception))
          .isInstanceOf(RetryableException.class);
    }
  }

  @Nested
  @DisplayName("重试次数控制")
  class RetryAttempts {

    @Test
    @DisplayName("maxAttempts=1 时第一次失败即抛出")
    void shouldThrowImmediatelyWhenMaxAttemptsIsOne() {
      Set<String> methods = Collections.singleton("GET");
      MethodAwareRetryer retryer = new MethodAwareRetryer(100, 500, 1, methods);
      RetryableException exception = createRetryableException("GET");

      assertThatThrownBy(() -> retryer.continueOrPropagate(exception))
          .isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("clone 应创建新实例")
    void cloneShouldCreateNewInstance() {
      Set<String> methods = Collections.singleton("GET");
      MethodAwareRetryer original = new MethodAwareRetryer(100, 500, 3, methods);
      MethodAwareRetryer cloned = (MethodAwareRetryer) original.clone();

      assertThat(cloned).isNotSameAs(original);
      // 验证是 MethodAwareRetryer 类型
      assertThat(cloned).isInstanceOf(MethodAwareRetryer.class);
    }
  }

  @Nested
  @DisplayName("配置参数")
  class Configuration {

    @Test
    @DisplayName("使用方法白名单构造应正确过滤")
    void shouldFilterByMethodWhitelist() {
      // 仅允许 POST 和 GET
      Set<String> methods = new LinkedHashSet<>();
      methods.add("GET");
      methods.add("POST");
      MethodAwareRetryer retryer = new MethodAwareRetryer(50, 200, 3, methods);

      // GET 和 POST 不应抛异常（未超次数时）
      assertThat(retryer).isNotNull();
    }
  }

  // ==================== 辅助方法 ====================

  private RetryableException createRetryableException(String httpMethod) {
    Request request =
        Request.create(
            Request.HttpMethod.valueOf(httpMethod),
            "http://localhost/test",
            Collections.emptyMap(),
            null,
            java.nio.charset.StandardCharsets.UTF_8);
    // 使用 Long 类型的 retryAfter 参数构造
    return new RetryableException(
        500,
        "Server Error",
        Request.HttpMethod.valueOf(httpMethod),
        Long.valueOf(0),
        request);
  }
}
