package com.njydsz.common.config.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * {@link ConfigEncryptHealthIndicator} 单元测试。
 *
 * <p>覆盖：无加密属性返回 UP、主密码来源识别、存在 ENC() 但无主密码返回 DOWN、
 * 缓存命中与驱逐、边界值处理（非 String 类型、部分匹配 ENC）。
 *
 * @since 26.09.20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigEncryptHealthIndicator 测试")
class ConfigEncryptHealthIndicatorTest {

  @Mock
  private ConfigurableEnvironment environment;

  private ConfigEncryptHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    indicator = new ConfigEncryptHealthIndicator(environment, 0);
  }

  @Nested
  @DisplayName("无加密属性场景")
  class NoEncryptedPropertiesTest {

    @Test
    @DisplayName("空属性源：UP + encryptedPropertyCount=0")
    void noEncryptedProperties_returnsUp() {
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("empty", new HashMap<String, Object>()));
      when(environment.getPropertySources()).thenReturn(sources);

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("UP").getStatus());
    }

    @Test
    @DisplayName("存在非加密属性：UP + encryptedPropertyCount=0")
    void plainPropertiesOnly_returnsUp() {
      Map<String, Object> props = new HashMap<>();
      props.put("spring.datasource.url", "jdbc:mysql://localhost:3306/test");
      props.put("server.port", 8080);
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("UP").getStatus());
    }
  }

  @Nested
  @DisplayName("加密属性存在场景")
  class EncryptedPropertiesPresentTest {

    @Test
    @DisplayName("存在 ENC() 属性但主密码未配置 → DOWN")
    void encryptedPropsWithoutMasterPassword_returnsDown() {
      Map<String, Object> props = new HashMap<>();
      props.put("spring.datasource.password", "ENC(encrypted-value-here)");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("DOWN").getStatus());
    }

    @Test
    @DisplayName("存在 ENC() 属性且主密码已配置（CONFIG_PROPERTY）→ UP")
    void encryptedPropsWithConfigMasterPassword_returnsUp() {
      Map<String, Object> props = new HashMap<>();
      props.put("spring.datasource.password", "ENC(encrypted-value-here)");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);
      when(environment.getProperty("jasypt.encryptor.password"))
          .thenReturn("my-master-password");

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("UP").getStatus());
    }
  }

  @Nested
  @DisplayName("details 信息")
  class DetailsTest {

    @Test
    @DisplayName("UP 无加密属性时包含 encryptedPropertyCount=0")
    void up_noEncrypted_includesCountZero() {
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("empty", new HashMap<String, Object>()));
      when(environment.getPropertySources()).thenReturn(sources);

      Health health = indicator.health();
      assertThat(health.getDetails()).containsKey("encryptedPropertyCount");
      assertThat(health.getDetails().get("encryptedPropertyCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("UP 有加密属性时包含 encryptorPasswordSource")
    void up_withEncrypted_includesPasswordSource() {
      Map<String, Object> props = new HashMap<>();
      props.put("spring.datasource.password", "ENC(xyz)");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);
      when(environment.getProperty("jasypt.encryptor.password"))
          .thenReturn("configured-password");

      Health health = indicator.health();
      assertThat(health.getDetails()).containsKey("encryptorPasswordSource");
    }
  }

  @Nested
  @DisplayName("健康检查缓存")
  class CacheTest {

    @Test
    @DisplayName("TTL > 0 时第二次调用不重新扫描（结果来自缓存）")
    void cache_ttlPositive_returnsCachedResult() {
      ConfigEncryptHealthIndicator cachedIndicator =
          new ConfigEncryptHealthIndicator(environment, 60_000);

      Map<String, Object> props = new HashMap<>();
      props.put("some.value", "plain");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);

      Health first = cachedIndicator.health();
      assertThat(first.getStatus()).isEqualTo(Health.status("UP").getStatus());

      cachedIndicator.evictCache();
      Health second = cachedIndicator.health();
      assertThat(second.getStatus()).isEqualTo(Health.status("UP").getStatus());
    }
  }

  @Nested
  @DisplayName("边界条件")
  class EdgeCaseTest {

    @Test
    @DisplayName("值为非 String 类型时不计入加密属性统计")
    void nonStringValues_notCountedAsEncrypted() {
      Map<String, Object> props = new HashMap<>();
      props.put("server.port", 8080);
      props.put("feature.enabled", true);
      props.put("name", "plain-text");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("UP").getStatus());
      assertThat(health.getDetails().get("encryptedPropertyCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("ENC() 部分匹配前缀或后缀不识别为加密")
    void enc_partialMatch_notRecognized() {
      Map<String, Object> props = new HashMap<>();
      props.put("malformed1", "ENC(no-closing");
      props.put("malformed2", "no-opening)");
      props.put("malformed3", "ENC(incomplete");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("UP").getStatus());
    }

    @Test
    @DisplayName("多个 ENC() 属性：encryptedPropertyCount 正确计数")
    void multipleEncProperties_countAggregated() {
      Map<String, Object> props = new HashMap<>();
      props.put("db.password", "ENC(secret1)");
      props.put("redis.password", "ENC(secret2)");
      props.put("api.key", "ENC(secret3)");
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test", props));
      when(environment.getPropertySources()).thenReturn(sources);
      when(environment.getProperty("jasypt.encryptor.password"))
          .thenReturn("master-pass");

      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Health.status("UP").getStatus());
      assertThat(health.getDetails().get("encryptedPropertyCount")).isEqualTo(3);
    }
  }
}
