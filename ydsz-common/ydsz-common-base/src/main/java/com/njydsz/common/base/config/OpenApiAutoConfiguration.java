package com.njydsz.common.base.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OpenAPI 多分组自动配置类
 *
 * <p>当 classpath 中存在 springdoc-openapi 时自动激活，提供 OpenAPI 3.0 规范支持。 支持单分组模式和多分组模式：
 *
 * <ul>
 *   <li><b>单分组模式：</b>未配置 {@code ydsz.doc.groups} 时，使用全局配置生成默认分组（匹配全部路径）
 *   <li><b>多分组模式：</b>配置了 {@code ydsz.doc.groups} 时，为每个 group 创建独立的 {@link GroupedOpenApi} Bean
 * </ul>
 *
 * <p><b>与 {@link com.njydsz.common.base.config.BaseOpenApiConfiguration} 的关系：</b> 当业务模块通过继承 {@link
 * com.njydsz.common.base.config.BaseOpenApiConfiguration} 提供了自定义 {@link OpenAPI} Bean 时， 本配置类的
 * {@link #openAPI()} 方法将自动退出（{@code @ConditionalOnMissingBean}）， 仅保留 {@link #groupedOpenApis()}
 * 分组能力。
 *
 * <p><b>线程安全性：</b>无状态配置类，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(DocProperties.class)
@ConditionalOnClass(name = "org.springdoc.core.configuration.SpringDocConfiguration")
@ConditionalOnProperty(
    prefix = "ydsz.doc",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class OpenApiAutoConfiguration {

  /** 文档模块配置属性，由 Spring 注入 */
  private final DocProperties docProperties;

  /**
   * 构建全局 OpenAPI 信息
   *
   * <p>将 {@link DocProperties#getInfo()} 中配置的标题、描述、版本、联系方式、许可证 转换为 OpenAPI 3.0 规范的 {@link OpenAPI}
   * Bean。
   *
   * <p>版本号解析优先级：{@code ydsz.doc.doc-version} &gt; {@link DocProperties.OpenApiInfo#getVersion()}。
   *
   * <p>当项目中已存在自定义 {@link OpenAPI} Bean（如通过继承 {@link
   * com.njydsz.common.base.config.BaseOpenApiConfiguration}）时，本 Bean 自动退出，避免冲突。
   *
   * @return OpenAPI 实例，包含文档标题、描述、版本、联系方式和许可证等信息
   */
  @Bean
  @ConditionalOnMissingBean(OpenAPI.class)
  public OpenAPI openAPI() {
    DocProperties.OpenApiInfo info = docProperties.getInfo();

    Contact contact =
        new Contact()
            .name(info.getContact().getName())
            .email(info.getContact().getEmail())
            .url(info.getContact().getUrl());

    License license =
        new License().name(info.getLicense().getName()).url(info.getLicense().getUrl());

    String version =
        docProperties.getDocVersion() != null ? docProperties.getDocVersion() : info.getVersion();

    return new OpenAPI()
        .info(
            new Info()
                .title(info.getTitle())
                .description(info.getDescription())
                .version(version)
                .termsOfService(info.getTermsOfService())
                .contact(contact)
                .license(license));
  }

  /**
   * 根据分组配置动态创建 GroupedOpenApi Bean 列表
   *
   * <p>根据 {@link DocProperties#getGroups()} 决定分组模式：
   *
   * <ul>
   *   <li>未配置或为空：创建默认分组（单分组模式）
   *   <li>非空：为每个 group 创建独立的分组（多分组模式）
   * </ul>
   *
   * @return GroupedOpenApi 列表，包含所有创建的分组 API
   */
  @Bean
  @ConditionalOnMissingBean(name = "groupedOpenApis")
  public List<GroupedOpenApi> groupedOpenApis() {
    List<GroupedOpenApi> apis = new ArrayList<>();
    List<DocProperties.GroupConfig> groups = docProperties.getGroups();

    if (groups == null || groups.isEmpty()) {
      // 单分组模式：创建默认分组
      apis.add(createDefaultGroup());
    } else {
      // 多分组模式：为每个 group 创建独立的分组
      for (DocProperties.GroupConfig group : groups) {
        apis.add(createGroupApi(group));
      }
    }

    logGroupConfigInfo(apis);
    return apis;
  }

  /**
   * 创建默认分组（单分组模式）
   *
   * <p>默认分组名为 {@code default}，{@code displayName} 使用 {@link DocProperties.OpenApiInfo#getTitle()}，
   * 匹配所有路径（{@code /**}）。
   *
   * @return 默认的 GroupedOpenApi 实例，匹配所有路径
   */
  private GroupedOpenApi createDefaultGroup() {
    DocProperties.OpenApiInfo info = docProperties.getInfo();

    return GroupedOpenApi.builder()
        .group("default")
        .displayName(info.getTitle())
        .pathsToMatch("/**")
        .build();
  }

  /**
   * 根据分组配置创建 GroupedOpenApi
   *
   * <p>支持两种分组方式，按以下优先级选择：
   *
   * <ol>
   *   <li>按包扫描：{@code packages}（列表） &gt; {@code basePackage}（兼容旧版）
   *   <li>按路径模式匹配：{@code paths}（列表） &gt; {@code basePath}（兼容旧版）
   * </ol>
   *
   * <p>排除路径：{@code excludePaths} 非空时生效。
   *
   * @param group 分组配置信息
   * @return 根据配置构建的 GroupedOpenApi 实例
   */
  private GroupedOpenApi createGroupApi(DocProperties.GroupConfig group) {
    GroupedOpenApi.Builder builder =
        GroupedOpenApi.builder()
            .group(group.getName())
            .displayName(group.getTitle() != null ? group.getTitle() : group.getName());

    // 优先使用 packages 列表
    if (group.getPackages() != null && !group.getPackages().isEmpty()) {
      builder.packagesToScan(group.getPackages().toArray(new String[0]));
    }
    // 使用 paths 列表
    else if (group.getPaths() != null && !group.getPaths().isEmpty()) {
      builder.pathsToMatch(group.getPaths().toArray(new String[0]));
    }
    // 兼容旧版 basePath
    else {
      builder.pathsToMatch(group.getBasePath());
    }

    // 配置排除路径
    if (group.getExcludePaths() != null && !group.getExcludePaths().isEmpty()) {
      builder.pathsToExclude(group.getExcludePaths().toArray(new String[0]));
    }

    return builder.build();
  }

  /**
   * 输出分组配置信息到日志
   *
   * @param apis 已创建的 GroupedOpenApi 列表
   */
  private void logGroupConfigInfo(List<GroupedOpenApi> apis) {
    log.info("========================================");
    log.info("OpenAPI 文档已启用");
    DocProperties.OpenApiInfo info = docProperties.getInfo();
    log.info("  - 文档标题: {}", info.getTitle());
    log.info("  - 文档描述: {}", info.getDescription());
    log.info("  - API 路径: {}", docProperties.getApiDocsPath());
    log.info("  - 分组数量: {}", apis.size());
    for (GroupedOpenApi api : apis) {
      log.info("  - 分组 [{}] - {}", api.getGroup(), api.getDisplayName());
    }
    log.info("========================================");
  }
}
