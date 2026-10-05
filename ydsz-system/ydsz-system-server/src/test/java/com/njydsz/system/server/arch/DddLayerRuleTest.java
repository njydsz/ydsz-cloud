package com.njydsz.system.server.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * DDD 分层架构 ArchUnit 门禁（YDIZ-DDD-001 P0 合规）
 *
 * <p>编译期 + 测试期双重保障：
 * <ol>
 *   <li>Controller（web）不得直接访问 Repository（infra）— 必须通过 Service 层</li>
 *   <li>Server 层不得直接依赖 infra Mapper — 只能注入 Repository 接口</li>
 *   <li>Domain 层不得依赖同项目其他业务模块（仅依赖 common）</li>
 *   <li>无循环依赖包</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.10.05
 */
@DisplayName("DDD 分层架构 - ArchUnit 规则验证")
class DddLayerRuleTest {

  private static final String BASE_PACKAGE = "com.njydsz.system";

  private final JavaClasses importedClasses = new ClassFileImporter()
      .importPackages(BASE_PACKAGE);

  @Test
  @DisplayName("Controller 不得直接注入 Repository（必须通过 Service 层）")
  void controllersShouldNotDependOnRepositories() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..web.controller..")
        .should().dependOnClassesThat().resideInAPackage("..infra.repository..")
        .because("Controller → Service → Repository 是 DDD 单向依赖，禁止 web 层直注 infra Repository (YDIZ-DDD-001)");

    rule.check(importedClasses);
  }

  @Test
  @DisplayName("Server 层不得直接依赖 Mapper（必须通过 Repository 接口）")
  void serverShouldNotDependOnMapper() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..server..")
        .should().dependOnClassesThat().resideInAPackage("..infra.mapper..")
        .because("Server 层应通过 domain Repository 接口访问，禁止直注 infra Mapper (YDIZ-DDD-001)");

    rule.check(importedClasses);
  }

  @Test
  @DisplayName("Domain 层不得依赖项目内其他业务模块")
  void domainShouldNotDependOnSiblingModules() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("com.njydsz.(agent|cronjob|userinfo|message|workflow|nextwiki|literule|generator)..")
        .because("domain 层仅依赖 common，禁止引入其他业务模块（YDIZ-DDD-001）");

    rule.check(importedClasses);
  }

  @Test
  @DisplayName("禁止包循环依赖（同层内无环）")
  void noCyclicDependenciesInLayers() {
    ArchRule rule = slices()
        .matching("com.njydsz.system.(*)..")
        .should().beFreeOfCycles()
        .because("DDD 分层必须单向依赖，循环依赖违反架构红线");

    rule.check(importedClasses);
  }
}
