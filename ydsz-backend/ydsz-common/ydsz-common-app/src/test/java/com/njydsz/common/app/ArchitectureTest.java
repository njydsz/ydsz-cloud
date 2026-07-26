package com.njydsz.common.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ydsz-common-app 模块架构分层校验测试。
 *
 * <p>使用 ArchUnit 静态校验包依赖关系，确保模块内分层清晰、无循环依赖。
 * 校验范围：ydsz-common-app 模块自身的字节码（不含传递依赖）。
 *
 * <p><b>校验规则：</b>
 * <ul>
 *   <li>所有类必须在 {@code com.njydsz.common.app..} 包下</li>
 *   <li>过滤器/拦截器层不应反向依赖配置层（config）—— 配置层负责装配，过滤器/拦截器只应被装配</li>
 *   <li>过滤器层（filter）不允许依赖拦截器层（interceptor）</li>
 *   <li>工具层（util）不允许依赖业务层（auth/filter/interceptor/config）</li>
 *   <li>注解层（annotation）不依赖任何业务实现</li>
 *   <li>包之间无循环依赖</li>
 * </ul>
 * <p><b>设计说明：</b>Spring Boot 中 {@code @Configuration} 类通过 {@code FilterRegistrationBean}
 * 注册过滤器、通过 {@code WebMvcConfigurer} 注入拦截器是标准装配模式，config → filter/interceptor
 * 的依赖方向属于正常的控制反转，不视为违规；真正需要禁止的是反向依赖（filter/interceptor 反向读取 config 状态）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("ydsz-common-app 架构分层校验")
class ArchitectureTest {

    /**
     * 仅导入 ydsz-common-app 模块自身的类，避免校验传递依赖（如 Spring Framework）。
     */
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.njydsz.common.app..");

    @Test
    @DisplayName("所有类必须在 com.njydsz.common.app 包下")
    void allClassesShouldBeInAppPackage() {
        ArchRule rule = classes()
                .should().resideInAPackage("com.njydsz.common.app..")
                .because("ydsz-common-app 模块所有类必须在 com.njydsz.common.app 包或子包下");

        rule.check(classes);
    }

    @Test
    @DisplayName("过滤器/拦截器层不应反向依赖配置层（config）的 @Configuration 装配类")
    void filterAndInterceptorShouldNotDependOnConfigurationClass() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..filter..", "..interceptor..")
                .should().dependOnClassesThat()
                .resideInAPackage("..config..")
                .and().areAnnotatedWith(Configuration.class)
                .because("配置层的 @Configuration 类负责装配过滤器/拦截器，过滤器/拦截器只应被装配，"
                        + "不应反向调用 @Configuration 类；但允许依赖 @ConfigurationProperties 属性类读取自身配置");

        rule.check(classes);
    }

    @Test
    @DisplayName("工具层（util）不允许依赖业务层（auth/filter/interceptor/config/exception/advice）")
    void utilShouldNotDependOnBusinessLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..util..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..auth..", "..filter..", "..interceptor..",
                        "..config..", "..exception..", "..advice..")
                .because("工具层应保持无业务依赖，可被任意层引用");

        rule.check(classes);
    }

    @Test
    @DisplayName("注解层（annotation）不依赖业务实现层")
    void annotationShouldNotDependOnBusinessImplementation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..annotation..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..auth..", "..filter..", "..interceptor..",
                        "..config..", "..exception..", "..advice..", "..health..",
                        "..metrics..", "..util..")
                .because("注解定义应保持独立，不依赖具体实现");

        rule.check(classes);
    }

    @Test
    @DisplayName("过滤器层（filter）不应依赖拦截器层（interceptor）")
    void filterShouldNotDependOnInterceptor() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..filter..")
                .should().dependOnClassesThat()
                .resideInAPackage("..interceptor..")
                .because("过滤器和拦截器是独立的处理链，不应相互依赖");

        rule.check(classes);
    }

    @Test
    @DisplayName("拦截器层（interceptor）不应依赖过滤器层（filter）")
    void interceptorShouldNotDependOnFilter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..interceptor..")
                .should().dependOnClassesThat()
                .resideInAPackage("..filter..")
                .because("过滤器和拦截器是独立的处理链，不应相互依赖");

        rule.check(classes);
    }

    @Test
    @DisplayName("指标层（metrics）不应依赖过滤器/拦截器/控制器层")
    void metricsShouldNotDependOnWebInfraLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..metrics..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..filter..", "..interceptor..", "..advice..")
                .because("指标采集层应保持独立，不依赖 Web 处理链组件");

        rule.check(classes);
    }

    @Test
    @DisplayName("健康检查层（health）不应依赖过滤器/拦截器/异常处理层")
    void healthShouldNotDependOnWebInfraLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..health..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..filter..", "..interceptor..", "..exception..")
                .because("健康检查层应保持独立，不依赖 Web 处理链组件");

        rule.check(classes);
    }

    @Test
    @DisplayName("各子包之间不允许存在循环依赖")
    void slicesShouldBeFreeOfCycles() {
        slices().matching("com.njydsz.common.app.(*)..")
                .should().beFreeOfCycles()
                .because("模块内子包之间不应存在循环依赖，否则会导致编译/维护困难");
    }
}
