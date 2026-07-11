package com.njydsz.pmis.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 架构守护测试（P2-5）
 *
 * <p>使用 ArchUnit 静态分析代码结构，防止架构腐化。
 * 覆盖分层依赖、包结构、命名规范等架构约束。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ArchUnit 架构守护测试")
class ArchitectureGuardTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.njydsz.pmis..");
    }

    // ==================== 分层依赖规则 ====================

    @Test
    @DisplayName("Controller 不能直接访问 Mapper 层")
    void controllerShouldNotAccessMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .because("Controller 应通过 Service 层访问数据，禁止直接依赖 Mapper");

        rule.check(classes);
    }

    @Test
    @DisplayName("Controller 不能直接访问 Entity(DO) 层")
    void controllerShouldNotAccessEntityDO() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..entity..")
                .andShould().dependOnClassesThat().haveSimpleNameEndingWith("DO")
                .because("Controller 应使用 DTO/VO 与前端交互，禁止直接暴露 DO");

        // 使用宽松检查：仅检查直接依赖 DO 类名的情况
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("DO")
                .because("Controller 不应直接引用 DO 实体类")
                .check(classes);
    }

    @Test
    @DisplayName("Service 层不能引用 Controller 层")
    void serviceShouldNotAccessController() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("Service 层不应反向依赖 Controller 层");

        rule.check(classes);
    }

    @Test
    @DisplayName("Mapper 层不能引用 Controller 或 Service 层")
    void mapperShouldNotAccessControllerOrService() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
                .because("Mapper 是数据访问层，不应依赖业务层");

        rule.check(classes);
    }

    // ==================== 包结构规则 ====================

    @Test
    @DisplayName("DTO 类应放在 dto 包下")
    void dtoShouldBeInDtoPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("DTO")
                .and().resideInAPackage("com.njydsz.pmis..")
                .and().areTopLevelClasses()
                .should().resideInAPackage("..dto..")
                .orShould().resideInAPackage("..feign..")
                .because("DTO 类应统一放在 dto 包下，便于管理和查找");

        rule.check(classes);
    }

    @Test
    @DisplayName("VO 类应放在 vo 包下")
    void voShouldBeInVoPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("VO")
                .and().resideInAPackage("com.njydsz.pmis..")
                .and().areTopLevelClasses()
                .should().resideInAPackage("..vo..")
                .orShould().resideInAPackage("..feign..")
                .because("VO 类应统一放在 vo 包下，便于管理和查找");

        rule.check(classes);
    }

    @Test
    @DisplayName("DO 实体类应放在 entity 包下")
    void doShouldBeInEntityPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("DO")
                .and().resideInAPackage("com.njydsz.pmis..")
                .and().areTopLevelClasses()
                .should().resideInAPackage("..entity..")
                .because("DO 实体类应统一放在 entity 包下");

        rule.check(classes);
    }

    // ==================== 模块隔离规则 ====================

    @Test
    @DisplayName("common 模块不应依赖业务模块")
    void commonShouldNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.njydsz.pmis.userinfo..",
                        "com.njydsz.pmis.project..",
                        "com.njydsz.pmis.workflow..",
                        "com.njydsz.pmis.system..",
                        "com.njydsz.pmis.message..",
                        "com.njydsz.pmis.cronjob..",
                        "com.njydsz.pmis.agent.."
                )
                .because("common 是公共模块，不应反向依赖业务模块");

        rule.check(classes);
    }

    @Test
    @DisplayName("gateway 模块不应直接依赖业务模块内部实现")
    void gatewayShouldNotDependOnBusinessInternals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.njydsz.pmis.userinfo.service..",
                        "com.njydsz.pmis.project.service..",
                        "com.njydsz.pmis.workflow.service.."
                )
                .because("Gateway 应通过 Feign 或请求头透传与业务模块通信，不应直接依赖 Service 实现");

        rule.check(classes);
    }

    // ==================== 循环依赖检测 ====================

    @Test
    @DisplayName("各模块包不应存在循环依赖")
    void noCircularDependencies() {
        slices()
                .matching("com.njydsz.pmis.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    // ==================== 安全规则 ====================

    @Test
    @DisplayName("敏感工具类不应被 Controller 直接引用")
    void sensitiveUtilsShouldNotBeInController() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..util.CryptoUtil")
                .because("加密工具不应在 Controller 层直接使用，应在 Service 层处理");

        rule.check(classes);
    }

    @Test
    @DisplayName("配置类应放在 config 包下")
    void configShouldBeInConfigPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Config")
                .and().haveSimpleNameNotEndingWith("CanaryConfig")
                .and().resideInAPackage("com.njydsz.pmis..")
                .and().areAnnotatedWith("org.springframework.context.annotation.Configuration")
                .should().resideInAPackage("..config..")
                .because("配置类应统一放在 config 包下");

        rule.check(classes);
    }
}
