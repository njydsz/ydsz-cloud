package com.njydsz.workflow.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.workflow.domain.gateway.NameServiceClient;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.server.health.FlowHealthIndicator;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowGroupResolver;
import com.njydsz.workflow.server.service.impl.integration.NameServiceClientAdapter;

/**
 * 工作流模块自动配置。
 *
 * <p>注册到 {@code AutoConfiguration.imports}，由 Spring Boot 自动装配机制加载。 可通过 {@code
 * ydsz.flow.enabled=false} 禁用整个工作流模块。
 *
 * <p>启用调度支持（SLA 扫描、自动催办等定时任务）。
 *
 * <p>P0-2: FlowHealthIndicator 和 FlowMetrics 从 @Component 改为在此处 @Bean 注册， 统一模块 Bean
 * 装配入口，与项目其他模块保持一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ydsz.flow",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableScheduling
@EnableConfigurationProperties(FlowProperties.class)
public class FlowAutoConfiguration {

  /** 工作流健康检查 Bean */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnProperty(
      prefix = "ydsz.flow",
      name = "health-enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean(FlowHealthIndicator.class)
  public FlowHealthIndicator flowHealthIndicator(
      FlowInstanceRepository instanceRepository,
      FlowRunTaskRepository runTaskRepository,
      ObjectProvider<RedisStringOps> redisServiceProvider) {
    return new FlowHealthIndicator(instanceRepository, runTaskRepository, redisServiceProvider);
  }

  /**
   * 工作流 Prometheus 指标收集器 Bean
   *
   * @param instanceRepository 流程实例仓储接口
   * @param taskRepository 运行时任务仓储接口
   * @return 工作流 Prometheus 指标收集器
   */
  @Bean
  @ConditionalOnMissingBean(FlowMetrics.class)
  public FlowMetrics flowMetrics(
      FlowInstanceRepository instanceRepository,
      FlowRunTaskRepository taskRepository) {
    return new FlowMetrics(instanceRepository, taskRepository);
  }

  /**
   * P2-2: 分组办理人解析器默认实现 Bean。
   *
   * <p>将分组编码直接作为单个办理人 ID 返回（降级兼容）。业务系统实现自定义 {@link FlowGroupResolver} 并注册为 Bean
   * 即可覆盖本默认实现，接入自身的用户分组/团队服务。
   *
   * @return 默认分组解析器
   */
  @Bean
  @ConditionalOnMissingBean(FlowGroupResolver.class)
  public FlowGroupResolver flowGroupResolver() {
    return new FlowGroupResolver.DefaultFlowGroupResolver();
  }

  /**
   * GAP-A2: 名称查询网关适配器 Bean。
   *
   * <p>修复此前 domain 层 {@link NameServiceClient} 网关接口全仓无实现、
   * {@code FlowUserCacheService} 构造注入无 Bean 导致的应用上下文启动失败风险。
   *
   * <p>委托 common-feign 的 {@link NameAssembler}（ID → 名称富化组件，带缓存）；
   * 业务系统可注册自定义 {@code NameServiceClient} Bean 覆盖本默认实现
   * （如直连 userinfo Feign 客户端）。
   *
   * @param nameAssemblerProvider 名称富化组件提供器（显式禁用平台兜底时返回 null）
   * @return 名称查询网关适配器
   */
  @Bean
  @ConditionalOnMissingBean(NameServiceClient.class)
  public NameServiceClient nameServiceClient(ObjectProvider<NameAssembler> nameAssemblerProvider) {
    // 平台兜底 NoOpNameAssembler 缺省必在；显式禁用 ydsz.feign.name-assembler 时降级为空适配器，保证可启动
    return new NameServiceClientAdapter(nameAssemblerProvider.getIfAvailable());
  }

  // P0-1: flowQueueExecutor 线程池已迁移到 ydsz-common-thread 统一管理
  // 配置项: ydsz.thread.pools.flowQueue.* (见 application.yml)
  // Bean 名称: flowQueueExecutor（key + "Executor"）
}
