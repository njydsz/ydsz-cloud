package com.njydsz.common.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层守护测试 — 云顶编码规范 YDIZ-DDD-001/002/003 自动化验证。
 *
 * <p>守护三大核心约束：</p>
 * <ol>
 *   <li><b>domain 纯净性</b>：业务模块 domain 层仅允许依赖 common 模块与 JDK，
 *       禁止引用任何其他业务模块的 infra/server/web</li>
 *   <li><b>server-to-infra 阻断</b>：server 层禁止 import infra 层类
 *       （依赖倒置通过 domain 接口编程实现）</li>
 *   <li><b>infra 依赖倒置</b>：infra 层允许引用 domain（实现 Repository 接口），
 *       此为正例不再否定性断言</li>
 * </ol>
 *
 * <p>本测试通过 ArchUnit 的 {@code ClassFileImporter} 扫描 {@code com.njydsz} 全量类定义，
 * 使用标准 JUnit 5 {@link Test} + {@code rule.check(classes)} 执行模式。
 * 跨模块可见性要求：在 CI 全量 reactor 构建（{@code mvn verify}）后执行测试，
 * 以确保业务模块 {@code target/classes} 已编译并被纳入分析。</p>
 *
 * <p>规则 ID 对齐：</p>
 * <ul>
 *   <li>DDD-LAYER-001：domain 不依赖 infra</li>
 *   <li>DDD-LAYER-002：domain 不依赖 server</li>
 *   <li>DDD-LAYER-003：domain 不依赖 web</li>
 *   <li>DDD-LAYER-005：server 不依赖 infra</li>
 * </ul>
 */
final class DddLayerArchTest {

    /**
     * 业务模块枚举（ydsz-cloud 当前 9 个业务微服务）。
     * common 模块及其子模块不参与 DDD 分层约束（common 各有 L1-L6 自身分层体系）。
     */
    private static final String[] BUSINESS_MODULES = {
        "system",
        "userinfo",
        "message",
        "workflow",
        "cronjob",
        "literule",
        "nextwiki",
        "agent",
        "generator"
    };

    /**
     * 全量导入 com.njydsz 下所有非测试类。
     */
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("com.njydsz");
    }

    // =====================================================================
    // DDD-LAYER-001: domain 层禁止依赖任何业务模块的 infra 层
    // =====================================================================

    @Test
    @DisplayName("DDD-LAYER-001: domain 层禁止依赖 infra 层")
    void domain_should_not_depend_on_infra() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(businessDomainPackages())
            .should().dependOnClassesThat()
            .resideInAnyPackage(businessInfraPackages())
            .because("YDIZ-DDD-001: domain 层禁止依赖 infra 层（依赖倒置，domain 仅依赖 common + JDK）.");
        rule.check(classes);
    }

    // =====================================================================
    // DDD-LAYER-002: domain 层禁止依赖任何业务模块的 server 层
    // =====================================================================

    @Test
    @DisplayName("DDD-LAYER-002: domain 层禁止依赖 server 层")
    void domain_should_not_depend_on_server() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(businessDomainPackages())
            .should().dependOnClassesThat()
            .resideInAnyPackage(businessServerPackages())
            .because("YDIZ-DDD-002: domain 层禁止依赖 server 层（domain 是 DDD 分层中心，零内部模块依赖）.");
        rule.check(classes);
    }

    // =====================================================================
    // DDD-LAYER-003: domain 层禁止依赖任何业务模块的 web 层
    // =====================================================================

    @Test
    @DisplayName("DDD-LAYER-003: domain 层禁止依赖 web 层")
    void domain_should_not_depend_on_web() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(businessDomainPackages())
            .should().dependOnClassesThat()
            .resideInAnyPackage(businessWebPackages())
            .because("YDIZ-DDD-003: domain 层禁止依赖 web 层（web 是组合根，不得反向引用 domain 之外还让 domain 引用 web）.");
        rule.check(classes);
    }

    // =====================================================================
    // DDD-LAYER-005: server 层禁止依赖 infra 层（P1-2 已整改）
    // =====================================================================

    @Test
    @DisplayName("DDD-LAYER-005: server 层禁止依赖 infra 层")
    void server_should_not_depend_on_infra() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(businessServerPackages())
            .should().dependOnClassesThat()
            .resideInAnyPackage(businessInfraPackages())
            .because("YDIZ-DDD-005: server 层禁止 import infra 层类（通过 domain 的 Repository 接口编程，Spring 在 web 组合根完成装配）.");
        rule.check(classes);
    }

    // =====================================================================
    // 包路径生成 helpers
    // =====================================================================

    private static String[] businessDomainPackages() {
        return prefixPackages("domain");
    }

    private static String[] businessInfraPackages() {
        return prefixPackages("infra");
    }

    private static String[] businessServerPackages() {
        return prefixPackages("server");
    }

    private static String[] businessWebPackages() {
        return prefixPackages("web");
    }

    private static String[] prefixPackages(String layer) {
        String[] result = new String[BUSINESS_MODULES.length];
        for (int i = 0; i < BUSINESS_MODULES.length; i++) {
            result[i] = "com.njydsz." + BUSINESS_MODULES[i] + "." + layer + "..";
        }
        return result;
    }
}
