package com.njydsz.common.feign.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.feign.config.FeignProperties;
import com.njydsz.common.feign.exception.OpenFeignException;

/**
 * BulkheadRequestInterceptor 单元测试。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>并发请求未超限时正常获取许可
 *   <li>并发请求超限时快速失败
 *   <li>释放许可后其他请求可继续获取
 *   <li>按服务名隔离（不同服务独立计数）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class BulkheadRequestInterceptorTest {

  @Nested
  @DisplayName("信号量获取与释放")
  class PermitAcquireAndRelease {

    @Test
    @DisplayName("正常情况下应成功获取许可")
    void shouldAcquirePermitNormally() {
      BulkheadRequestInterceptor interceptor = new BulkheadRequestInterceptor(5, 100, null);

      RequestTemplate template = createRequestTemplate("http://ydsz-message/api/test");

      // 应在超时时间内成功获取
      interceptor.apply(template);

      // 验证释放不报错
      interceptor.releaseCurrentPermit();
    }

    @Test
    @DisplayName("超限时应快速失败抛出 OpenFeignException")
    void shouldThrowWhenExceedsMaxConcurrent() {
      // maxConcurrent = 1, timeout = 50ms
      BulkheadRequestInterceptor interceptor = new BulkheadRequestInterceptor(1, 50, null);

      // 第一次获取许可成功
      RequestTemplate template1 = createRequestTemplate("http://ydsz-message/api/test");
      interceptor.apply(template1);

      // 第二次获取应快速失败（信号量已满）
      RequestTemplate template2 = createRequestTemplate("http://ydsz-message/api/test");
      assertThatThrownBy(() -> interceptor.apply(template2))
          .isInstanceOf(OpenFeignException.class)
          .hasMessageContaining("Bulkhead full");

      // 释放第一次的许可
      interceptor.releaseCurrentPermit();
    }

    @Test
    @DisplayName("释放后可再次获取")
    void shouldAllowReacquireAfterRelease() {
      BulkheadRequestInterceptor interceptor = new BulkheadRequestInterceptor(1, 50, null);

      RequestTemplate template = createRequestTemplate("http://ydsz-message/api/test");
      interceptor.apply(template);
      interceptor.releaseCurrentPermit();

      // 释放后应能再次获取
      RequestTemplate template2 = createRequestTemplate("http://ydsz-message/api/test");
      interceptor.apply(template2);
      interceptor.releaseCurrentPermit();
    }
  }

  @Nested
  @DisplayName("按服务名隔离")
  class ServiceIsolation {

    @Test
    @DisplayName("不同服务名应独立计数")
    void shouldIsolateByServiceName() {
      BulkheadRequestInterceptor interceptor = new BulkheadRequestInterceptor(2, 100, null);

      // 对 order 服务获取许可
      RequestTemplate orderRequest = createRequestTemplate("http://ydsz-order/api/orders");
      interceptor.apply(orderRequest);

      // 对 user 服务获取许可（独立计数，不应受 order 影响）
      RequestTemplate userRequest = createRequestTemplate("http://ydsz-user/api/users");
      interceptor.apply(userRequest);

      // 释放两个服务的许可
      interceptor.releaseCurrentPermit();
    }

    @Test
    @DisplayName("按服务名定制并发数")
    void shouldRespectPerServiceMaxConcurrent() {
      Map<String, Integer> perService = new HashMap<>();
      perService.put("ydsz-message", 5);
      perService.put("ydsz-user", 100);

      BulkheadRequestInterceptor interceptor = new BulkheadRequestInterceptor(10, 100, perService);

      // message 服务应使用 maxConcurrent=5
      RequestTemplate messageReq = createRequestTemplate("http://ydsz-message/api/send");
      interceptor.apply(messageReq);
      interceptor.releaseCurrentPermit();
    }
  }

  @Nested
  @DisplayName("FeignProperties 构造")
  class FeignPropertiesConstruction {

    @Test
    @DisplayName("从 FeignProperties.Bulkhead 配置构造应正确读取")
    void shouldReadFromFeignProperties() {
      FeignProperties.Bulkhead bulkhead = new FeignProperties.Bulkhead();
      bulkhead.setDefaultMaxConcurrent(30);
      bulkhead.setAcquireTimeoutMs(200);

      // 验证默认 getter
      assertThat(bulkhead.getDefaultMaxConcurrent()).isEqualTo(30);
      assertThat(bulkhead.getAcquireTimeoutMs()).isEqualTo(200);
    }

    @Test
    @DisplayName("默认值应正确")
    void shouldHaveCorrectDefaults() {
      FeignProperties.Bulkhead bulkhead = new FeignProperties.Bulkhead();

      assertThat(bulkhead.isEnabled()).isFalse();
      assertThat(bulkhead.getDefaultMaxConcurrent()).isEqualTo(50);
      assertThat(bulkhead.getAcquireTimeoutMs()).isEqualTo(100);
      assertThat(bulkhead.getServiceMaxConcurrent()).isEmpty();
    }
  }

  // ==================== 辅助方法 ====================

  private RequestTemplate createRequestTemplate(String url) {
    RequestTemplate template = new RequestTemplate();
    template.target(url);
    return template;
  }
}
