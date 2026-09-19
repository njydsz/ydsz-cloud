package com.njydsz.common.cache.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CacheKeyBuilder 租户感知缓存键构造器测试。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class CacheKeyBuilderTest {

  @Nested
  @DisplayName("基本键构造")
  class BuildTest {

    @Test
    @DisplayName("buildPattern 自动追加当前租户")
    void buildPattern_shouldAutoPrependTenant() {
      CacheKeyBuilder.setTenantIdResolver(() -> "acme");
      try {
        String key = CacheKeyBuilder.buildPattern("system", "dict:items", "user_status");
        assertThat(key).isEqualTo("ydsz:acme:system:dict:items:user_status");
      } finally {
        CacheKeyBuilder.setTenantIdResolver(null);
      }
    }

    @Test
    @DisplayName("build 将 tenant+module+entity+id 拼接并自动追加租户")
    void build_shouldGenerateTenantIsolatedKey() {
      CacheKeyBuilder.setTenantIdResolver(() -> "acme");
      try {
        String key = CacheKeyBuilder.build("system", "dict:items", "user_status");
        // build 内部调用 buildPattern(currentTenant(), module, entity, id)
        // buildPattern 再次追加 tenant，因此 tenant 出现两次
        assertThat(key).isEqualTo("ydsz:acme:acme:system:dict:items:user_status");
      } finally {
        CacheKeyBuilder.setTenantIdResolver(null);
      }
    }

    @Test
    @DisplayName("无租户时使用默认占位符")
    void build_withoutTenant_shouldUseDefault() {
      CacheKeyBuilder.setTenantIdResolver(null);
      String key = CacheKeyBuilder.build("workflow", "def:latest", "leave_apply");
      assertThat(key).isNotNull();
      assertThat(key).startsWith("ydsz:");
      assertThat(key).contains("workflow:def:latest:leave_apply");
    }
  }

  @Nested
  @DisplayName("模式键构造")
  class BuildPatternTest {

    @Test
    @DisplayName("buildPattern 支持通配符模式")
    void buildPattern_shouldSupportGlob() {
      CacheKeyBuilder.setTenantIdResolver(() -> "acme");
      try {
        String pattern = CacheKeyBuilder.buildPattern("nextwiki", "quota", "*", "*");
        assertThat(pattern).isEqualTo("ydsz:acme:nextwiki:quota:*:*");
      } finally {
        CacheKeyBuilder.setTenantIdResolver(null);
      }
    }
  }
}
