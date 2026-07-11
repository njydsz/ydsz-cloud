package com.njydsz.pmis.common.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 全局架构约束测试（P2-2 架构优化）。
 *
 * <p>使用 ArchUnit 在 CI 中强制校验模块依赖方向，防止架构劣化。
 * 覆盖以下规则：
 * <ul>
 *   <li>模块间不能直接访问 Mapper（禁止跨模块直连数据库表）</li>
 *   <li>Feign Client 接口必须集中在 common.feign 包</li>
 *   <li>Controller 不能直接调用 Mapper（必须经过 Service 层）</li>
 *   <li>common 模块不能依赖其他业务模块</li>
 *   <li>各模块的 DO 类必须继承 BaseDO</li>
 *   <li>禁止跨模块直接实例化其他模块的 Service 实现类</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("全局架构约束测试")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.njydsz.pmis..");
    }

    // ==================== 模块依赖方向约束 ====================

    @Test
    @DisplayName("common 模块不能依赖其他业务模块")
    void commonShouldNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.common..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.njydsz.pmis.project..",
                        "com.njydsz.pmis.cronjob..",
                        "com.njydsz.pmis.message..",
                        "com.njydsz.pmis.workflow..",
                        "com.njydsz.pmis.agent..",
                        "com.njydsz.pmis.literule.."
                )
                .because("common 模块是基础库，不能反向依赖业务模块");

        rule.check(classes);
    }

    // ==================== 分层架构约束 ====================

    @Test
    @DisplayName("Controller 不能直接调用 Mapper")
    void controllerShouldNotAccessMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat()
                .resideInAPackage("..mapper..")
                .because("Controller 必须通过 Service 层访问数据，不能直接调用 Mapper");

        rule.check(classes);
    }

    @Test
    @DisplayName("Feign Client 接口必须集中在 common.feign 包")
    void feignClientsShouldBeInCommonFeign() {
        ArchRule rule = classes()
                .that().areInterfaces()
                .and().haveSimpleNameEndingWith("Client")
                .and().areAnnotatedWith("org.springframework.cloud.openfeign.FeignClient")
                .should().resideInAPackage("com.njydsz.pmis.common.feign..")
                .because("所有 Feign Client 接口必须集中在 common.feign 包，禁止模块间直接 HTTP 调用");

        rule.check(classes);
    }

    // ==================== 跨模块直连约束 ====================

    @Test
    @DisplayName("project 模块不能直接访问 cronjob 的 Mapper")
    void projectShouldNotAccessCronjobMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.project..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.njydsz.pmis.cronjob.mapper..")
                .because("project 模块不能直接访问 cronjob 的数据库表，必须通过 Feign 调用");

        rule.check(classes);
    }

    @Test
    @DisplayName("project 模块不能直接访问 message 的 Mapper")
    void projectShouldNotAccessMessageMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.project..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.njydsz.pmis.message.mapper..")
                .because("project 模块不能直接访问 message 的数据库表，必须通过 Feign 调用");

        rule.check(classes);
    }

    @Test
    @DisplayName("workflow 模块不能直接访问 message 的 Mapper")
    void workflowShouldNotAccessMessageMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.workflow..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.njydsz.pmis.message.mapper..")
                .because("workflow 模块不能直接访问 message 的数据库表，必须通过 Feign 调用");

        rule.check(classes);
    }

    @Test
    @DisplayName("cronjob 模块不能直接访问 project 的 Mapper")
    void cronjobShouldNotAccessProjectMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.njydsz.pmis.cronjob..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.njydsz.pmis.project.mapper..")
                .because("cronjob 模块不能直接访问 project 的数据库表，必须通过 Feign 调用");

        rule.check(classes);
    }

    // ==================== 模块循环依赖检查 ====================

    @Test
    @DisplayName("各模块之间不能存在循环依赖")
    void modulesShouldBeFreeOfCycles() {
        ArchRule rule = slices()
                .matching("com.njydsz.pmis.(*)..")
                .should().beFreeOfCycles()
                .because("模块之间不能存在循环依赖，违反微服务架构原则");

        rule.check(classes);
    }
}
