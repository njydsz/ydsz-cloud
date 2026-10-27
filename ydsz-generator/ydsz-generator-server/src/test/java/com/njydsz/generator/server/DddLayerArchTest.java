package com.njydsz.generator.server;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层守护测试 — generator 模块（YDIZ-DDD-001/002/003/005 自动化验证）。
 *
 * <p><b>本模块包名与其它业务模块不同</b>：generator 的四个子模块共用顶层包 {@code com.njydsz.generator}，
 * 层信息体现在"子包名"而非"模块段"，因此不存在 {@code ..server..} / {@code ..infra..} 这样的包段。
 * 早期版本照抄其他模块的 {@code ..server..} / {@code ..infra..} 选择器，导致
 * {@code that()} 侧匹配到 0 个类，ArchUnit 直接报 "failed to check any classes"（守护为空转）；
 * 即便不报错，{@code should()} 侧也匹配不到任何类，规则恒真、零约束力。
 *
 * <p>因此本守护按 generator 的<b>真实包结构</b>声明选择器：
 * <table border="1">
 *   <caption>generator 模块包归属</caption>
 *   <tr><th>层</th><th>包</th></tr>
 *   <tr><td>domain</td><td>{@code domain / entity / vo / query / enums / converter / repository}（{@code repository} 为精确包，接口所在）</td></tr>
 *   <tr><td>infra</td><td>{@code mapper / po / repository.impl}（{@code repository.impl} 为实现所在子包）</td></tr>
 *   <tr><td>server</td><td>{@code service / engine / task / exception}</td></tr>
 *   <tr><td>web</td><td>{@code web}</td></tr>
 * </table>
 *
 * <p><b>已知覆盖缺口（不在本守护能力范围内，需求见报告 B1-11）</b>：
 * {@code com.njydsz.generator.config} 同时被 infra（{@code TemplateDataInitializer}）与
 * server（{@code ExecutorConfig} / {@code GeneratorVelocityConfig}）使用，包名无法区分层，
 * 故 {@code config} 包整体不参与分层判定；根治方式是把 generator 各子模块包名对齐
 * {@code com.njydsz.generator.{layer}.} 约定（与其余 8 个模块一致）。
 */
@AnalyzeClasses(
    packages = "com.njydsz.generator",
    importOptions = {
        ImportOption.DoNotIncludeTests.class
        // 不能用 DoNotIncludeJars：generator 的 domain 类在本模块的 -domain 依赖 jar 里，
        // 排除 jar 即扫不到 domain 类 = 三条 domain 守护退化成 "failed to check any classes"。
    }
)
@DisplayName("DDD 分层守护 — generator 模块")
final class DddLayerArchTest {

    /** domain 层真实包（{@code ..repository} 用精确包名，不含 infra 的 {@code repository.impl} 子包）。 */
    private static final String[] DOMAIN_PACKAGES = {
        "com.njydsz.generator.domain..",
        "com.njydsz.generator.entity..",
        "com.njydsz.generator.vo..",
        "com.njydsz.generator.query..",
        "com.njydsz.generator.enums..",
        "com.njydsz.generator.converter..",
        "com.njydsz.generator.repository"
    };

    /** infra 层真实包（仅列 generator-infra 独有、不会与 domain/server 重名的包）。 */
    private static final String[] INFRA_PACKAGES = {
        "com.njydsz.generator.mapper..",
        "com.njydsz.generator.po..",
        "com.njydsz.generator.repository.impl.."
    };

    /** server 层真实包。 */
    private static final String[] SERVER_PACKAGES = {
        "com.njydsz.generator.service..",
        "com.njydsz.generator.engine..",
        "com.njydsz.generator.task..",
        "com.njydsz.generator.exception.."
    };

    /** web 层真实包。 */
    private static final String[] WEB_PACKAGES = {"com.njydsz.generator.web.."};

    @ArchTest
    static final ArchRule should_not_skip_layers_to_infra = noClasses()
        .that().resideInAnyPackage(SERVER_PACKAGES)
        .should().dependOnClassesThat()
        .resideInAnyPackage(INFRA_PACKAGES)
        .because("YDIZ-DDD-005: server 层禁止直接访问 infra 层 mapper/po/repository_impl（依赖倒置，通过 domain Repository 接口编程）.");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infra = noClasses()
        .that().resideInAnyPackage(DOMAIN_PACKAGES)
        .should().dependOnClassesThat()
        .resideInAnyPackage(INFRA_PACKAGES)
        .because("YDIZ-DDD-001: domain 层禁止依赖 infra 层（domain 仅依赖 common + JDK，零内部模块依赖）.");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_server = noClasses()
        .that().resideInAnyPackage(DOMAIN_PACKAGES)
        .should().dependOnClassesThat()
        .resideInAnyPackage(SERVER_PACKAGES)
        .because("YDIZ-DDD-002: domain 层禁止依赖 server 层（domain 是 DDD 分层中心，不允许反向依赖）.");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_web = noClasses()
        .that().resideInAnyPackage(DOMAIN_PACKAGES)
        .should().dependOnClassesThat()
        .resideInAnyPackage(WEB_PACKAGES)
        .because("YDIZ-DDD-003: domain 层禁止依赖 web 层（web 是组合根，不允许 domain 反向引用 web）.");

    @ArchTest
    static final ArchRule should_maintain_unidirectional_dependency = SlicesRuleDefinition
        .slices()
        .matching("com.njydsz.generator.(*)..")
        .should().beFreeOfCycles()
        .because("YDIZ-DDD-001: DDD 分层必须单向依赖（web -> server -> domain <- infra），禁止循环引用。"
            + "注意 generator 的 repository/converter/config 包跨模块同名，切片按包聚合，"
            + "本规则实际校验的是顶层包之间无循环，不能替代上面四条精确到层的规则.");
}
