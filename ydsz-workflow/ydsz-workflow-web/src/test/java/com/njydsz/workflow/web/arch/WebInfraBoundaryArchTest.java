package com.njydsz.workflow.web.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * web→infra 边界守护测试 — workflow 模块（YDIZ-DDD-006）。
 *
 * <p><b>背景</b>：web 是组合根（Composition Root），同时依赖 server + infra + domain。
 * 虽然 web 可以依赖 infra（用于 Bean 装配），但<b>业务请求链路</b>上不应直接依赖 infra
 * 实现包（mapper / repository / converter），而应经 server 层编排。历史上
 * {@code McpSseServerTransport}（Controller）直接 import infra 协议类，属边界违规。
 *
 * <p><b>豁免</b>：组合根装配类（{@code *AutoConfiguration} / {@code *Configuration}）
 * 负责 Bean 装配，允许依赖 infra。
 *
 * <p><b>说明</b>：本规则放在 web 模块而非 server，因为 ArchUnit 的
 * {@code DoNotIncludeJars} 会排除依赖 jar，而 web 模块的 {@code ..web..} 类就在本模块
 * 源码中，扫描更准确。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@AnalyzeClasses(
    packages = "com.njydsz.workflow",
    importOptions = {ImportOption.DoNotIncludeTests.class})
@DisplayName("web→infra 边界守护 — workflow 模块")
final class WebInfraBoundaryArchTest {

    @ArchTest
    static final ArchRule web_should_not_depend_on_infra_impl = noClasses()
        .that().resideInAnyPackage("..web..")
        .and().haveSimpleNameNotEndingWith("AutoConfiguration")
        .and().haveSimpleNameNotEndingWith("Configuration")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..infra.mapper..", "..infra.repository..", "..infra.converter..")
        .because(
            "YDIZ-DDD-006: web 层禁止直接依赖 infra 实现包（应经 server 编排）；"
                + "组合根装配类（*AutoConfiguration / *Configuration）负责 Bean 装配，豁免.");
}
