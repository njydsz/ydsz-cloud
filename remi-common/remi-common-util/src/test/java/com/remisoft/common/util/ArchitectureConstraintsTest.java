package com.remisoft.common.util;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * ArchUnit 架构约束测试 — 通过静态字节码分析保证代码结构一致性。
 *
 * <p><b>核心约束：</b>
 * <ol>
 *   <li>工具类必须私有构造器 + public static 方法（防止被误实例化）</li>
 *   <li>包隔离：安全类（AES/RSA/密码）不得被 id/string/time 等低层包导入</li>
 *   <li>命名约定：工具类以 "Utils" 结尾</li>
 * </ol>
 *
 * <p>这些测试在 {@code mvn test} 阶段自动运行，失败则 CI 阻断。
 *
 * @author remi-team
 * @since 1.3.0
 */
@AnalyzeClasses(packages = "com.remisoft.common.util", importOptions = ImportOption.DoNotIncludeTests.class)
@DisplayName("ArchUnit 架构约束")
class ArchitectureConstraintsTest {

    /**
     * 匹配所有以 "Utils" 结尾的类（排除 interfaces 和 enums）。
     */
    private static final DescribedPredicate<JavaClass> IS_UTILITY_CLASS =
            new DescribedPredicate<>("是以 Utils 结尾的工具类") {
                @Override
                public boolean test(JavaClass input) {
                    return input.getSimpleName().endsWith("Utils")
                            && !input.isInterface()
                            && !input.isEnum();
                }
            };

    /**
     * 匹配所有 public 静态方法的条件（工具类方法的规范）。
     */
    private static final ArchCondition<JavaClass> HAVE_PRIVATE_CONSTRUCTORS =
            new ArchCondition<JavaClass>("必须具有私有构造器") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean allConstructorsPrivate = javaClass.getConstructors().stream()
                            .allMatch(c -> c.getModifiers().contains(JavaModifier.PRIVATE));
                    if (!allConstructorsPrivate) {
                        events.add(SimpleConditionEvent.violated(javaClass,
                                String.format("工具类 %s 存在非私有构造器；工具类应私有化构造器防止被 new 实例化",
                                        javaClass.getName())));
                    }
                }
            };

    // ==================== 1. 工具类规范 ====================

    /**
     * 工具类规则：以 Utils 结尾的类必须所有构造器私有。
     *
     * <p>典型正确示例：
     * <pre>{@code
     * public class AesUtils {
     *     private AesUtils() { throw new UnsupportedOperationException(); }
     * }
     * }</pre>
     */
    @ArchTest
    static final ArchRule UTILITY_CLASSES_MUST_HAVE_PRIVATE_CONSTRUCTORS =
            classes().that(IS_UTILITY_CLASS).should(HAVE_PRIVATE_CONSTRUCTORS);

    /**
     * 工具类 public 方法应该是 static 的。
     *
     * <p>ArchUnit 的 {@code beStatic()} 检查方法修饰符。
     */
    @ArchTest
    static final ArchRule UTILITY_CLASSES_METHODS_SHOULD_BE_STATIC =
            methods()
                    .that().areDeclaredInClassesThat(IS_UTILITY_CLASS)
                    .and().arePublic()
                    .should().haveModifier(JavaModifier.STATIC)
                    .as("工具类 public 方法必须是 static 方法（非实例方法）");

    // ==================== 2. 包隔离 ====================

    /**
     * 基础工具包清单（不应导入 security 包）。
     */
    private static final String[] LOW_LEVEL_CORE_PACKAGES = {
            "com.remisoft.common.util.id..",
            "com.remisoft.common.util.string..",
            "com.remisoft.common.util.time..",
            "com.remisoft.common.util.collection..",
            "com.remisoft.common.util.io..",
    };

    /**
     * 匹配 security 包中的类。
     */
    private static final DescribedPredicate<JavaClass> IS_SECURITY_CLASS =
            new DescribedPredicate<>("在 security 包下") {
                @Override
                public boolean test(JavaClass input) {
                    return input.getPackageName().startsWith("com.remisoft.common.util.security");
                }
            };

    /**
     * 基础工具层不得依赖 security 包。
     *
     * <p>原因：security 包会引入 SecretKey / Cipher 等重量级安全 API；
     * id/string/time 等基础工具应是零安全依赖的纯工具层，避免上层模块
     * 传递性引入不需要的加密依赖。
     */
    @ArchTest
    static final ArchRule LOW_LEVEL_CLASSES_SHOULD_NOT_DEPEND_ON_SECURITY =
            noClasses()
                    .that().resideInAnyPackage(LOW_LEVEL_CORE_PACKAGES)
                    .should().dependOnClassesThat(IS_SECURITY_CLASS)
                    .as("id/string/time/collection/io 包不应依赖 security 包");

    // ==================== Runner 方法（JUnit 5 要求至少一个 @Test） ====================

    /**
     * ArchUnit {@link @ArchTest} 静态字段规则无需主动运行即会被 JUnit 平台扫描。
     *
     * <p>此空方法仅作为 IDE "运行当前类" 的锚点；规则收集由 ArchUnit ArchUnitRunner 完成。
     */
    @Test
    @DisplayName("收集运行所有 ArchUnit 规则（自动扫描 @ArchTest 字段）")
    void runAllArchUnitRules() {
        // ArchUnit JUnit5 扩展会自动运行所有 @ArchTest 字段规则；此方法仅占位。
    }
}
