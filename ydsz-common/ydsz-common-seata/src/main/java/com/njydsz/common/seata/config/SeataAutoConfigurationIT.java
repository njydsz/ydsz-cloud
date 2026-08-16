package com.njydsz.common.seata.config;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.njydsz.common.seata.api.DistributedTransactionManager;
import com.njydsz.common.seata.api.TransactionType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeataAutoConfiguration 集成测试
 *
 * <p>验证自动配置类在以下场景正确装配：
 *
 * <ul>
 *   <li>ydsz.seata.enabled=true 时注册 DistributedTransactionManager
 *   <li>ydsz.seata.default-type=LOCAL 时使用 LocalTransactionManager
 *   <li>配置属性正确绑定到 SeataProperties
 * </ul>
 *
 * <p><b>注意</b>：当前暂时放置于 src/main/java，待 src/test/java 目录创建后需移至标准位置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SeataAutoConfigurationIT {

  private SeataAutoConfigurationIT() {
    // 工具类，禁止实例化
  }

  /** 运行所有 AutoConfiguration 集成测试 */
  public static void runAllTests() {
    verifySeataEnabled();
    verifySeataDisabled();
    verifyPropertiesBinding();
    verifyDefaultEnabled();
    System.out.println("所有 SeataAutoConfiguration 集成测试验证通过！");
  }

  /** 验证启用 seata 时 DistributedTransactionManager 被注册 */
  static void verifySeataEnabled() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeataAutoConfiguration.class));

    contextRunner
        .withPropertyValues("ydsz.seata.enabled=true", "ydsz.seata.default-type=LOCAL")
        .run(
            context -> {
              assertThat(context).hasSingleBean(DistributedTransactionManager.class);
              assertThat(context).hasSingleBean(SeataProperties.class);
              System.out.println("[PASS] seataEnabled_distributedTransactionManagerBeanRegistered");
            });
  }

  /** 验证 seata 禁用时不注册任何 seata 相关 Bean */
  static void verifySeataDisabled() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeataAutoConfiguration.class));

    contextRunner
        .withPropertyValues("ydsz.seata.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(DistributedTransactionManager.class);
              System.out.println("[PASS] seataDisabled_noBeansRegistered");
            });
  }

  /** 验证 SeataProperties 配置属性正确绑定 */
  static void verifyPropertiesBinding() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeataAutoConfiguration.class));

    contextRunner
        .withPropertyValues(
            "ydsz.seata.enabled=true",
            "ydsz.seata.default-type=TCC",
            "ydsz.seata.tcc-enabled=true",
            "ydsz.seata.tcc-log-store=memory",
            "ydsz.seata.saga-max-retries=3")
        .run(
            context -> {
              SeataProperties props = context.getBean(SeataProperties.class);
              assertThat(props.isEnabled()).isTrue();
              assertThat(props.getDefaultType()).isEqualTo(TransactionType.TCC);
              assertThat(props.isTccEnabled()).isTrue();
              assertThat(props.getTccLogStore()).isEqualTo("memory");
              assertThat(props.getSagaMaxRetries()).isEqualTo(3);
              System.out.println("[PASS] seataProperties_binding");
            });
  }

  /** 验证默认配置（不显式设置 enabled 时默认为 true） */
  static void verifyDefaultEnabled() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeataAutoConfiguration.class));

    contextRunner
        .withPropertyValues("ydsz.seata.default-type=SAGA")
        .run(
            context -> {
              DistributedTransactionManager manager =
                  context.getBean(DistributedTransactionManager.class);
              assertThat(manager).isNotNull();
              assertThat(manager.getCurrentType()).isEqualTo(TransactionType.SAGA);
              System.out.println("[PASS] defaultEnabled_distributedTransactionManagerAvailable");
            });
  }
}
