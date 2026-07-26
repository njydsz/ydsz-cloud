package com.njydsz.common.base.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.dependencies.DependencyRules;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * PMIS 项目全局架构约束规则（ArchUnit）。
 *
 * <p>P3-1: 通过 ArchUnit 在编译期强制执行架构约束，防止层级穿透和循环依赖。
 *
 * <h3>规则分类</h3>
 * <ul>
 *   <li>DDD 分层约束：domain → infra → server → web，不可逆向依赖</li>
 *   <li>公共模块隔离：common-* 不可依赖业务模块</li>
 *   <li>无循环依赖：各业务模块 slice 之间不可循环依赖</li>
 *   <li>FQN 禁令：Java 代码中不允许行内全限定类名（由 check-inline-fqn.sh 覆盖）</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * mvn test -pl ydsz-common/ydsz-common-base -Dtest=ArchitectureRulesTest
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@AnalyzeClasses(packages = {"com.njydsz"})
public class ArchitectureRulesTest {

    /**
     * P3-1-R1: DDD 分层约束 — domain 层不可依赖 infra/server/web 层。
     *
     * <p>领域层是最内层，只包含领域模型和领域服务，不应感知基础设施实现细节。
     */
    @ArchTest
    static final ArchRule domainShouldNotDependOnInfra = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..server..", "..web..");

    /**
     * P3-1-R2: 公共模块隔离 — common-* 不可依赖业务模块（workflow/cronjob/message 等）。
     *
     * <p>公共模块是基础设施层，不应感知上层业务逻辑。
     */
    @ArchTest
    static final ArchRule commonShouldNotDependOnBusiness = noClasses()
            .that().resideInAPackage("..common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..");

    /**
     * P3-1-R3: Web 层不可直接访问 infra 层（必须通过 server/service 层中转）。
     *
     * <p>Controller 不应直接调用 Mapper 或 Repository，应通过 Service 间接访问。
     */
    @ArchTest
    static final ArchRule webShouldNotAccessInfraDirectly = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infra..");

    /**
     * P3-1-R4: 无循环依赖 — 各业务模块之间不可循环依赖。
     *
     * <p>例如 workflow → cronjob → workflow 是禁止的。
     * 跨模块通信应通过 Feign 客户端或事件驱动方式解耦。
     */
    @ArchTest
    static final ArchRule noCircularDependenciesBetweenModules = slices()
            .matching("..(workflow|cronjob|message|nextwiki|literule|agent|userinfo|system|project)..")
            .should().beFreeOfCycles();

    /**
     * P3-1-R5: Service 层方法名规范 — 以 Service 结尾的类应在 service 包中。
     *
     * <p>确保 DDD 分层清晰，Service 类不会散落在其他包中。
     */
    @ArchTest
    static final ArchRule serviceClassesShouldBeInServicePackage = classes()
            .that().haveSimpleNameEndingWith("ServiceImpl")
            .and().resideInAPackage("..server..")
            .should().resideInAPackage("..service..");
}
