package com.njydsz.literule.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.literule.domain.repository.RuleDefinitionConfigRepository;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;
import com.njydsz.literule.infra.repository.impl.RuleDefinitionConfigRepositoryImpl;

/**
 * Literule 基础设施层自动配置。
 *
 * <p>注册 MyBatis Mapper 扫描路径，使 {@code infra.mapper} 包下的 Mapper 接口被 Spring 容器扫描并注册为 Mapper Bean。
 *
 * <p>此配置由 Spring Boot 自动装配机制通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 发现和加载，避免 web/app 入口模块直接依赖 infra 包的物理结构，符合 DDD 分层依赖方向（web → server → domain ← infra）。
 *
 * <p><b>条件装配：</b>
 *
 * <ul>
 *   <li>{@code @ConditionalOnClass}：仅当 MyBatis-Plus 与数据源驱动均在 classpath 上时才激活，避免纯内存模式（如单元测试）加载时因缺少驱动而报错
 *   <li>{@code @ConditionalOnProperty}：通过 {@code ydsz.literule.mybatis.enabled} 开关控制，默认开启；
 *       需要禁用时可配置 {@code ydsz.literule.mybatis.enabled=false}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@AutoConfiguration
@MapperScan("com.njydsz.literule.infra.mapper")
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.MybatisConfiguration")
@ConditionalOnProperty(prefix = "ydsz.literule.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiteruleInfraAutoConfiguration {

  /**
   * 规则定义「引擎配置源」端口实现（domain 端口 {@link RuleDefinitionConfigRepository} 的 infra 实现）。
   *
   * <p><b>为何用 {@code @Bean} 而不是 {@code @Repository} + 组件扫描：</b>
   * server 层 {@code LiteRuleAutoConfiguration#directDbRuleConfigProvider} 以
   * {@code @ConditionalOnBean(RuleDefinitionConfigRepository.class)} 判定 DB 规则源是否装配，
   * 判定失败时静默回落到 NO-OP（空规则）。组件扫描属于宿主应用，嵌入方不扫 {@code com.njydsz.literule} 时
   * 就会出现"Mapper 已就绪、端口缺失"的组合，规则数据源无声消失。
   * 本 Bean 随本类一同激活（条件与本类 {@code @ConditionalOnClass}/{@code @ConditionalOnProperty} 一致），
   * 端口与 Mapper 同生同灭，判定结果与改造前一致。
   *
   * <p><b>不加 {@code @ConditionalOnBean(RuleDefinitionMapper.class)}：</b>Mapper 定义由本类
   * {@code @MapperScan} 在配置解析阶段注册，与方法条件求值的先后顺序不受本模块控制；
   * 条件误判会静默丢掉端口。改为直接以构造参数注入 Mapper——缺失时容器启动即报错（fail-fast），
   * 而不是带着空规则源对外服务。
   *
   * @param ruleDefinitionMapper 规则定义 Mapper（由本类 {@code @MapperScan} 注册）
   * @return 规则定义配置源端口实现
   * @since 26.10.27
   */
  @Bean
  @ConditionalOnMissingBean(RuleDefinitionConfigRepository.class)
  public RuleDefinitionConfigRepository ruleDefinitionConfigRepository(
      RuleDefinitionMapper ruleDefinitionMapper) {
    log.info("[LiteRule-Infra] 规则定义配置源端口 RuleDefinitionConfigRepository 已注册");
    return new RuleDefinitionConfigRepositoryImpl(ruleDefinitionMapper);
  }
}
