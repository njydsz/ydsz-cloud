package com.njydsz.common.search.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 引擎 Starter 配置器接口 — 用于独立引擎模块的自我注册。
 *
 * <p>当引擎模块（如 {@code ydsz-common-search-es}、{@code ydsz-common-search-redis}） 作为独立 Starter
 * 发布时，各模块只需实现本接口并在 {@code AutoConfiguration.imports} 中注册， 即能被核心模块的 {@link
 * SearchAutoConfiguration.ModularEngineConfiguration} 发现并装配。
 *
 * <p>实现类命名建议：{@code EsEngineStarterConfig}、{@code RediSearchStarterConfig}。
 *
 * <p>典型用法（以 ES 模块为例）：
 *
 * <pre>{@code
 * public class EsEngineStarterConfig implements EngineStarterConfigurer {
 *     @Override
 *     public ImportSelector[] getImportSelectors() {
 *         return new ImportSelector[]{ new EsEngineImportSelector() };
 *     }
 * }
 * }</pre>
 *
 * <p>核心模块仅保留 PG + Memory 引擎作为默认策略，ES/Redis/Solr/OpenSearch 等重型引擎 按需引入。这种拆分带来三个好处：
 *
 * <ul>
 *   <li>减少默认依赖体积（不含 heavyweight 客户端）
 *   <li>各引擎版本独立演进，避免依赖冲突
 *   <li>业务方只引入实际使用的引擎，避免 unused beans 占用内存
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SearchAutoConfiguration.ModularEngineConfiguration
 * @see org.springframework.context.annotation.ImportSelector
 */
public interface EngineStarterConfigurer {

  /**
   * 返回该引擎模块需要额外导入的配置选择器数组。
   *
   * <p>每个 {@link ImportSelector} 可以返回一组自动配置类的全限定名， Spring 容器会将它们纳入装配流程。通过这种方式， 引擎模块可以在不改动核心 {@code
   * SearchAutoConfiguration} 的前提下， 将自己的 {@code SearchStrategy} 实现注册到引擎注册表。
   *
   * <p>返回空数组表示本模块无需额外配置（极少见）。
   *
   * @return 导入选择器数组，永不为 {@code null}
   */
  ImportSelector[] getImportSelectors();

  /**
   * 引擎的自动装配优先级 — 数值越大优先级越高。
   *
   * <p>当多个引擎模块并存时，优先级高的引擎更先被注册到 {@link com.njydsz.common.search.core.SearchEngineRegistry}。 核心模块的
   * PG 引擎默认优先级为 0。
   *
   * @return 优先级，默认为 0
   */
  default int getOrder() {
    return 0;
  }

  /**
   * 引擎模块的唯一标识符，用于日志、健康检查与 JMX 暴露。
   *
   * <p>约定：使用引擎名称全小写，如 {@code "es"}、{@code "redis"}、{@code "solr"}。
   *
   * @return 引擎标识符，永不为 {@code null} 或空白
   */
  String getEngineId();

  /**
   * Spring 条件匹配的简易钩子 — 返回 {@code false} 时本模块的所有配置都被跳过。
   *
   * <p>典型实现：检查 classpath 是否存在引擎客户端类（如 {@code RestHighLevelClient}）， 或检查 {@code ydsz.search.primary}
   * 配置是否匹配。
   *
   * <p>默认返回 {@code true} 无条件装配。
   *
   * @param metadata 导入方的注解元数据
   * @return {@code true} 表示允许装配
   */
  default boolean shouldConfigure(AnnotationMetadata metadata) {
    return true;
  }
}
