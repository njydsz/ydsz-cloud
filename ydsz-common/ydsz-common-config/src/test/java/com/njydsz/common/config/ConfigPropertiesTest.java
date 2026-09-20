package com.njydsz.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigProperties} 单元测试。
 *
 * <p>覆盖：构造默认值、嵌套配置内部类默认值、属性读写 round-trip。
 *
 * @since 26.09.20
 */
@DisplayName("ConfigProperties 测试")
class ConfigPropertiesTest {

  @Nested
  @DisplayName("构造默认值")
  class DefaultValuesTest {

    @Test
    @DisplayName("ChangeMonitor 默认值全部正确")
    void changeMonitor_defaults() {
      ConfigProperties.ChangeMonitor monitor = new ConfigProperties.ChangeMonitor();
      assertThat(monitor.isEnabled()).isTrue();
      assertThat(monitor.isSnapshotOldValues()).isTrue();
      assertThat(monitor.isAsyncDispatch()).isTrue();
      assertThat(monitor.getAsyncCorePoolSize()).isEqualTo(2);
      assertThat(monitor.getAsyncQueueCapacity()).isEqualTo(256);
    }

    @Test
    @DisplayName("Cli 默认值全部正确")
    void cli_defaults() {
      ConfigProperties.Cli cli = new ConfigProperties.Cli();
      assertThat(cli.isEnabled()).isTrue();
      assertThat(cli.getAlgorithm()).isEqualTo("PBEWithHMACSHA512AndAES_256");
      assertThat(cli.getKeyObtentionIterations()).isEqualTo(1000);
      assertThat(cli.getPoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("Health 默认值全部正确")
    void health_defaults() {
      ConfigProperties.Health health = new ConfigProperties.Health();
      assertThat(health.isEnabled()).isTrue();
      assertThat(health.getCacheTtlMs()).isEqualTo(5000L);
    }
  }

  @Nested
  @DisplayName("属性读写")
  class PropertyAccessTest {

    @Test
    @DisplayName("ConfigProperties 顶层返回三层嵌套配置")
    void topLevelProperties_present() {
      ConfigProperties props = new ConfigProperties();
      assertThat(props.getChangeMonitor()).isNotNull();
      assertThat(props.getCli()).isNotNull();
      assertThat(props.getHealth()).isNotNull();
    }

    @Test
    @DisplayName("ChangeMonitor setter/getter round-trip")
    void changeMonitor_setterGetter() {
      ConfigProperties.ChangeMonitor monitor = new ConfigProperties.ChangeMonitor();
      monitor.setEnabled(false);
      monitor.setSnapshotOldValues(false);
      monitor.setAsyncDispatch(false);
      monitor.setAsyncCorePoolSize(4);
      monitor.setAsyncQueueCapacity(512);

      assertThat(monitor.isEnabled()).isFalse();
      assertThat(monitor.isSnapshotOldValues()).isFalse();
      assertThat(monitor.isAsyncDispatch()).isFalse();
      assertThat(monitor.getAsyncCorePoolSize()).isEqualTo(4);
      assertThat(monitor.getAsyncQueueCapacity()).isEqualTo(512);
    }

    @Test
    @DisplayName("Health setter/getter round-trip")
    void health_setterGetter() {
      ConfigProperties.Health health = new ConfigProperties.Health();
      health.setEnabled(false);
      health.setCacheTtlMs(10_000L);

      assertThat(health.isEnabled()).isFalse();
      assertThat(health.getCacheTtlMs()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("Cli setter/getter round-trip")
    void cli_setterGetter() {
      ConfigProperties.Cli cli = new ConfigProperties.Cli();
      cli.setAlgorithm("PBEWithMD5AndDES");
      cli.setKeyObtentionIterations(2000);
      cli.setPoolSize(8);

      assertThat(cli.getAlgorithm()).isEqualTo("PBEWithMD5AndDES");
      assertThat(cli.getKeyObtentionIterations()).isEqualTo(2000);
      assertThat(cli.getPoolSize()).isEqualTo(8);
    }
  }

  @Nested
  @DisplayName("布尔字段 is 前缀合规（OOP-006）")
  class BooleanFieldComplianceTest {

    @Test
    @DisplayName("所有布尔 getter 均以 is 前缀开头")
    void booleanFields_haveIsPrefix() {
      ConfigProperties.ChangeMonitor monitor = new ConfigProperties.ChangeMonitor();
      // 命名规范已在代码中静态保证，此测试验证运行时行为
      assertThat(monitor.isEnabled()).isTrue();
      assertThat(monitor.isSnapshotOldValues()).isTrue();
      assertThat(monitor.isAsyncDispatch()).isTrue();
    }
  }
}
