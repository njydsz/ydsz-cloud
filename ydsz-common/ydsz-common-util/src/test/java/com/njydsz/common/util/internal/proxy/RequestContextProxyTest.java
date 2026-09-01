package com.njydsz.common.util.internal.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * RequestContextProxy 降级路径测试。
 *
 * <p>测试 classpath 不含 ydsz-common-core（L1 工具层禁止反向依赖 L2），
 * 因此本测试验证降级契约：core 缺失时所有读写操作返回安全默认值且不抛异常。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class RequestContextProxyTest {

  @Test
  @DisplayName("core 缺失：isAvailable 返回 false")
  void notAvailableWithoutCore() {
    assertThat(RequestContextProxy.isAvailable()).isFalse();
  }

  @Test
  @DisplayName("降级读取：get/getTraceId/getRequestId 均返回 null")
  void readsReturnNullInFallback() {
    assertThat(RequestContextProxy.get("any-key")).isNull();
    assertThat(RequestContextProxy.getTraceId()).isNull();
    assertThat(RequestContextProxy.getRequestId()).isNull();
  }

  @Test
  @DisplayName("降级写入：setTraceId/remove 为无操作且不抛异常")
  void writesAreNoOpInFallback() {
    assertThatCode(
            () -> {
              RequestContextProxy.setTraceId("should-be-ignored");
              RequestContextProxy.remove("any-key");
            })
        .doesNotThrowAnyException();
    assertThat(RequestContextProxy.getTraceId()).as("降级写入不应产生可读状态").isNull();
  }

  @Test
  @DisplayName("core 缺失时 verifyBinding 返回 true（正常独立使用，不告警）")
  void verifyBindingPassesWhenCoreAbsent() {
    assertThat(RequestContextProxy.verifyBinding()).isTrue();
  }

  @Test
  @DisplayName("clearCache 可安全重复调用（状态重置幂等）")
  void clearCacheIsIdempotent() {
    RequestContextProxy.clearCache();
    RequestContextProxy.clearCache();

    assertThat(RequestContextProxy.isAvailable()).isFalse();
  }
}
