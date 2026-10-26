package com.njydsz.workflow.server;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层守护测试 — workflow 模块（YDIZ-DDD-001/002/003/005 自动化验证）。
 *
 * <p>守护三大核心约束：</p>
 * <ol>
 *   <li><b>noLayerSkipping</b>：server 层禁止直接访问 infra 层 mapper/repository_impl 包</li>
 *   <li><b>domain 零依赖</b>：domain 层禁止依赖 web/server/infra</li>
 *   <li><b>单向依赖</b>：web 可以依赖 server+infra+domain，server 只能依赖 domain</li>
 * </ol>
 */
@AnalyzeClasses(
    packages = "com.njydsz.workflow",
    importOptions = {
        ImportOption.DoNotIncludeTests.class
        // 不能用 DoNotIncludeJars：..domain.. 类在本模块的 -domain 依赖 jar 里，排除 jar 即扫不到
        // domain 类 = 三条 domain 守护退化成 "failed to check any classes"，分层守护从未真正生效。
    }
)
@DisplayName("DDD 分层守护 — workflow 模块")
final class DddLayerArchTest {

    @ArchTest
    static final ArchRule should_not_skip_layers_to_infra = noClasses()
        .that().resideInAnyPackage("..server..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..infra.mapper..", "..infra.repository..")
        .because("YDIZ-DDD-005: server 层禁止直接访问 infra 层 mapper/repository（P1-2 已整改，依赖倒置通过 domain Repository 接口编程）.");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infra = noClasses()
        .that().resideInAnyPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..infra..")
        .because("YDIZ-DDD-001: domain 层禁止依赖 infra 层（domain 仅依赖 common + JDK，零内部模块依赖）.");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_server = noClasses()
        .that().resideInAnyPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..server..")
        .because("YDIZ-DDD-002: domain 层禁止依赖 server 层（domain 是 DDD 分层中心，不允许反向依赖）.");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_web = noClasses()
        .that().resideInAnyPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..web..")
        .because("YDIZ-DDD-003: domain 层禁止依赖 web 层（web 是组合根，不允许 domain 反向引用 web）.");

    @ArchTest
    static final ArchRule should_maintain_unidirectional_dependency = SlicesRuleDefinition
        .slices()
        .matching("com.njydsz.workflow.(*)..")
        .should().beFreeOfCycles()
        .because("YDIZ-DDD-001: DDD 分层必须单向依赖（web -> server -> domain <- infra），禁止循环引用.");
}
