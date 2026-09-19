package com.njydsz.common.feign.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
    @DisplayName("GET 方法在白名单中应允许重试")
    void shouldAllowRetryForGet() {
      Set<String> methods = new LinkedHashSet<>();
      methods.add("GET");
      MethodAwareRetryer retryer = new MethodAwareRetryer(100, 500, 3, methods);
      RetryableException exception = createRetryableException("GET");

      AtomicInteger attempts = new AtomicInteger(0);

      // 重试器会在 continueOrPropagate 中执行重试逻辑
      // 当 maxAttempts=3 时，前 2 次调用不抛出，第 3 次抛出
      for (int i = 0; i < 2; i++) {
        retryer.clone().continueOrPropagate(exception);
        attempts.incrementAndGet();
      }

      // 第三次应抛出异常
      MethodAwareRetryer finalRetryer = retryer.clone();
      assertThatThrownBy(() -> finalRetryer.continueOrPropagate(exception))
          .isInstanceOf(RetryableException.class);
    }

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
    @DisplayName("clone 应创建独立实例支持并发")
    void cloneShouldCreateIndependentInstance() {
      Set<String> methods = Collections.singleton("GET");
      MethodAwareRetryer original = new MethodAwareRetryer(100, 500, 3, methods);
      MethodAwareRetryer cloned = original.clone();

      assertThat(cloned).isNotSameAs(original);
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
    return new RetryableException(
        500,
        "Server Error",
        Request.HttpMethod.valueOf(httpMethod),
        null,
        request);
  }
}
